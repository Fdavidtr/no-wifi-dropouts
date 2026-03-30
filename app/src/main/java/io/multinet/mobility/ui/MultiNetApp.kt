package io.multinet.mobility.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.multinet.mobility.data.db.EventLogEntry
import io.multinet.mobility.data.db.SignalSampleEntry
import io.multinet.mobility.domain.CellularWarmupState
import io.multinet.mobility.domain.ContinuityRuntimeState
import io.multinet.mobility.domain.ContinuitySettingsState
import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.TransportType
import io.multinet.mobility.domain.WifiSignalBucket
import io.multinet.mobility.service.ContinuityService
import io.multinet.mobility.ui.theme.MultiNetTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MultiNetApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingConnect by rememberSaveable { mutableStateOf(false) }

    val runtimePermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val allRuntimePermissionsGranted = runtimePermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = runtimePermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (granted && pendingConnect) {
            viewModel.markIntroCompleted()
            ContinuityService.start(context)
        }
        pendingConnect = false
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val statusLine = remember(uiState, allRuntimePermissionsGranted) {
        buildStatusLine(
            runtimeState = uiState.runtime,
            modeEnabled = uiState.settings.modeEnabled,
            allPermissionsGranted = allRuntimePermissionsGranted,
        )
    }
    val networkLine = remember(uiState.runtime.snapshot) {
        buildNetworkLine(uiState.runtime.snapshot)
    }

    MultiNetScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        allRuntimePermissionsGranted = allRuntimePermissionsGranted,
        statusLine = statusLine,
        networkLine = networkLine,
        onPrimaryAction = {
            if (uiState.settings.modeEnabled) {
                ContinuityService.stop(context)
            } else if (!allRuntimePermissionsGranted) {
                pendingConnect = true
                permissionLauncher.launch(runtimePermissions)
            } else {
                viewModel.markIntroCompleted()
                ContinuityService.start(context)
            }
        },
        onToggleDiagnostics = {
            viewModel.setDiagnosticsUnlocked(!uiState.settings.diagnosticsUnlocked)
        },
        onHideDiagnostics = {
            viewModel.setDiagnosticsUnlocked(false)
        },
    )
}

