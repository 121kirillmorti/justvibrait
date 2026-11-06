package com.kotiktortiktech.justvibrait.ui.screens

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.audio.AudioFeatures
import com.kotiktortiktech.justvibrait.data.VibeProfile
import com.kotiktortiktech.justvibrait.media.LocalPlaybackState
import com.kotiktortiktech.justvibrait.ui.components.BpmBadge
import com.kotiktortiktech.justvibrait.ui.components.LevelBar
import com.kotiktortiktech.justvibrait.ui.components.SectionTitle
import com.kotiktortiktech.justvibrait.ui.components.VibeVisualizer
import com.kotiktortiktech.justvibrait.ui.components.formatDuration
import com.kotiktortiktech.justvibrait.ui.labelRes

@Composable
fun LocalScreen(
    state: LocalPlaybackState,
    features: AudioFeatures,
    profile: VibeProfile,
    onPickFile: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onAttachSurface: (SurfaceView) -> Unit,
    onDetachSurface: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profileLabel = stringResource(profile.labelRes())
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LocalHero(
            state = state,
            features = features,
            profileLabel = profileLabel
        )
        MediaCard(
            state = state,
            features = features,
            profileLabel = profileLabel,
            onPickFile = onPickFile,
            onTogglePlayback = onTogglePlayback,
            onSeek = onSeek,
            onAttachSurface = onAttachSurface,
            onDetachSurface = onDetachSurface
        )
        AnalysisCard(features = features, profileLabel = profileLabel)
        TipCard()
    }
}

@Composable
private fun LocalHero(
    state: LocalPlaybackState,
    features: AudioFeatures,
    profileLabel: String
) {
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(46.dp)
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
                        text = stringResource(
                            if (state.isPlaying) R.string.status_playing
                            else R.string.status_ready
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (state.isPlaying) R.string.status_vibration_on
                            else R.string.status_vibration_off
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BpmBadge(features.bpm)
            }

            Spacer(Modifier.height(18.dp))
            VibeVisualizer(
                features = features,
                active = state.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(
                    icon = R.drawable.ic_tune,
                    text = profileLabel
                )
                HeroPill(
                    icon = R.drawable.ic_wave,
                    text = stringResource(
                        if (state.isPlaying) R.string.status_vibration_on
                        else R.string.status_vibration_off
                    )
                )
            }
        }
    }
}

@Composable
private fun HeroPill(icon: Int, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MediaCard(
    state: LocalPlaybackState,
    features: AudioFeatures,
    profileLabel: String,
    onPickFile: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onAttachSurface: (SurfaceView) -> Unit,
    onDetachSurface: () -> Unit
) {
    ElevatedCard(shape = MaterialTheme.shapes.large) {
        if (state.uri == null) {
            EmptyMediaState(onPickFile = onPickFile)
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.hasVideo) {
                    VideoPreview(
                        onAttachSurface = onAttachSurface,
                        onDetachSurface = onDetachSurface
                    )
                } else {
                    AlbumArt(features = features, active = state.isPlaying)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.title.ifBlank { stringResource(R.string.local_title) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))
                MediaSlider(
                    state = state,
                    onSeek = onSeek
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(state.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (state.durationMs > 0L) {
                            formatDuration(state.durationMs)
                        } else {
                            stringResource(R.string.duration_unknown)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.pause else R.string.play
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (state.isPlaying) R.string.status_playing
                                else R.string.status_paused
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.profile_current, profileLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BpmBadge(features.bpm)
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaState(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_file_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.no_file_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = onPickFile) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_file))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.supported_formats),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VideoPreview(
    onAttachSurface: (SurfaceView) -> Unit,
    onDetachSurface: () -> Unit
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view ->
                view.setBackgroundColor(android.graphics.Color.BLACK)
                onAttachSurface(view)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
        onRelease = { onDetachSurface() }
    )
}

@Composable
private fun AlbumArt(features: AudioFeatures, active: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        VibeVisualizer(
            features = features,
            active = active,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(68.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaSlider(
    state: LocalPlaybackState,
    onSeek: (Long) -> Unit
) {
    val duration = state.durationMs
    var sliderValue by remember(state.uri) { mutableStateOf(0f) }
    var dragging by remember(state.uri) { mutableStateOf(false) }

    LaunchedEffect(state.positionMs, duration, dragging) {
        if (!dragging && duration > 0L) {
            sliderValue = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        }
    }

    Slider(
        value = sliderValue,
        onValueChange = {
            dragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            if (duration > 0L) {
                onSeek((sliderValue * duration).toLong())
            }
            dragging = false
        },
        enabled = duration > 0L,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AnalysisCard(features: AudioFeatures, profileLabel: String) {
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
                value = features.bass,
                accent = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            LevelBar(
                label = stringResource(R.string.level_label),
                value = features.level,
                accent = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(
                    label = stringResource(R.string.bpm_label),
                    value = if (features.bpm > 0) features.bpm.toString()
                    else stringResource(R.string.bpm_unknown),
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    label = stringResource(R.string.profile_label),
                    value = profileLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TipCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_wave),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.local_tip_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.local_tip_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
