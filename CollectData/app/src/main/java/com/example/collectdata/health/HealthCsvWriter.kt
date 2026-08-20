package com.example.collectdata.health

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Persists Health SDK query results to per-metric CSV files, mirroring the watch app's
 * numbered-session convention: `health_data/<n>/<Metric>_<n>.csv` under the external files dir.
 * Session numbers on this device are independent from the watch app's session numbers on its
 * device — line them up by comparing the timestamp columns, not the folder number.
 */
class HealthCsvWriter(context: Context) {

    private val rootDir: File = run {
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(parent, "health_data")
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Unable to create health CSV root: ${dir.absolutePath}")
        }
        dir
    }

    /** One greater than the highest existing session folder, so ids keep counting up across restarts. */
    fun nextSessionId(): Int {
        val highest = rootDir.listFiles { file -> file.isDirectory }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.maxOrNull() ?: 0
        return highest + 1
    }

    /** Writes one CSV per metric for [sessionId], overwriting any previous attempt (e.g. a "fetch again"). */
    fun writeSession(sessionId: Int, results: List<MetricReadResult>): File {
        val dir = File(rootDir, sessionId.toString())
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Unable to create session folder: ${dir.absolutePath}")
        }
        for (result in results) {
            val data = result.data ?: continue
            val file = File(dir, "${result.metric.fileLabel}_$sessionId.csv")
            try {
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    writer.append(data.header.joinToString(","))
                    writer.newLine()
                    for (row in data.rows) {
                        writer.append(row.joinToString(",") { escape(it) })
                        writer.newLine()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing CSV for ${result.metric.fileLabel}", e)
            }
        }
        return dir
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    companion object {
        private const val TAG = "HealthCsvWriter"
    }
}
