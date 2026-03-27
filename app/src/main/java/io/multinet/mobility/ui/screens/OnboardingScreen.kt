package io.multinet.mobility.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    hasRuntimePermissions: Boolean,
    profileCount: Int,
    approvalLabel: String,
    onRequestPermissions: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onAddProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Android Mobility Mode", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Configura primero permisos, aprobación de sugerencias Wi-Fi y al menos un SSID de casa. Sin eso la app se queda en modo observación.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. Permisos", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasRuntimePermissions) "Permisos concedidos." else "Faltan permisos de ubicación, Wi-Fi cercana o notificaciones.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestPermissions) {
                    Text("Revisar permisos")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("2. Aprobación de sugerencias Wi-Fi", style = MaterialTheme.typography.titleMedium)
                Text("Estado actual: $approvalLabel", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenWifiSettings) {
                    Text("Abrir ajustes Wi-Fi")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("3. Wi-Fi de casa", style = MaterialTheme.typography.titleMedium)
                Text("Perfiles cargados: $profileCount", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAddProfile) {
                    Text("Añadir perfil Wi-Fi")
                }
            }
        }
    }
}

