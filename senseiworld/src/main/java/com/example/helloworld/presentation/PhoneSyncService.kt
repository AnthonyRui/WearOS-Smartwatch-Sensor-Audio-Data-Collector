package com.example.helloworld.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.helloworld.R

/**
 * Runs [PhoneSync] in the foreground so a multi-file transfer (especially the larger WAV
 * recordings, which can take longer than the watch's screen timeout) survives the screen turning
 * off. Sending straight from [MainActivity] let Wear OS's background execution limits suspend
 * the transfer mid-flight once the screen went to ambient/off, surfacing as
 * ChannelIOException("Channel closed unexpectedly before stream was finished").
 */
class PhoneSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sending_to_phone)))

        PhoneSync(this).sendAllData { result ->
            Log.i(TAG, "Sync finished: sent=${result.sent} failed=${result.failed}")
            broadcastResult(result)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun broadcastResult(result: PhoneSync.Result) {
        val intent = Intent(ACTION_SYNC_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_SENT, result.sent)
            putExtra(EXTRA_FAILED, result.failed)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Phone Sync", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_SYNC_RESULT = "com.example.helloworld.action.SYNC_RESULT"
        const val EXTRA_SENT = "sent"
        const val EXTRA_FAILED = "failed"
        private const val TAG = "PhoneSyncService"
        private const val CHANNEL_ID = "phone_sync_channel"
        private const val NOTIFICATION_ID = 2
    }
}
