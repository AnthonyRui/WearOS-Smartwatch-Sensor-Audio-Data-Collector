package com.example.helloworld.presentation

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pushes every file under `sensor_data/` and `audio/` (the watch app's CSV and WAV output) to
 * the paired phone over a direct Bluetooth RFCOMM socket.
 *
 * This used to go over Google Play Services' [com.google.android.gms.wearable.ChannelClient],
 * but on at least one test device GMS's WearableService silently failed to deliver every
 * onChannelOpened/onChannelClosed event to the phone-side listener ("Failed to deliver message
 * to AppKey...", and the receiver service was never even bound) - a Play-Services-side bug, not
 * something fixable from either app. Raw Bluetooth Classic sockets talk directly between the two
 * devices' Bluetooth stacks (they're already bonded, since that's how the Wear connection itself
 * runs) and don't go through Play Services at all.
 *
 * Protocol, one RFCOMM connection per file: a length-prefixed UTF-8 relative path (e.g.
 * `sensor_data/3/Accelerometer_3.csv`), then the file length as a long, then the raw file bytes.
 * [com.example.collectdata.sync.BluetoothFileReceiver] on the phone reads the same framing.
 */
class PhoneSync(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Result of a [sendAllData] call: how many files were sent successfully vs. failed. */
    data class Result(val sent: Int, val failed: Int)

    fun sendAllData(onResult: (Result) -> Unit) {
        val root = context.getExternalFilesDir(null)
        val files = if (root != null) {
            listOf(File(root, "sensor_data"), File(root, "audio"))
                .filter { it.exists() }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            onResult(Result(0, 0))
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission; nothing sent")
            onResult(Result(0, files.size))
            return
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val phone = adapter?.bondedDevices
            ?.firstOrNull { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }
            ?: adapter?.bondedDevices?.firstOrNull()

        if (adapter == null || phone == null) {
            Log.w(TAG, "No paired phone found; nothing sent")
            onResult(Result(0, files.size))
            return
        }

        val queue = ArrayDeque(files)
        val sent = AtomicInteger(0)
        val failed = AtomicInteger(0)
        sendNextInQueue(root!!, phone, queue, sent, failed, onResult)
    }

    private fun sendNextInQueue(
        root: File,
        device: BluetoothDevice,
        queue: ArrayDeque<File>,
        sent: AtomicInteger,
        failed: AtomicInteger,
        onResult: (Result) -> Unit
    ) {
        val file = queue.removeFirstOrNull() ?: run {
            onResult(Result(sent.get(), failed.get()))
            return
        }
        val relativePath = file.relativeTo(root).path.replace(File.separatorChar, '/')
        sendFileWithRetries(device, relativePath, file) { ok ->
            if (ok) sent.incrementAndGet() else failed.incrementAndGet()
            sendNextInQueue(root, device, queue, sent, failed, onResult)
        }
    }

    // Retries a failed transfer a couple of times before giving up: a connection can drop from
    // transient Bluetooth radio contention, which is worth a retry rather than a hard failure.
    private fun sendFileWithRetries(
        device: BluetoothDevice,
        relativePath: String,
        file: File,
        attempt: Int = 0,
        callback: (Boolean) -> Unit
    ) {
        executor.execute {
            val ok = sendFile(device, relativePath, file)
            mainHandler.post {
                if (ok || attempt >= MAX_ATTEMPTS - 1) {
                    callback(ok)
                } else {
                    Log.w(TAG, "Retrying ${file.name} (attempt ${attempt + 2}/$MAX_ATTEMPTS)")
                    mainHandler.postDelayed({
                        sendFileWithRetries(device, relativePath, file, attempt + 1, callback)
                    }, RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun sendFile(device: BluetoothDevice, relativePath: String, file: File): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
            socket.connect()
            DataOutputStream(socket.outputStream).use { out ->
                val pathBytes = relativePath.toByteArray(Charsets.UTF_8)
                out.writeInt(pathBytes.size)
                out.write(pathBytes)
                out.writeLong(file.length())
                file.inputStream().use { it.copyTo(out) }
                out.flush()
            }
            Log.i(TAG, "Sent $relativePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending ${file.name}", e)
            false
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Already failed/closing; nothing more to do.
            }
        }
    }

    companion object {
        private const val TAG = "PhoneSync"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1500L

        /** Must match [com.example.collectdata.sync.BluetoothFileReceiver.SYNC_UUID] on the phone. */
        val SYNC_UUID: UUID = UUID.fromString("bac828a8-9646-4f54-8178-39ecb4933923")
    }
}
