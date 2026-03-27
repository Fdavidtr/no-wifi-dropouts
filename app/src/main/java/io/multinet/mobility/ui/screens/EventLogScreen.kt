package io.multinet.mobility.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.multinet.mobility.data.db.EventLogEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun EventLogScreen(
    events: List<EventLogEntry>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(event.category, style = MaterialTheme.typography.titleMedium)
                    Text(event.message, style = MaterialTheme.typography.bodyMedium)
                    Text("Severity: ${event.severity} · SSID: ${event.ssid ?: "N/A"}")
                    Text(DateFormat.getDateTimeInstance().format(Date(event.timestampEpochMillis)))
                }
            }
        }
    }
}

