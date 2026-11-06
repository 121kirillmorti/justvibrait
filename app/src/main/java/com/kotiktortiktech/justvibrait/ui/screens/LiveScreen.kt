package com.kotiktortiktech.justvibrait.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.service.CaptureMode
import com.kotiktortiktech.justvibrait.service.LiveState
import com.kotiktortiktech.justvibrait.ui.components.BpmBadge
import com.kotiktortiktech.justvibrait.ui.components.LevelBar
import com.kotiktortiktech.justvibrait.ui.components.SectionTitle
import com.kotiktortiktech.justvibrait.ui.components.VibeVisualizer
import com.kotiktortiktech.justvibrait.ui.components.formatDuration
import com.kotiktortiktech.justvibrait.ui.messageRes
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(
    state: LiveState,
    features: AudioFeatures,
    selectedMode: CaptureMode,
    onModeChange: (CaptureMode) -> Unit,
    onToggle: (CaptureMode) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.isRunning, state.startedAtElapsedMs) {
        while (state.isRunning) {
            now = SystemClock.elapsedRealtime()
            delay(500L)
        }
    }
    val elapsed = if (state.isRunning && state.startedAtElapsedMs > 0L) {
        (now - state.startedAtElapsedMs).coerceAtLeast(0L)
    } else {
        0L
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiveHero(
            state = state,
            features = features,
            elapsedMs = elapsed,
            selectedMode = selectedMode,
            onToggle = onToggle
        )

        state.error?.let { error ->
            ErrorCard(text = stringResource(error.messageRes()))
        }

        SourceCard(
            selectedMode = selectedMode,
            state = state,
            onModeChange = onModeChange
        )

        LevelsCard(features = features, state = state)

        PermissionCard(onOpenSettings = onOpenSettings)

        LimitationCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveHero(
    state: LiveState,
    features: AudioFeatures,
    elapsedMs: Long,
    selectedMode: CaptureMode,
    onToggle: (CaptureMode) -> Unit
) {
    val statusText = when {
        state.isPreparing -> stringResource(R.string.live_status_preparing)
        state.isRunning && state.mode == CaptureMode.PLAYBACK ->
            stringResource(R.string.live_status_running_system)
        state.isRunning -> stringResource(R.string.live_status_running_mic)
        else -> stringResource(R.string.live_status_idle)
    }
    val running = state.isRunning
    val effectiveMode = if (running || state.isPreparing) state.mode else selectedMode
    val modeLabel = if (effectiveMode == CaptureMode.PLAYBACK) {
        stringResource(R.string.source_system)
    } else {
        stringResource(R.string.source_mic)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.live_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BpmBadge(features.bpm)
            }

            Spacer(Modifier.height(18.dp))
            VibeVisualizer(
                features = features,
                active = running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(122.dp)
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveMetric(
                    label = stringResource(R.string.live_elapsed),
                    value = formatDuration(elapsedMs),
                    modifier = Modifier.weight(1f)
                )
                LiveMetric(
                    label = stringResource(R.string.source_label),
                    value = modeLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onToggle(if (running || state.isPreparing) state.mode else selectedMode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = if (running || state.isPreparing) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (running || state.isPreparing) R.drawable.ic_stop else R.drawable.ic_mic
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (running || state.isPreparing) R.string.live_stop
                        else R.string.live_start
                    )
                )
            }
        }
    }
}

@Composable
private fun LiveMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    selectedMode: CaptureMode,
    state: LiveState,
    onModeChange: (CaptureMode) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(title = stringResource(R.string.source_label))
            Spacer(Modifier.height(12.dp))
            val modes = listOf(CaptureMode.PLAYBACK, CaptureMode.MIC)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = selectedMode == item,
                        onClick = { onModeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        enabled = !state.isRunning && !state.isPreparing,
                        label = {
                            Text(
                                text = stringResource(
                                    if (item == CaptureMode.PLAYBACK) R.string.source_system
                                    else R.string.source_mic
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (selectedMode == CaptureMode.PLAYBACK) R.string.live_system_hint
                    else R.string.live_mic_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LevelsCard(features: AudioFeatures, state: LiveState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(
                title = stringResource(R.string.analysis_title),
                subtitle = stringResource(R.string.analysis_subtitle)
            )
            Spacer(Modifier.height(16.dp))
            LevelBar(
                label = stringResource(R.string.bass_level),
                value = if (state.isRunning) features.bass else 0f,
                accent = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            LevelBar(
                label = stringResource(R.string.level_label),
                value = if (state.isRunning) features.level else 0f,
                accent = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        )
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.live_permission_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.live_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_settings))
                }
            }
        }
    }
}

@Composable
private fun LimitationCard() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        )
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_wave),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.live_limitation_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.live_limitation_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_wave),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
