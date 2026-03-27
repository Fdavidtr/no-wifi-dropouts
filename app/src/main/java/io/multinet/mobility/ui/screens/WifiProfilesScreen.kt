package io.multinet.mobility.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.multinet.mobility.domain.ManagedWifiProfile

@Composable
fun WifiProfilesScreen(
    profiles: List<ManagedWifiProfile>,
    onAddProfile: () -> Unit,
    onEditProfile: (ManagedWifiProfile) -> Unit,
    onToggleProfile: (ManagedWifiProfile, Boolean) -> Unit,
    onDeleteProfile: (ManagedWifiProfile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(profile.ssid, style = MaterialTheme.typography.titleMedium)
                            Text("Priority ${profile.priority} · ${profile.preferredBand}")
                            Text("Threshold ${profile.minSignalDbm} dBm · Cellular ${profile.allowCellFallback}")
                        }
                        Switch(
                            checked = profile.enabled,
                            onCheckedChange = { onToggleProfile(profile, it) },
                        )
                        IconButton(onClick = { onEditProfile(profile) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar perfil")
                        }
                        IconButton(onClick = { onDeleteProfile(profile) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Borrar perfil")
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(Icons.Outlined.Add, contentDescription = "Añadir perfil")
            }
        }
    }
}
