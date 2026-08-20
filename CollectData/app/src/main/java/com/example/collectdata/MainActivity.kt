package com.example.collectdata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.collectdata.health.HealthCsvWriter
import com.example.collectdata.health.HealthDataRepository
import com.example.collectdata.health.MetricReadResult
import com.example.collectdata.sync.BluetoothFileReceiver
import com.example.collectdata.sync.receiveWatchChannel
import com.example.collectdata.ui.theme.CollectDataTheme
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var repository: HealthDataRepository
    private lateinit var csvWriter: HealthCsvWriter
    private lateinit var bluetoothFileReceiver: BluetoothFileReceiver

    // Play Services' manifest-declared BIND_LISTENER wake-up was observed to silently fail to
    // deliver channel events on this device, so also listen while the app is in the foreground.
    // Kept as a fallback alongside BluetoothFileReceiver in case Play Services gets fixed.
    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            receiveWatchChannel(this@MainActivity, channel)
        }
    }

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) bluetoothFileReceiver.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HealthDataRepository(this)
        csvWriter = HealthCsvWriter(this)
        Wearable.getChannelClient(this).registerChannelCallback(channelCallback)

        bluetoothFileReceiver = BluetoothFileReceiver(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothFileReceiver.start()
        } else {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        enableEdgeToEdge()
        setContent {
            CollectDataTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HealthSessionScreen(
                        modifier = Modifier.padding(innerPadding),
                        activity = this,
                        repository = repository,
                        csvWriter = csvWriter,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        Wearable.getChannelClient(this).unregisterChannelCallback(channelCallback)
        bluetoothFileReceiver.stop()
        super.onDestroy()
    }
}

/** Identifies one collection window: a session id (for the CSV folder) plus its start/end instants. */
private data class SessionWindow(val id: Int, val start: Instant, val end: Instant)

private sealed interface SessionUiState {
    data object Idle : SessionUiState
    data class Collecting(val start: Instant) : SessionUiState
    data class Fetching(val window: SessionWindow) : SessionUiState
    data class Done(val window: SessionWindow, val folder: String, val results: List<MetricReadResult>) : SessionUiState
    data class Error(val window: SessionWindow?, val message: String) : SessionUiState
}

@Composable
private fun HealthSessionScreen(
    modifier: Modifier = Modifier,
    activity: ComponentActivity,
    repository: HealthDataRepository,
    csvWriter: HealthCsvWriter,
) {
    var state by remember { mutableStateOf<SessionUiState>(SessionUiState.Idle) }
    val scope = rememberCoroutineScope()

    fun fetch(window: SessionWindow) {
        scope.launch {
            state = SessionUiState.Fetching(window)
            try {
                repository.ensurePermissions(activity)
                val results = repository.fetchSession(window.start, window.end)
                val folder = csvWriter.writeSession(window.id, results)
                state = SessionUiState.Done(window, folder.absolutePath, results)
            } catch (e: ResolvablePlatformException) {
                if (e.hasResolution) e.resolve(activity)
                state = SessionUiState.Error(window, e.errorMessage ?: "Samsung Health needs attention")
            } catch (e: HealthDataException) {
                state = SessionUiState.Error(window, e.errorMessage ?: e.message ?: "Unknown Health SDK error")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Health Data Sync", style = MaterialTheme.typography.titleLarge)

        val isCollecting = state is SessionUiState.Collecting
        Button(onClick = {
            val current = state
            if (current is SessionUiState.Collecting) {
                fetch(SessionWindow(csvWriter.nextSessionId(), current.start, Instant.now()))
            } else {
                state = SessionUiState.Collecting(Instant.now())
            }
        }) {
            Text(if (isCollecting) "Stop Session" else "Start Session")
        }

        when (val current = state) {
            is SessionUiState.Idle ->
                Text("Press Start, then Stop, to pull the matching Samsung Health data for that window.")

            is SessionUiState.Collecting ->
                Text("Collecting since ${current.start.atZone(ZoneId.systemDefault())}")

            is SessionUiState.Fetching ->
                Text("Fetching Samsung Health data for session #${current.window.id}...")

            is SessionUiState.Done -> {
                Text("Session #${current.window.id} saved to ${current.folder}")
                LazyColumn {
                    items(current.results) { result -> MetricResultRow(result) }
                }
                Button(onClick = { fetch(current.window) }) {
                    Text("Fetch again (watch data may still be syncing)")
                }
            }

            is SessionUiState.Error -> {
                Text("Error: ${current.message}")
                val window = current.window
                if (window != null) {
                    Button(onClick = { fetch(window) }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun MetricResultRow(result: MetricReadResult) {
    val label = if (result.error != null) {
        "${result.metric.fileLabel}: ${result.error}"
    } else {
        "${result.metric.fileLabel}: ${result.data?.rows?.size ?: 0} rows"
    }
    Text(label)
}
