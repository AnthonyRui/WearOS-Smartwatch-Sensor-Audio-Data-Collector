package com.example.helloworld.presentation

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Persists sensor samples to per-sensor CSV files on the device.
 *
 * Data is grouped into numbered *sessions*: every time the user starts a new
 * collection, [startSession] creates a fresh folder (`sensor_data/1`,
 * `sensor_data/2`, ...). Inside it, each sensor type gets its own file named
 * with the session id (e.g. `Accelerometer_1.csv`), and every sensor event is
 * written as a single row: the sample timestamp followed by all values of that
 * event (for the accelerometer: timestamp, x, y, z).
 *
 * The `sensor_data` root lives in the app's external files dir when available
 * (pullable via `adb pull` / a file manager without extra permissions) and
 * falls back to internal storage otherwise.
 */
class SensorCsvWriter(context: Context) {

    /** One sensor event: the sample time and all axis values of that event. */
    data class SensorSample(val timeMillis: Long, val values: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SensorSample) return false
            return timeMillis == other.timeMillis && values.contentEquals(other.values)
        }

        override fun hashCode(): Int = 31 * timeMillis.hashCode() + values.contentHashCode()
    }

    private val rootDir: File = run {
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(parent, "sensor_data")
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w("SensorCsvWriter", "Unable to create sensor CSV root: ${dir.absolutePath}")
        }
        dir
    }

    private var sessionId: Int = 0
    private var sessionDir: File? = null

    /**
     * Begins a new collection session in its own numbered folder and returns
     * the session id. The id is one greater than the highest existing folder,
     * so sessions keep counting up across app restarts.
     */
    fun startSession(): Int {
        val id = nextSessionId()
        val dir = File(rootDir, id.toString())
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w("SensorCsvWriter", "Unable to create session folder: ${dir.absolutePath}")
        }
        sessionId = id
        sessionDir = dir
        Log.i("SensorCsvWriter", "Started CSV session $id at ${dir.absolutePath}")
        return id
    }

    /** Appends the given samples to the current session's file for [sensorType]. */
    fun appendSamples(sensorType: String, samples: List<SensorSample>) {
        if (samples.isEmpty()) return

        val dir = sessionDir
        if (dir == null) {
            Log.w("SensorCsvWriter", "No active session; dropping ${samples.size} $sensorType samples")
            return
        }

        val file = File(dir, fileName(sensorType, sessionId))
        val writeHeader = !file.exists() || file.length() == 0L

        try {
            BufferedWriter(FileWriter(file, /* append = */ true)).use { writer ->
                if (writeHeader) {
                    writer.append(headerFor(samples.first().values.size))
                    writer.newLine()
                }
                for (sample in samples) {
                    writer.append(sample.timeMillis.toString())
                    for (value in sample.values) {
                        writer.append(',')
                        writer.append(value.toString())
                    }
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            Log.e("SensorCsvWriter", "Error writing CSV for $sensorType", e)
        }
    }

    private fun nextSessionId(): Int {
        val highest = rootDir.listFiles { file -> file.isDirectory }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.maxOrNull() ?: 0
        return highest + 1
    }

    private fun headerFor(valueCount: Int): String {
        val header = StringBuilder("timestampMillis")
        for (i in 0 until valueCount) {
            header.append(",value_").append(i)
        }
        return header.toString()
    }

    private fun fileName(sensorType: String, id: Int): String {
        val safe = sensorType.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return "${safe.ifEmpty { "Unknown_Sensor" }}_$id.csv"
    }
}
