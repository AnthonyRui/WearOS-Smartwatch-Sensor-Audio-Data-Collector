package com.example.helloworld.presentation

import android.Manifest
import com.example.helloworld.R
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var collectionService: SensorCollectionService? = null
    private var isBound = false
    // Mirrors the service's collecting state.
    private var isCollectingData = false
    private var isSyncing = false
    private lateinit var collectDataButton: Button
    private lateinit var sendToPhoneButton: Button
    private lateinit var resetDataButton: Button

    private val syncResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sent = intent?.getIntExtra(PhoneSyncService.EXTRA_SENT, 0) ?: 0
            val failed = intent?.getIntExtra(PhoneSyncService.EXTRA_FAILED, 0) ?: 0
            isSyncing = false
            sendToPhoneButton.isEnabled = true
            Toast.makeText(
                this@MainActivity,
                getString(R.string.send_to_phone_result, sent, failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            collectionService = (binder as SensorCollectionService.LocalBinder).getService()
            isBound = true
            isCollectingData = collectionService?.isCollecting == true
            updateButtonState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collectionService = null
            isBound = false
        }
    }

    // Requests BODY_SENSORS + ACTIVITY_RECOGNITION + RECORD_AUDIO.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants: Map<String, Boolean> ->
        if (grants[Manifest.permission.BODY_SENSORS] == true) {
            if (grants[Manifest.permission.ACTIVITY_RECOGNITION] == false) {
                Toast.makeText(this, "Step data unavailable (activity permission denied)", Toast.LENGTH_SHORT).show()
            }
            if (grants[Manifest.permission.RECORD_AUDIO] == false) {
                Toast.makeText(this, "Audio recording unavailable (microphone permission denied)", Toast.LENGTH_SHORT).show()
            }
            requestOptionalPermissionsThenToggle()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Requests POST_NOTIFICATIONS.
    private val requestOptionalPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { requestBackgroundBodySensorsThenToggle() }

    // Requests BODY_SENSORS_BACKGROUND.
    private val requestBackgroundBodySensorsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Background sensor access denied; some sensors may pause when the app isn't in view",
                Toast.LENGTH_LONG
            ).show()
        }
        toggleDataCollection()
    }

    // Requests BLUETOOTH_CONNECT, needed to open a socket to the paired phone.
    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startPhoneSync()
        } else {
            isSyncing = false
            sendToPhoneButton.isEnabled = true
            Toast.makeText(this, "Bluetooth permission denied; nothing sent", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        collectDataButton = findViewById(R.id.button_collect_data)
        collectDataButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        sendToPhoneButton = findViewById(R.id.button_send_to_phone)
        sendToPhoneButton.setOnClickListener {
            sendDataToPhone()
        }

        resetDataButton = findViewById(R.id.button_reset_data)
        resetDataButton.setOnClickListener {
            confirmResetData()
        }
    }

    override fun onStart() {
        super.onStart()
        // Binds to the service to read its current collecting state.
        Intent(this, SensorCollectionService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        val filter = IntentFilter(PhoneSyncService.ACTION_SYNC_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(syncResultReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(syncResultReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
            collectionService = null
        }
        unregisterReceiver(syncResultReceiver)
    }

    // Requests BODY_SENSORS + ACTIVITY_RECOGNITION (API 29+) + RECORD_AUDIO if needed, then toggles collection.
    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.RECORD_AUDIO)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            requestPermissionLauncher.launch(permissions)
        } else {
            requestOptionalPermissionsThenToggle()
        }
    }

    private fun requestOptionalPermissionsThenToggle() {
        val missingOptional = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        } else {
            emptyList()
        }

        if (missingOptional.isNotEmpty()) {
            requestOptionalPermissionLauncher.launch(missingOptional.toTypedArray())
        } else {
            requestBackgroundBodySensorsThenToggle()
        }
    }

    private fun requestBackgroundBodySensorsThenToggle() {
        val needsBackgroundBodySensors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED

        if (needsBackgroundBodySensors) {
            requestBackgroundBodySensorsLauncher.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
        } else {
            toggleDataCollection()
        }
    }

    // Starts or stops the collection service and updates the button/toast to match.
    private fun toggleDataCollection() {
        Log.i("MainActivity", "toggleDataCollection triggered, wasCollecting=$isCollectingData")
        val intent = Intent(this, SensorCollectionService::class.java)
        if (isCollectingData) {
            intent.action = SensorCollectionService.ACTION_STOP
            startService(intent)
            Toast.makeText(this, "Stopped collecting data", Toast.LENGTH_SHORT).show()
        } else {
            intent.action = SensorCollectionService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Started collecting data", Toast.LENGTH_SHORT).show()
        }
        isCollectingData = !isCollectingData
        updateButtonState()
    }

    private fun updateButtonState() {
        collectDataButton.text = getString(
            if (isCollectingData) R.string.stop_collecting_data else R.string.start_collecting_data
        )
    }

    // Sends every saved CSV/audio file to a connected phone via a foreground service, so the
    // transfer survives the watch's screen turning off mid-way through a large WAV file.
    private fun sendDataToPhone() {
        isSyncing = true
        sendToPhoneButton.isEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            startPhoneSync()
        }
    }

    private fun startPhoneSync() {
        Toast.makeText(this, getString(R.string.sending_to_phone), Toast.LENGTH_SHORT).show()
        ContextCompat.startForegroundService(this, Intent(this, PhoneSyncService::class.java))
    }

    // Confirms, then clears all collected sensor/audio data so the next session starts clean
    // (e.g. between different users). Blocked while a collection or a phone sync is in progress.
    private fun confirmResetData() {
        if (isCollectingData) {
            Toast.makeText(this, getString(R.string.reset_data_blocked), Toast.LENGTH_SHORT).show()
            return
        }
        if (isSyncing) {
            Toast.makeText(this, getString(R.string.reset_data_blocked_syncing), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.reset_data)
            .setMessage(R.string.reset_data_confirm_message)
            .setPositiveButton(R.string.reset_data) { _, _ -> resetData() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetData() {
        resetDataButton.isEnabled = false
        Thread {
            val success = DataStorage.clearAllData(this)
            runOnUiThread {
                resetDataButton.isEnabled = true
                Toast.makeText(
                    this,
                    getString(if (success) R.string.reset_data_success else R.string.reset_data_failed),
                    Toast.LENGTH_SHORT
                ).show()
                Log.i("MainActivity", "Reset data finished: success=$success")
            }
        }.start()
    }
}
