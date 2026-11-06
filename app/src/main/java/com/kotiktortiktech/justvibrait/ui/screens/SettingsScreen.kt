package com.kotiktortiktech.justvibrait.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotiktortiktech.justvibrait.BuildConfig
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.data.AppSettings
import com.kotiktortiktech.justvibrait.data.ThemeMode
import com.kotiktortiktech.justvibrait.data.VibeProfile
import com.kotiktortiktech.justvibrait.ui.components.SectionTitle
import com.kotiktortiktech.justvibrait.ui.descriptionRes
import com.kotiktortiktech.justvibrait.ui.labelRes
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onIntensityChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSyncDelayChange: (Int) -> Unit,
    onProfileChange: (VibeProfile) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTelemetryEnabledChange: (Boolean) -> Unit,
    onTelemetryHostChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHero(settings = settings)
        VibrationCard(
            settings = settings,
            onIntensityChange = onIntensityChange,
            onSensitivityChange = onSensitivityChange,
            onSyncDelayChange = onSyncDelayChange
        )
        ProfileCard(
            selected = settings.profile,
            onSelect = onProfileChange
        )
        AppearanceCard(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            onThemeChange = onThemeChange,
            onDynamicColorChange = onDynamicColorChange
        )
        DebugCard(
            settings = settings,
            onTelemetryEnabledChange = onTelemetryEnabledChange,
            onTelemetryHostChange = onTelemetryHostChange
        )
        AboutCard()
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reset_settings))
        }
    }
}

@Composable
private fun SettingsHero(settings: AppSettings) {
    val profileLabel = stringResource(settings.profile.labelRes())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.profile_current, profileLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VibrationCard(
    settings: AppSettings,
    onIntensityChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSyncDelayChange: (Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(
                title = stringResource(R.string.vibration_section),
                subtitle = stringResource(R.string.settings_subtitle)
            )
            Spacer(Modifier.height(18.dp))
            SettingSlider(
                label = stringResource(R.string.intensity_label),
                description = stringResource(R.string.intensity_desc),
                valueText = "${(settings.intensity * 100f).roundToInt()}%",
                value = settings.intensity,
                onValueChange = onIntensityChange
            )
            Spacer(Modifier.height(18.dp))
            SettingSlider(
                label = stringResource(R.string.sensitivity_label),
                description = stringResource(R.string.sensitivity_desc),
                valueText = "${(settings.sensitivity * 100f).roundToInt()}%",
                value = settings.sensitivity,
                onValueChange = onSensitivityChange
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = stringResource(
                        R.string.threshold_value,
                        (settings.triggerThreshold * 100f).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            SettingSlider(
                label = stringResource(R.string.sync_delay_label),
                description = stringResource(R.string.sync_delay_desc),
                valueText = stringResource(R.string.millis_value, settings.syncDelayMs),
                value = settings.syncDelayMs.toFloat(),
                valueRange = 0f..250f,
                steps = 49,
                onValueChange = { onSyncDelayChange(it.roundToInt()) }
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    description: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileCard(
    selected: VibeProfile,
    onSelect: (VibeProfile) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(title = stringResource(R.string.profile_section))
            Spacer(Modifier.height(12.dp))
            VibeProfile.entries.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    selected = profile == selected,
                    onClick = { onSelect(profile) }
                )
                if (profile != VibeProfile.entries.last()) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: VibeProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(profile.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(profile.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.78f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(title = stringResource(R.string.appearance_section))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.theme_label),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            val modes = ThemeMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = themeMode == item,
                        onClick = { onThemeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size
                        ),
                        label = {
                            Text(
                                text = stringResource(item.labelRes()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dynamic_color),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            stringResource(R.string.dynamic_color_desc)
                        } else {
                            stringResource(R.string.dynamic_color_unsupported)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = onDynamicColorChange,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
            }
        }
    }
}

@Composable
private fun DebugCard(
    settings: AppSettings,
    onTelemetryEnabledChange: (Boolean) -> Unit,
    onTelemetryHostChange: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(
                title = stringResource(R.string.debug_section),
                subtitle = stringResource(R.string.telemetry_desc)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.telemetry_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "UDP 45454",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.telemetryEnabled,
                    onCheckedChange = onTelemetryEnabledChange
                )
            }
            if (settings.telemetryEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.telemetryHost,
                    onValueChange = onTelemetryHostChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.telemetry_host_label)) },
                    placeholder = { Text(stringResource(R.string.telemetry_host_hint)) },
                    supportingText = {
                        Text(stringResource(R.string.telemetry_host_desc))
                    },
                    singleLine = true
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.telemetry_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutCard() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wave),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AboutRow(
                label = stringResource(R.string.about_developer),
                value = stringResource(R.string.developer_name)
            )
            Spacer(Modifier.height(8.dp))
            AboutRow(
                label = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.about_stack),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_pixel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Unspecified
        )
    }
}
