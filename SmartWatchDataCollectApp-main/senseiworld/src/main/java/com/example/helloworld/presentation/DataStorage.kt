package com.example.helloworld.presentation

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Deletes everything under `sensor_data/` and `audio/` (the watch app's collected CSVs and WAV
 * recordings). Session numbering in [SensorCsvWriter] is derived by listing folders on disk, so
 * clearing these directories naturally restarts session ids at 1 for the next collection — handy
 * for resetting between different users' collection runs.
 */
object DataStorage {

    fun clearAllData(context: Context): Boolean {
        val root = context.getExternalFilesDir(null) ?: return false
        var allDeleted = true
        for (name in listOf("sensor_data", "audio")) {
            val dir = File(root, name)
            if (dir.exists() && !dir.deleteRecursively()) {
                Log.w(TAG, "Failed to fully delete $name")
                allDeleted = false
            }
        }
        return allDeleted
    }

    private const val TAG = "DataStorage"
}
