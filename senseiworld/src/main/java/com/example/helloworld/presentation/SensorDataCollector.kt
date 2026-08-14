package com.example.helloworld.presentation

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SensorDataCollector(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val csvWriter = SensorCsvWriter(context)

    private val sensorDataMap = mutableMapOf<String, MutableList<SensorCsvWriter.SensorSample>>()

    // Id of the current collection session; used to name audio files so they
    // line up with the CSV folder (session 1 -> ..._audio_1.wav).
    private var currentSessionId = 0

    private val handler = Handler(Looper.getMainLooper())
    private val storageInterval: Long = 60 * 1000

    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var currentAudioFile: File? = null
    private var recordedDataBytes: Long = 0L

    @Volatile
    private var isRecording = false

    private var executorService = Executors.newSingleThreadExecutor()

    private val storeDataRunnable = object : Runnable {
        override fun run() {
            storeSensorData()
            handler.postDelayed(this, storageInterval)
        }
    }

    fun startListening() {
        if (executorService.isShutdown) {
            executorService = Executors.newSingleThreadExecutor()
        }

        currentSessionId = csvWriter.startSession()

        registerSensor(Sensor.TYPE_ACCELEROMETER)
        registerSensor(Sensor.TYPE_GYROSCOPE)
        registerSensor(Sensor.TYPE_HEART_RATE)
        registerSensor(Sensor.TYPE_STEP_COUNTER)
        registerSensor(Sensor.TYPE_LIGHT)
        registerSensor(Sensor.TYPE_GRAVITY)
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD)
        registerSensor(Sensor.TYPE_ROTATION_VECTOR)

        registerSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        registerSensor(Sensor.TYPE_STEP_DETECTOR)
        registerSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        registerSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        registerSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        registerSensor(Sensor.TYPE_PRESSURE)
        registerSensor(Sensor.TYPE_PROXIMITY)
        registerSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        registerSensor(Sensor.TYPE_HEART_BEAT)

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startMicrophoneRecording()
        } else {
            Log.w("SensorDataCollector", "RECORD_AUDIO not granted; skipping microphone recording")
        }

        handler.post(storeDataRunnable)
    }

    private fun createAudioFile(): File {
        // Same external files root as the CSV data so audio and sensor data sit
        // side by side (sdcard/Android/data/<pkg>/files/audio/...).
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        val audioDir = File(parent, "audio")
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            throw IllegalStateException("Unable to create audio directory: ${audioDir.absolutePath}")
        }

        // e.g. 20260709_audio_1.wav — the trailing number is the session id so it
        // matches the CSV folder.
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return File(audioDir, "${date}_audio_$currentSessionId.wav")
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneRecording() {
        if (isRecording) {
            Log.w("SensorDataCollector", "Microphone is already recording.")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_ENCODING
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw IllegalStateException("Invalid AudioRecord buffer size: $minBufferSize")
            }

            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE / 10 * BYTES_PER_SAMPLE)
            val audioFile = createAudioFile()

            RandomAccessFile(audioFile, "rw").use { file ->
                file.setLength(0)
                writeWavHeader(file, 0L)
            }

            val recorder = AudioRecord.Builder()
                .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_ENCODING)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            recordedDataBytes = 0L
            currentAudioFile = audioFile
            audioRecord = recorder
            isRecording = true

            recorder.startRecording()
            audioThread = Thread(
                { writeAudioData(recorder, audioFile, bufferSize) },
                "WatchWavRecorder"
            ).apply { start() }

            Log.i(
                "SensorDataCollector",
                "Microphone WAV recording started. File saved at: ${audioFile.absolutePath}"
            )
        } catch (e: IllegalStateException) {
            Log.e(
                "SensorDataCollector",
                "Illegal state during microphone setup: ${e.localizedMessage}",
                e
            )
            releaseAudioRecorder()
        } catch (e: Exception) {
            Log.e(
                "SensorDataCollector",
                "Error starting microphone recording: ${e.localizedMessage}",
                e
            )
            releaseAudioRecorder()
        }
    }

    private fun stopMicrophoneRecording() {
        if (!isRecording) return

        try {
            val recorder = audioRecord
            isRecording = false

            try {
                recorder?.stop()
            } catch (e: IllegalStateException) {
                Log.w("SensorDataCollector", "AudioRecord was not recording when stop was called", e)
            }

            audioThread?.join(STOP_JOIN_TIMEOUT_MS)
            recorder?.release()

            val audioFile = currentAudioFile
            if (audioFile != null) {
                updateWavHeader(audioFile, recordedDataBytes)
                Log.i(
                    "SensorDataCollector",
                    "Microphone WAV recording stopped: ${audioFile.absolutePath}"
                )
            }

            audioRecord = null
            audioThread = null
            currentAudioFile = null
            recordedDataBytes = 0L
        } catch (e: Exception) {
            Log.e("SensorDataCollector", "Error stopping microphone recording", e)
            releaseAudioRecorder()
        }
    }

    private fun registerSensor(sensorType: Int, samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_FASTEST) {
        sensorManager.getDefaultSensor(sensorType)?.also { sensor ->
            sensorManager.registerListener(this, sensor, samplingPeriodUs)
        } ?: run {
            Log.w("SensorDataCollector", "Sensor not available: $sensorType")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(storeDataRunnable)
        stopMicrophoneRecording()
        storeSensorData() // flush any samples buffered since the last interval
        executorService.shutdown()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val sensorType = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
                Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
                Sensor.TYPE_GRAVITY -> "Gravity Sensor"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope"

                Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
                Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
                Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"

                Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer Uncalibrated"
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope Uncalibrated"
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetic Field Uncalibrated"

                Sensor.TYPE_STEP_COUNTER -> "Step Counter"
                Sensor.TYPE_STEP_DETECTOR -> "Step Detector"

                Sensor.TYPE_PRESSURE -> "Pressure"
                Sensor.TYPE_LIGHT -> "Light Sensor"
                Sensor.TYPE_PROXIMITY -> "Proximity"

                Sensor.TYPE_HEART_RATE -> "Heart Rate"
                Sensor.TYPE_HEART_BEAT -> "Heart Beat"
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Off-Body Detect"

                else -> "Unknown Sensor"
            }

            val sampleTime = System.currentTimeMillis()
            // event.values is reused by the framework, so copy it before buffering.
            val sample = SensorCsvWriter.SensorSample(sampleTime, event.values.copyOf())

            synchronized(sensorDataMap) {
                sensorDataMap.getOrPut(sensorType) { mutableListOf() }.add(sample)
            }
        }
    }

    private fun storeSensorData() {
        Log.i("SensorDataCollector", "Storing aggregated sensor data")

        if (executorService.isShutdown) return

        executorService.execute {
            val dataToStore: Map<String, List<SensorCsvWriter.SensorSample>>

            synchronized(sensorDataMap) {
                dataToStore = sensorDataMap.mapValues { it.value.toList() }
                sensorDataMap.clear()
            }

            for ((sensorType, samples) in dataToStore) {
                try {
                    csvWriter.appendSamples(sensorType, samples)
                } catch (e: Exception) {
                    Log.e(
                        "SensorDataCollector",
                        "Error storing data for $sensorType",
                        e
                    )
                }
            }

            Log.i("SensorDataCollector", "Data storage completed")
        }
    }

    private fun writeAudioData(recorder: AudioRecord, audioFile: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)

        try {
            RandomAccessFile(audioFile, "rw").use { file ->
                file.seek(WAV_HEADER_SIZE.toLong())

                while (isRecording) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        file.write(buffer, 0, bytesRead)
                        recordedDataBytes += bytesRead.toLong()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SensorDataCollector", "Error writing WAV audio data", e)
        }
    }

    private fun releaseAudioRecorder() {
        isRecording = false
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w("SensorDataCollector", "Error releasing AudioRecord", e)
        }
        audioRecord = null
        audioThread = null
        currentAudioFile = null
        recordedDataBytes = 0L
    }

    private fun writeWavHeader(file: RandomAccessFile, dataSize: Long) {
        val totalDataLen = dataSize + 36
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE

        file.writeBytes("RIFF")
        writeLittleEndianInt(file, totalDataLen.toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        writeLittleEndianInt(file, 16)
        writeLittleEndianShort(file, 1)
        writeLittleEndianShort(file, CHANNEL_COUNT.toShort())
        writeLittleEndianInt(file, SAMPLE_RATE)
        writeLittleEndianInt(file, byteRate)
        writeLittleEndianShort(file, (CHANNEL_COUNT * BYTES_PER_SAMPLE).toShort())
        writeLittleEndianShort(file, BITS_PER_SAMPLE.toShort())
        file.writeBytes("data")
        writeLittleEndianInt(file, dataSize.toInt())
    }

    private fun updateWavHeader(audioFile: File, dataSize: Long) {
        RandomAccessFile(audioFile, "rw").use { file ->
            file.seek(0)
            writeWavHeader(file, dataSize)
        }
    }

    private fun writeLittleEndianInt(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write(value shr 8 and 0xff)
        file.write(value shr 16 and 0xff)
        file.write(value shr 24 and 0xff)
    }

    private fun writeLittleEndianShort(file: RandomAccessFile, value: Short) {
        val intValue = value.toInt()
        file.write(intValue and 0xff)
        file.write(intValue shr 8 and 0xff)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val WAV_HEADER_SIZE = 44
        private const val STOP_JOIN_TIMEOUT_MS = 1000L
    }
}
