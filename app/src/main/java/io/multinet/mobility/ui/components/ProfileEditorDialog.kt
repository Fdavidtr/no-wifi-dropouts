package io.multinet.mobility.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.multinet.mobility.domain.WifiBandPreference
import io.multinet.mobility.domain.WifiSecurityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorDialog(
    existingProfile: ManagedWifiProfile?,
    onDismiss: () -> Unit,
    onSave: (
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType,
        priority: Int,
        preferredBand: WifiBandPreference,
        minSignalDbm: Int,
        allowCellFallback: Boolean,
        enabled: Boolean,
    ) -> Unit,
) {
    var ssid by remember(existingProfile) { mutableStateOf(existingProfile?.ssid.orEmpty()) }
    var passphrase by remember { mutableStateOf("") }
    var securityType by remember(existingProfile) { mutableStateOf(existingProfile?.securityType ?: WifiSecurityType.WPA2) }
    var priority by remember(existingProfile) { mutableIntStateOf(existingProfile?.priority ?: 100) }
    var preferredBand by remember(existingProfile) { mutableStateOf(existingProfile?.preferredBand ?: WifiBandPreference.ANY) }
    var minSignalDbm by remember(existingProfile) { mutableIntStateOf(existingProfile?.minSignalDbm ?: -80) }
    var allowCellFallback by remember(existingProfile) { mutableStateOf(existingProfile?.allowCellFallback ?: true) }
    var enabled by remember(existingProfile) { mutableStateOf(existingProfile?.enabled ?: true) }
    var bandMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingProfile == null) "Nuevo perfil Wi-Fi" else "Editar perfil Wi-Fi")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("SSID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(if (existingProfile == null) "Passphrase" else "Nueva passphrase (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = securityType == WifiSecurityType.WPA2,
                        onClick = { securityType = WifiSecurityType.WPA2 },
                        label = { Text("WPA2") },
                    )
                    FilterChip(
                        selected = securityType == WifiSecurityType.WPA3,
                        onClick = { securityType = WifiSecurityType.WPA3 },
                        label = { Text("WPA3") },
                    )
                }
                OutlinedTextField(
                    value = priority.toString(),
                    onValueChange = { value -> priority = value.toIntOrNull() ?: priority },
                    label = { Text("Prioridad") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minSignalDbm.toString(),
                    onValueChange = { value -> minSignalDbm = value.toIntOrNull() ?: minSignalDbm },
                    label = { Text("Umbral RSSI (dBm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = bandMenuExpanded,
                    onExpandedChange = { bandMenuExpanded = !bandMenuExpanded },
                ) {
                    OutlinedTextField(
                        value = preferredBand.name.replace('_', ' '),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Banda preferida") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bandMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = bandMenuExpanded,
                        onDismissRequest = { bandMenuExpanded = false },
                    ) {
                        WifiBandPreference.entries.forEach { band ->
                            DropdownMenuItem(
                                text = { Text(band.name.replace('_', ' ')) },
                                onClick = {
                                    preferredBand = band
                                    bandMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permitir fallback a celular", style = MaterialTheme.typography.bodyLarge)
                        Text("Si no hay otra Wi-Fi gestionada mejor.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = allowCellFallback, onCheckedChange = { allowCellFallback = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Perfil activo", style = MaterialTheme.typography.bodyLarge)
                        Text("Solo los perfiles activos participan en Mobility Mode.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ssid.trim(),
                        passphrase,
                        securityType,
                        priority,
                        preferredBand,
                        minSignalDbm,
                        allowCellFallback,
                        enabled,
                    )
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
