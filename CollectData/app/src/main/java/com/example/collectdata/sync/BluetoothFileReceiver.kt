package com.example.collectdata.sync

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Accepts the watch app's `PhoneSync` connections over a direct Bluetooth RFCOMM socket and
 * writes each incoming file under this app's external files dir, mirroring the watch's own
 * `sensor_data/` and `audio/` layout - alongside the existing `health_data/` folder written by
 * [com.example.collectdata.health.HealthCsvWriter].
 *
 * Replaces [WatchFileReceiverService] (Google Play Services' ChannelClient/Wearable Data Layer),
 * which on at least one test device silently failed to deliver every channel event to this app -
 * a Play-Services-side bug. Raw Bluetooth sockets talk directly between the two devices' already
 * -bonded Bluetooth stacks and don't go through Play Services at all.
 *
 * Requires this app to be running to accept connections - there's no manifest-declared wake-up
 * for incoming RFCOMM connections the way there is for ChannelClient, so [start] is called from
 * `MainActivity.onCreate`.
 */
class BluetoothFileReceiver(private val context: Context) {

    private val running = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null

    fun start() {
        if (running.getAndSet(true)) return
        thread(name = "btfr-server") {
            try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                if (adapter == null) {
                    Log.e(TAG, "No Bluetooth adapter; can't receive watch data")
                    return@thread
                }
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("WatchFileSync", SYNC_UUID)
                Log.i(TAG, "Listening for watch connections")
                while (running.get()) {
                    Log.i(TAG, "Waiting on accept()...")
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "accept() failed", e)
                        break
                    }
                    Log.i(TAG, "Accepted connection from ${socket.remoteDevice?.address}")
                    handleConnection(socket)
                }
                Log.i(TAG, "Accept loop exited")
            } catch (e: Exception) {
                Log.e(TAG, "Server socket setup failed", e)
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Already closing; nothing more to do.
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        thread(name = "btfr-conn") {
            Log.i(TAG, "Connection handler thread started")
            try {
                DataInputStream(socket.inputStream).use { input ->
                    val pathLength = input.readInt()
                    Log.i(TAG, "Read path length: $pathLength")
                    val pathBytes = ByteArray(pathLength)
                    input.readFully(pathBytes)
                    val relativePath = String(pathBytes, Charsets.UTF_8)
                    val fileLength = input.readLong()
                    Log.i(TAG, "Header: path=$relativePath length=$fileLength")

                    val root = context.getExternalFilesDir(null) ?: context.filesDir
                    val outFile = File(root, relativePath)
                    outFile.parentFile?.mkdirs()

                    FileOutputStream(outFile).use { output ->
                        copyExactly(input, output, fileLength)
                    }
                    Log.i(TAG, "Received ${outFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed receiving file", e)
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Already closing; nothing more to do.
                }
            }
        }
    }

    private fun copyExactly(input: DataInputStream, output: FileOutputStream, length: Long) {
        val buffer = ByteArray(8192)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    companion object {
        private const val TAG = "BluetoothFileReceiver"

        /** Must match [com.example.helloworld.presentation.PhoneSync.SYNC_UUID] on the watch. */
        val SYNC_UUID: UUID = UUID.fromString("bac828a8-9646-4f54-8178-39ecb4933923")
    }
}
