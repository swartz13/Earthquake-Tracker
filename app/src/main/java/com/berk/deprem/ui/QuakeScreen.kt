package com.berk.deprem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berk.deprem.core.AlertPolicy
import com.berk.deprem.core.Geo
import com.berk.deprem.core.Intensity
import com.berk.deprem.core.LocationStatus
import com.berk.deprem.core.Places
import com.berk.deprem.core.Settings
import com.berk.deprem.core.SourceStatus
import com.berk.deprem.model.Quake
import com.berk.deprem.model.Source
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuakeScreen(
    quakes: List<Quake>,
    placesReady: Boolean,
    status: Map<Source, SourceStatus>,
    settings: Settings,
    serviceRunning: Boolean,
    online: Boolean,
    locationStatus: LocationStatus?,
    batteryUnrestricted: Boolean,
    onToggleMonitor: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onFixBattery: () -> Unit,
    onRefresh: () -> Unit,
) {
    val s = LocalStrings.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.appName) },
                actions = {
                    Switch(checked = serviceRunning, onCheckedChange = onToggleMonitor)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = s.settings)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatusCard(status, settings, serviceRunning, online, locationStatus, now, onRefresh)
            }

            val hepsiAgHatasi = Source.entries.all { status[it]?.lastError.isNetworkError() }
            if (!online || hepsiAgHatasi) {
                item { OfflineWarning(systemSaysOnline = online, onRetry = onRefresh) }
            }

            val visible = quakes.filter {
                AlertPolicy.meetsThreshold(it.mag, settings.listMinMag) || it.notifiedLevel >= 0
            }
            val hidden = quakes.size - visible.size

            if (!batteryUnrestricted) {
                item { BatteryWarning(onFixBattery) }
            }

            if (visible.isEmpty()) {
                item { EmptyState(serviceRunning) }
            }

            items(visible, key = { it.id }) { q ->
                QuakeCard(q, settings, placesReady, now)
            }

            if (hidden > 0) {
                item {
                    Text(
                        s.hiddenRecordsNotice(hidden, Fmt.mag(settings.listMinMag)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: Map<Source, SourceStatus>,
    settings: Settings,
    serviceRunning: Boolean,
    online: Boolean,
    locationStatus: LocationStatus?,
    now: Long,
    onRefresh: () -> Unit,
) {
    val s = LocalStrings.current
    var selectedSource by remember { mutableStateOf<Source?>(null) }

    if (selectedSource != null) {
        val src = selectedSource!!
        val st = status[src]
        AlertDialog(
            onDismissRequest = { selectedSource = null },
            title = { Text(s.sourceDialogTitle(src)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            st?.liveSocket == true -> s.connectionLiveWs
                            st?.lastError.isNetworkError() -> s.connectionNetworkError
                            st?.lastError != null -> s.connectionError
                            st != null && st.lastSuccessMs > 0 -> s.connectionHealthy(((now - st.lastSuccessMs) / 1000).coerceAtLeast(0))
                            else -> s.connectionWaitingFirst
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (st != null && st.lastSuccessMs > 0) {
                        Text(s.lastSuccessAgo(((now - st.lastSuccessMs) / 1000).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(s.eventsObservedCount(st?.eventsSeen ?: 0), style = MaterialTheme.typography.bodySmall)
                    Text(s.firstReportsDetail(st?.firstReports ?: 0), style = MaterialTheme.typography.bodySmall)
                    if (st?.lastError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.errorDetails}\n${st.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSource = null }) {
                    Text(s.close)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedSource = null
                    onRefresh()
                }) {
                    Text(s.refreshNow)
                }
            }
        )
    }

    Card(Modifier.fillMaxWidth().clickable(onClick = onRefresh)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (serviceRunning) s.monitoringActive else s.monitoringStopped,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    val statusLabel = locationStatus?.let { s.locationStatusLabel(it) }
                    append(s.referencePrefix)
                    append(String.format(Locale.getDefault(), "%.3f", settings.homeLat))
                    append(", ")
                    append(String.format(Locale.getDefault(), "%.3f", settings.homeLon))
                    append(
                        when {
                            !settings.useDeviceLocation -> " (${s.fixed})"
                            settings.homeFromDevice && statusLabel == null -> " (${s.deviceLocation})"
                            settings.homeFromDevice -> " (${s.deviceLocation} · $statusLabel)"
                            else -> " (${s.referenceIstanbul} — ${statusLabel ?: s.waitingLocation})"
                        }
                    )
                    append("  ·  ${settings.radiusKm.roundToInt()} ${s.kmRadius}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Source.entries.forEach { src ->
                    SourceChip(
                        source = src,
                        st = status[src],
                        online = online,
                        now = now,
                        onClick = { selectedSource = src },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                s.tapToRefresh,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceChip(
    source: Source,
    st: SourceStatus?,
    online: Boolean,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val healthy = st != null && st.lastError == null &&
        (st.liveSocket || (st.lastSuccessMs > 0 && now - st.lastSuccessMs < 120_000))
    val color = when {
        healthy -> Color(0xFF2E7D32)
        !online -> Color(0xFF9E9E9E)
        st?.lastError != null -> Color(0xFFC62828)
        else -> Color(0xFF9E9E9E)
    }
    val detail = when {
        st == null -> s.waiting
        st.liveSocket -> s.live
        !online -> s.noNetwork
        st.lastError.isNetworkError() -> s.noNetwork
        st.lastError != null -> s.error
        st.lastSuccessMs == 0L -> s.waiting
        else -> s.secondsAgo(((now - st.lastSuccessMs) / 1000).coerceAtLeast(0))
    }
    Column(
        modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(source.short, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if ((st?.firstReports ?: 0) > 0) {
            Text(
                s.firstReportsCount(st!!.firstReports),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuakeCard(q: Quake, settings: Settings, placesReady: Boolean, now: Long) {
    val s = LocalStrings.current
    val dist = Geo.distanceKm(settings.homeLat, settings.homeLon, q.lat, q.lon)
    val mmi = Intensity.estimateMmi(q.mag, dist, q.depthKm)
    val dirDeg = Geo.bearingDegrees(settings.homeLat, settings.homeLon, q.lat, q.lon)
    val dirText = s.compassBearing(dirDeg)
    val place = remember(q.id, placesReady) {
        if (Places.loaded) Places.describe(q.lat, q.lon) else null
    }
    var expanded by remember(q.id) { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(MagColors.of(q.mag), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Fmt.mag(q.mag),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    place?.localizedHeadline(s) ?: q.region.ifBlank { s.unknownRegion },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    buildString {
                        place?.nearest?.province?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(s.distanceAndDepth(dist.roundToInt(), q.depthKm.roundToInt()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Fmt.clock(q.originTimeMs)} · ${s.timeAgo(((now - q.originTimeMs) / 1000).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Source.entries.forEach { src -> ConfirmDot(src, q.reports.containsKey(src)) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        s.intensityLabel(mmi),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    s.sourceSolutions,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                q.reports.values.sortedBy { it.source.priority }.forEach { r ->
                    Text(
                        "${r.source.short.padEnd(6)} M${Fmt.mag(r.mag)} ${r.magType}  " +
                            "${String.format(Locale.getDefault(), "%.3f", r.lat)},${String.format(Locale.getDefault(), "%.3f", r.lon)}  " +
                            "${Fmt.km(r.depthKm)}  " +
                            if (r.live) "+${Fmt.latency(r.originTimeMs, r.receivedAtMs)}" else "(${s.backfill})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (q.magSpread >= 0.3) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.magnitudeDiscrepancy(Fmt.mag(q.magSpread)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                place?.city?.let { city ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.nearestCity(city.label, place.cityKm.roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    s.distanceToYou(dist.roundToInt(), dirText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.sourceRegionName(q.region.ifBlank { "-" }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val live = q.fastestLive
                Text(
                    if (live != null) {
                        s.firstReportedBy(live.source.label, (live.receivedAtMs - q.originTimeMs) / 60000)
                    } else {
                        s.beforeMonitoringLatencyNotMeasured
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.intensityEstimate(Intensity.roman(mmi)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDot(source: Source, present: Boolean) {
    val c = if (present) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(c, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(
            source.short,
            style = MaterialTheme.typography.labelSmall,
            color = if (present) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private fun String?.isNetworkError(): Boolean {
    if (this == null) return false
    val e = this
    return e.contains("resolve host", true) || e.contains("Unable to resolve", true) ||
        e.contains("failed to connect", true) || e.contains("Network is unreachable", true) ||
        e.contains("No address associated", true) || e.contains("timeout", true)
}

@Composable
private fun OfflineWarning(systemSaysOnline: Boolean, onRetry: () -> Unit) {
    val s = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onRetry),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (systemSaysOnline) s.serversUnreachable else s.noInternet,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (systemSaysOnline) s.dnsWarning else s.noInternetWarning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BatteryWarning(onFix: () -> Unit) {
    val s = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onFix),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(s.batteryWarningTitle, fontWeight = FontWeight.Bold)
            Text(
                s.batteryWarningBody,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyState(serviceRunning: Boolean) {
    val s = LocalStrings.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (serviceRunning) s.noQuakesYet else s.monitoringStopped,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (serviceRunning) s.emptyRunningSubtitle else s.emptyStoppedSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
