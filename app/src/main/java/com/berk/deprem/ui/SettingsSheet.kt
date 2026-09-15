package com.berk.deprem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.berk.deprem.core.Settings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onTestNotification: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Section(s.languageSection)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = settings.language == "tr",
                    onClick = { onChange { it.copy(language = "tr") } },
                    label = { Text("Türkçe 🇹🇷", modifier = Modifier.padding(vertical = 4.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = settings.language == "en",
                    onClick = { onChange { it.copy(language = "en") } },
                    label = { Text("English 🇬🇧", modifier = Modifier.padding(vertical = 4.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))
            Section(s.scopeSection)
            SliderRow(
                label = s.monitoringRadius,
                value = settings.radiusKm.toFloat(),
                range = 25f..800f,
                steps = 30,
                display = "${settings.radiusKm.roundToInt()} km",
            ) { v -> onChange { it.copy(radiusKm = v.toDouble()) } }

            SliderRow(
                label = s.notificationThreshold,
                value = settings.minMag.toFloat(),
                range = 1.0f..6.0f,
                steps = 49,
                display = "M${Fmt.mag(settings.minMag)}",
            ) { v -> onChange { it.copy(minMag = v.toDouble()) } }
            Hint(s.notificationThresholdHint)

            SliderRow(
                label = s.listingThreshold,
                value = settings.listMinMag.toFloat(),
                range = 0f..5.0f,
                steps = 49,
                display = if (settings.listMinMag < 0.05) s.allThreshold else "M${Fmt.mag(settings.listMinMag)}",
            ) { v -> onChange { it.copy(listMinMag = v.toDouble()) } }
            Hint(s.listingThresholdHint)

            Spacer(Modifier.height(12.dp))
            Section(s.emergencyAlarmSection)
            Text(
                s.emergencyAlarmHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = s.alarmMagnitude,
                value = settings.alarmMag.toFloat(),
                range = 2.5f..7.0f,
                steps = 44,
                display = "M${Fmt.mag(settings.alarmMag)}",
            ) { v -> onChange { it.copy(alarmMag = v.toDouble()) } }

            SliderRow(
                label = s.alarmRadius,
                value = settings.alarmRadiusKm.toFloat(),
                range = 20f..500f,
                steps = 24,
                display = "${settings.alarmRadiusKm.roundToInt()} km",
            ) { v -> onChange { it.copy(alarmRadiusKm = v.toDouble()) } }

            Spacer(Modifier.height(12.dp))
            Section(s.locationSection)
            ToggleRow(
                s.useDeviceLocation,
                s.useDeviceLocationHint,
                settings.useDeviceLocation,
            ) { on ->
                onChange { it.copy(useDeviceLocation = on, homeFromDevice = false) }
            }

            Spacer(Modifier.height(12.dp))
            Section(s.sourcesSection)
            ToggleRow("EMSC", s.emscSourceHint, settings.emscEnabled) { on ->
                onChange { it.copy(emscEnabled = on) }
            }
            ToggleRow("AFAD", s.afadSourceHint, settings.afadEnabled) { on ->
                onChange { it.copy(afadEnabled = on) }
            }
            ToggleRow("Kandilli (KOERI)", s.koeriSourceHint, settings.koeriEnabled) { on ->
                onChange { it.copy(koeriEnabled = on) }
            }
            Text(
                s.sourcesSectionHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Button(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                Text(s.testAlarmButton)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