@Composable
private fun MultiNetScreen(
    uiState: MainUiState,
    snackbarHostState: SnackbarHostState,
    allRuntimePermissionsGranted: Boolean,
    statusLine: String,
    networkLine: String,
    onPrimaryAction: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onHideDiagnostics: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "No WiFi Dropouts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(24.dp))
                StatusCard(
                    statusLine = statusLine,
                    networkLine = networkLine,
                    diagnosticsVisible = uiState.settings.diagnosticsUnlocked,
                    onToggleDiagnostics = onToggleDiagnostics,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onPrimaryAction,
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = if (uiState.settings.modeEnabled) "Disconnect" else "Connect",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (allRuntimePermissionsGranted) {
                        "Local Wi-Fi + mobile data continuity."
                    } else {
                        "Location, nearby Wi-Fi, or notification access is missing."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (uiState.settings.diagnosticsUnlocked) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DiagnosticsPanel(
                        runtimeState = uiState.runtime,
                        events = uiState.events,
                        signalSamples = uiState.signalSamples,
                        onHide = onHideDiagnostics,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    statusLine: String,
    networkLine: String,
    diagnosticsVisible: Boolean,
    onToggleDiagnostics: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = networkLine,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onToggleDiagnostics) {
                Text(if (diagnosticsVisible) "Hide diagnostics" else "Show diagnostics")
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    runtimeState: ContinuityRuntimeState,
    events: List<EventLogEntry>,
    signalSamples: List<SignalSampleEntry>,
    onHide: () -> Unit,
) {
    var selectedEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    var eventsExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedEvent = remember(selectedEventId, events) {
        events.firstOrNull { it.id == selectedEventId }
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            DiagnosticLine("Default network", runtimeState.snapshot.defaultTransport.name)
            DiagnosticLine("Wi-Fi validated", if (runtimeState.snapshot.validated) "Yes" else "No")
            DiagnosticLine("Wi-Fi signal", runtimeState.wifiSignalBucket.name)
            DiagnosticLine("Mobile state", runtimeState.cellularWarmupState.name)
            DiagnosticLine(
                "Last transition",
                runtimeState.lastTransitionAtEpochMillis?.let(::formatTimestamp) ?: "No changes",
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Signal history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SignalHistoryChart(
                samples = signalSamples,
                events = events,
                selectedEventId = selectedEventId,
                onEventSelected = { selectedEventId = it.id },
            )
            if (selectedEvent == null && signalSamples.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap an event marker to inspect what happened at that point in the timeline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedEvent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                EventDetailCard(event = selectedEvent)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Recent events",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(
                    onClick = { eventsExpanded = !eventsExpanded },
                    enabled = events.isNotEmpty(),
                ) {
                    Text(if (eventsExpanded) "Hide events" else "Show events")
                }
            }
            if (eventsExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (events.isEmpty()) {
                    Text(
                        text = "No events recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    events.forEach { event ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "${formatTimestamp(event.timestampEpochMillis)} · ${event.category}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onHide,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Hide")
            }
        }
    }
}

@Composable
private fun EventDetailCard(
    event: EventLogEntry,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Selected event",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            DiagnosticLine("Time", formatTimestamp(event.timestampEpochMillis))
            DiagnosticLine("Category", event.category)
            DiagnosticLine("Severity", event.severity)
            if (!event.ssid.isNullOrBlank()) {
                DiagnosticLine("Wi-Fi", event.ssid)
            }
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun MultiNetScreenPreview() {
    val previewState = previewUiState()

    MultiNetTheme {
        MultiNetScreen(
            uiState = previewState,
            snackbarHostState = remember { SnackbarHostState() },
            allRuntimePermissionsGranted = true,
            statusLine = buildStatusLine(
                runtimeState = previewState.runtime,
                modeEnabled = previewState.settings.modeEnabled,
                allPermissionsGranted = true,
            ),
            networkLine = buildNetworkLine(previewState.runtime.snapshot),
            onPrimaryAction = {},
            onToggleDiagnostics = {},
            onHideDiagnostics = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun DiagnosticsPanelPreview() {
    val previewState = previewUiState()

    MultiNetTheme {
        DiagnosticsPanel(
            runtimeState = previewState.runtime,
            events = previewState.events,
            signalSamples = previewState.signalSamples,
            onHide = {},
        )
    }
}

private fun previewUiState(): MainUiState {
    val baseTime = 1_711_800_000_000L
    val previewEvents = listOf(
        EventLogEntry(
            id = 1,
            timestampEpochMillis = baseTime + 60_000L,
            severity = "INFO",
            category = "continuity_controller",
            message = "Continuity monitoring started.",
            ssid = "Home Wi-Fi",
        ),
        EventLogEntry(
            id = 2,
            timestampEpochMillis = baseTime + 180_000L,
            severity = "WARN",
            category = "wifi_signal",
            message = "Wi-Fi signal dropped below the warmup threshold.",
            ssid = "Home Wi-Fi",
        ),
        EventLogEntry(
            id = 3,
            timestampEpochMillis = baseTime + 240_000L,
            severity = "INFO",
            category = "network_transition",
            message = "Default network switched to mobile data.",
            ssid = "Home Wi-Fi",
        ),
    )
    val previewSamples = listOf(
        SignalSampleEntry(
            id = 1,
            timestampEpochMillis = baseTime,
            networkId = "wifi-home",
            ssid = "Home Wi-Fi",
            rssi = -67,
            thresholdRssi = -78,
            frequencyMhz = 5180,
            bucket = WifiSignalBucket.GOOD.name,
            validated = true,
        ),
        SignalSampleEntry(
            id = 2,
            timestampEpochMillis = baseTime + 60_000L,
            networkId = "wifi-home",
            ssid = "Home Wi-Fi",
            rssi = -71,
            thresholdRssi = -78,
            frequencyMhz = 5180,
            bucket = WifiSignalBucket.GOOD.name,
            validated = true,
        ),
        SignalSampleEntry(
            id = 3,
            timestampEpochMillis = baseTime + 120_000L,
            networkId = "wifi-home",
            ssid = "Home Wi-Fi",
            rssi = -77,
            thresholdRssi = -78,
            frequencyMhz = 5180,
            bucket = WifiSignalBucket.WEAK.name,
            validated = true,
        ),
        SignalSampleEntry(
            id = 4,
            timestampEpochMillis = baseTime + 180_000L,
            networkId = "wifi-home",
            ssid = "Home Wi-Fi",
            rssi = -82,
            thresholdRssi = -78,
            frequencyMhz = 5180,
            bucket = WifiSignalBucket.CRITICAL.name,
            validated = false,
        ),
        SignalSampleEntry(
            id = 5,
            timestampEpochMillis = baseTime + 240_000L,
            networkId = "wifi-home",
            ssid = "Home Wi-Fi",
            rssi = -84,
            thresholdRssi = -78,
            frequencyMhz = 5180,
            bucket = WifiSignalBucket.CRITICAL.name,
            validated = false,
        ),
    )

    return MainUiState(
        settings = ContinuitySettingsState(
            modeEnabled = true,
            introCompleted = true,
            diagnosticsUnlocked = true,
        ),
        runtime = ContinuityRuntimeState(
            modeEnabled = true,
            isMonitoring = true,
            snapshot = ConnectivitySnapshot(
                currentNetworkId = "cellular-fallback",
                defaultTransport = TransportType.CELLULAR,
                validated = true,
                internetAvailable = true,
                wifiSsid = "Home Wi-Fi",
                rssi = -84,
                frequencyMhz = 5180,
                updatedAtEpochMillis = baseTime + 240_000L,
            ),
            wifiSignalBucket = WifiSignalBucket.CRITICAL,
            cellularAvailable = true,
            cellularWarmupState = CellularWarmupState.AVAILABLE,
            lastTransitionAtEpochMillis = baseTime + 240_000L,
        ),
        events = previewEvents,
        signalSamples = previewSamples,
    )
}

private fun buildStatusLine(
    runtimeState: ContinuityRuntimeState,
    modeEnabled: Boolean,
    allPermissionsGranted: Boolean,
): String {
    if (!allPermissionsGranted) {
        return "Permissions required"
    }
    if (!modeEnabled) {
        return "Protection inactive"
    }

    return when {
        runtimeState.snapshot.defaultTransport == TransportType.CELLULAR -> "Mobile fallback active"
        runtimeState.cellularWarmupState == CellularWarmupState.REQUESTING -> "Warming up mobile data"
        runtimeState.cellularWarmupState == CellularWarmupState.UNAVAILABLE -> {
            "Protection active · no mobile backup"
        }

        runtimeState.snapshot.defaultTransport == TransportType.WIFI &&
            runtimeState.snapshot.validated &&
            !runtimeState.snapshot.dataStallSuspected &&
            runtimeState.wifiSignalBucket != io.multinet.mobility.domain.WifiSignalBucket.WEAK &&
            runtimeState.wifiSignalBucket != io.multinet.mobility.domain.WifiSignalBucket.CRITICAL -> {
            "Protection active · Wi-Fi stable"
        }

        runtimeState.cellularWarmupState == CellularWarmupState.AVAILABLE ||
            runtimeState.cellularWarmupState == CellularWarmupState.HOLDING -> {
            "Protection active · backup ready"
        }

        runtimeState.snapshot.defaultTransport == TransportType.NONE -> "Looking for connectivity"
        else -> "Protection active · Wi-Fi at risk"
    }
}

private fun buildNetworkLine(snapshot: ConnectivitySnapshot): String = when (snapshot.defaultTransport) {
    TransportType.WIFI -> {
        val ssid = snapshot.wifiSsid ?: "Wi-Fi"
        "Current network: $ssid"
    }

    TransportType.CELLULAR -> "Current network: mobile data"
    TransportType.NONE -> "No default network"
    else -> "Current network: ${snapshot.defaultTransport.name.lowercase()}"
}

private fun formatTimestamp(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))
