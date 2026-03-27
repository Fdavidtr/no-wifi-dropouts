package io.multinet.mobility.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.multinet.mobility.domain.MobilityRuntimeState

@Composable
fun DashboardScreen(
    runtimeState: MobilityRuntimeState,
    mobilityModeEnabled: Boolean,
    approvalLabel: String,
    onToggleMobilityMode: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mobility Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Activa el servicio foreground que observa red y ajusta las Wi-Fi gestionadas.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = mobilityModeEnabled, onCheckedChange = onToggleMobilityMode)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Red actual", style = MaterialTheme.typography.titleMedium)
                    Text("Transporte: ${runtimeState.snapshot.defaultTransport}")
                    Text("SSID: ${runtimeState.snapshot.wifiSsid ?: "N/A"}")
                    Text("Validada: ${runtimeState.snapshot.validated}")
                    Text("Metered: ${runtimeState.snapshot.metered}")
                    Text("RSSI: ${runtimeState.snapshot.rssi ?: 0} dBm")
                    Text("Banda: ${runtimeState.snapshot.wifiBand}")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Capacidades", style = MaterialTheme.typography.titleMedium)
                    Text("API level: ${runtimeState.capabilities.apiLevel}")
                    Text("Diagnostics: ${runtimeState.capabilities.hasConnectivityDiagnostics}")
                    Text("Suggestion approval listener: ${runtimeState.capabilities.hasSuggestionApprovalListener}")
                    Text("Multi-internet Wi-Fi: ${runtimeState.capabilities.supportsMultiInternetWifi}")
                    Text("Multi-internet mode: ${runtimeState.capabilities.multiInternetModeLabel}")
                    Text("Aprobación sugerencias: $approvalLabel")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Última decisión", style = MaterialTheme.typography.titleMedium)
                    Text(runtimeState.lastDecision.toString())
                }
            }
        }

        if (runtimeState.cooldowns.isNotEmpty()) {
            items(runtimeState.cooldowns.entries.toList(), key = { it.key }) { (ssid, remainingMs) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(ssid, style = MaterialTheme.typography.titleMedium)
                        Text("Cooldown restante: ${remainingMs / 1000}s")
                    }
                }
            }
        }
    }
}

