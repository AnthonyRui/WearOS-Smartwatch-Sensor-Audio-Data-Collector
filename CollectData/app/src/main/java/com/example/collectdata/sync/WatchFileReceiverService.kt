package com.example.collectdata.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileOutputStream

private const val TAG = "WatchFileReceiver"

/**
 * Receives the watch app's sensor CSVs and audio WAVs, sent over a [ChannelClient] channel by
 * `PhoneSync` on the watch. Each channel's path looks like `/watch-data/sensor_data/3/foo.csv`
 * or `/watch-data/audio/bar.wav`; stripping the `/watch-data/` prefix reproduces the watch's own
 * `sensor_data/` and `audio/` layout under this app's external files dir, alongside the existing
 * `health_data/` folder written by [com.example.collectdata.health.HealthCsvWriter].
 *
 * Called both from [WatchFileReceiverService] (the manifest-declared wake-up path for when this
 * app isn't running) and from a runtime [ChannelClient.ChannelCallback] registered while the app
 * is in the foreground - Play Services' manifest-based BIND_LISTENER wake-up was observed to
 * silently fail to deliver channel events on this device (GMS logs
 * "Failed to deliver message to AppKey..." for every onChannelOpened/onChannelClosed, and the
 * receiver service is never even bound), so the foreground path can't rely on it alone.
 */
fun receiveWatchChannel(context: Context, channel: ChannelClient.Channel) {
    val relativePath = channel.path.removePrefix("/watch-data/")
    val root = context.getExternalFilesDir(null) ?: context.filesDir
    val outFile = File(root, relativePath)
    outFile.parentFile?.mkdirs()

    val channelClient = Wearable.getChannelClient(context)
    channelClient.getInputStream(channel)
        .addOnSuccessListener { input ->
            try {
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
                Log.i(TAG, "Received ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing ${outFile.absolutePath}", e)
            } finally {
                input.close()
                channelClient.close(channel)
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Failed to open input stream for ${channel.path}", e)
        }
}

class WatchFileReceiverService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        receiveWatchChannel(this, channel)
    }
}
