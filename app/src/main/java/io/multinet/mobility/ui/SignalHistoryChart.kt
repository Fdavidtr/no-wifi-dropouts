package io.multinet.mobility.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.multinet.mobility.data.db.EventLogEntry
import io.multinet.mobility.data.db.SignalSampleEntry
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SignalHistoryChart(
    samples: List<SignalSampleEntry>,
    events: List<EventLogEntry>,
    selectedEventId: Long?,
    onEventSelected: (EventLogEntry) -> Unit,
) {
    val chartModel = remember(samples, events) {
        buildSignalChartModel(samples = samples, events = events)
    }

    if (chartModel == null) {
        Text(
            text = "Waiting for Wi-Fi signal samples.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val density = LocalDensity.current
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = MaterialTheme.colorScheme.tertiary
    val signalColor = MaterialTheme.colorScheme.primary
    Column {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val markerRadiusPx = with(density) { 6.dp.toPx() }

            Canvas(modifier = Modifier.matchParentSize()) {
                val signalPath = Path()
                val thresholdPath = Path()
                chartModel.samples.forEachIndexed { index, sample ->
                    val point = chartModel.toOffset(
                        width = size.width,
                        height = size.height,
                        timestamp = sample.timestampEpochMillis,
                        rssi = sample.rssi,
                    )
                    val thresholdPoint = chartModel.toOffset(
                        width = size.width,
                        height = size.height,
                        timestamp = sample.timestampEpochMillis,
                        rssi = sample.thresholdRssi,
                    )

                    if (index == 0) {
                        signalPath.moveTo(point.x, point.y)
                        thresholdPath.moveTo(thresholdPoint.x, thresholdPoint.y)
                    } else {
                        signalPath.lineTo(point.x, point.y)
                        thresholdPath.lineTo(thresholdPoint.x, thresholdPoint.y)
                    }
                }

                repeat(3) { index ->
                    val y = size.height * (index / 2f)
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                drawPath(
                    path = thresholdPath,
                    color = thresholdColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
                drawPath(
                    path = signalPath,
                    color = signalColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            chartModel.markers.forEach { marker ->
                val offset = chartModel.toOffset(
                    width = widthPx,
                    height = heightPx,
                    timestamp = marker.timestampEpochMillis,
                    rssi = marker.markerRssi,
                )
                val isSelected = marker.event.id == selectedEventId

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (offset.x - markerRadiusPx).roundToInt(),
                                y = (offset.y - markerRadiusPx).roundToInt(),
                            )
                        }
                        .size(if (isSelected) 14.dp else 12.dp)
                        .clip(CircleShape)
                        .background(markerColor(marker.event.severity, isSelected))
                        .clickable { onEventSelected(marker.event) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = signalColor)
            Text(
                text = "Signal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(12.dp))
            LegendDot(color = thresholdColor)
            Text(
                text = "Warmup threshold",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(12.dp))
            LegendDot(color = MaterialTheme.colorScheme.error)
            Text(
                text = "Event",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun markerColor(
    severity: String,
    selected: Boolean,
): Color = when {
    selected -> Color(0xFF111827)
    severity == "ERROR" -> Color(0xFFB91C1C)
    severity == "WARN" -> Color(0xFFD97706)
    else -> Color(0xFF2563EB)
}

private data class SignalChartModel(
    val samples: List<SignalSampleEntry>,
    val markers: List<SignalChartMarker>,
    val minTimestamp: Long,
    val maxTimestamp: Long,
    val minRssi: Int,
    val maxRssi: Int,
) {
    fun toOffset(
        width: Float,
        height: Float,
        timestamp: Long,
        rssi: Int,
    ): Offset {
        val timeSpan = max(maxTimestamp - minTimestamp, 1L).toFloat()
        val rssiSpan = max(maxRssi - minRssi, 1).toFloat()
        val x = ((timestamp - minTimestamp) / timeSpan) * width
        val y = ((maxRssi - rssi) / rssiSpan) * height
        return Offset(x = x, y = y)
    }
}

private data class SignalChartMarker(
    val event: EventLogEntry,
    val timestampEpochMillis: Long,
    val markerRssi: Int,
)

private fun buildSignalChartModel(
    samples: List<SignalSampleEntry>,
    events: List<EventLogEntry>,
): SignalChartModel? {
    if (samples.isEmpty()) return null

    val sortedSamples = samples.sortedBy { it.timestampEpochMillis }
    val minTimestamp = sortedSamples.first().timestampEpochMillis
    val maxTimestamp = sortedSamples.last().timestampEpochMillis

    val rssiFloor = sortedSamples.minOf { minOf(it.rssi, it.thresholdRssi) } - 4
    val rssiCeiling = sortedSamples.maxOf { maxOf(it.rssi, it.thresholdRssi) } + 4

    val markers = events
        .filter { it.timestampEpochMillis in minTimestamp..maxTimestamp }
        .sortedBy { it.timestampEpochMillis }
        .map { event ->
            val anchorSample = sortedSamples
                .lastOrNull { sample -> sample.timestampEpochMillis <= event.timestampEpochMillis }
                ?: sortedSamples.firstOrNull()
                ?: return@map null

            SignalChartMarker(
                event = event,
                timestampEpochMillis = event.timestampEpochMillis,
                markerRssi = anchorSample.rssi,
            )
        }
        .filterNotNull()

    return SignalChartModel(
        samples = sortedSamples,
        markers = markers,
        minTimestamp = minTimestamp,
        maxTimestamp = maxTimestamp,
        minRssi = rssiFloor,
        maxRssi = rssiCeiling,
    )
}
