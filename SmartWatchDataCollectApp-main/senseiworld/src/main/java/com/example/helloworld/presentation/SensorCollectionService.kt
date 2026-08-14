package com.example.helloworld.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.helloworld.R

/** Foreground service that runs [SensorDataCollector] until [ACTION_STOP] is sent. */
class SensorCollectionService : Service() {

    private lateinit var sensorDataCollector: SensorDataCollector
    private val binder = LocalBinder()
    private var audioPlayer: MediaPlayer? = null

    var isCollecting = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): SensorCollectionService = this@SensorCollectionService
    }

    override fun onCreate() {
        super.onCreate()
        sensorDataCollector = SensorDataCollector(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCollecting()
            ACTION_STOP -> stopCollecting()
        }
        return START_STICKY
    }

    fun startCollecting() {
        if (isCollecting) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), collectionServiceType())
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e("SensorCollectionService", "Unable to start foreground service", e)
            stopSelf()
            return
        }
        // Start playback before the mic grabs the audio input: on some watch
        // audio chips a live AudioRecord capture leaves no path for the
        // output track to prepare, and MediaPlayer.create() then silently
        // returns null.
        startAudioPlayback()
        sensorDataCollector.startListening()
        isCollecting = true
        Log.i("SensorCollectionService", "Collection started")
    }

    private fun startAudioPlayback() {
        val audioManager = getSystemService(AudioManager::class.java)
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.i("SensorCollectionService", "STREAM_MUSIC volume: $volume/$maxVolume")

        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val afd = resources.openRawResourceFd(R.raw.signal)
            afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnErrorListener { _, what, extra ->
                Log.e("SensorCollectionService", "MediaPlayer error: what=$what extra=$extra")
                true
            }
            player.isLooping = false
            player.setVolume(1.0f, 1.0f)
            player.prepare()
            player.start()
            audioPlayer = player
            Log.i("SensorCollectionService", "Audio playback started")
        } catch (e: Exception) {
            Log.e("SensorCollectionService", "Unable to start audio playback", e)
        }
    }

    private fun stopAudioPlayback() {
        audioPlayer?.let { player ->
            try {
                player.stop()
            } catch (e: IllegalStateException) {
                Log.w("SensorCollectionService", "MediaPlayer was not playing when stop was called", e)
            }
            player.release()
        }
        audioPlayer = null
    }

    private fun collectionServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    fun stopCollecting() {
        if (!isCollecting) return
        sensorDataCollector.stopListening()
        stopAudioPlayback()
        isCollecting = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i("SensorCollectionService", "Collection stopped")
    }

    override fun onDestroy() {
        if (isCollecting) {
            sensorDataCollector.stopListening()
            stopAudioPlayback()
            isCollecting = false
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.stop_collecting_data))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Data Collection",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.helloworld.action.START_COLLECTION"
        const val ACTION_STOP = "com.example.helloworld.action.STOP_COLLECTION"
        private const val CHANNEL_ID = "sensor_collection_channel"
        private const val NOTIFICATION_ID = 1
    }
}
