package com.kotiktortiktech.justvibrait.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotiktortiktech.justvibrait.MainViewModel
import com.kotiktortiktech.justvibrait.R
import com.kotiktortiktech.justvibrait.service.CaptureMode
import com.kotiktortiktech.justvibrait.service.LiveSession
import com.kotiktortiktech.justvibrait.ui.screens.LiveScreen
import com.kotiktortiktech.justvibrait.ui.screens.LocalScreen
import com.kotiktortiktech.justvibrait.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private const val TAB_LOCAL = 0
private const val TAB_LIVE = 1
private const val TAB_SETTINGS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustVibraitApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val localState by viewModel.localState.collectAsStateWithLifecycle()
    val localFeatures by viewModel.localFeatures.collectAsStateWithLifecycle()
    val liveState by LiveSession.state.collectAsStateWithLifecycle()
    val liveFeatures by LiveSession.features.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_LOCAL) }
    var liveModeName by rememberSaveable { mutableStateOf(CaptureMode.PLAYBACK.name) }
    val liveMode = runCatching { CaptureMode.valueOf(liveModeName) }
        .getOrDefault(CaptureMode.PLAYBACK)

    val openAppSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(context.getString(R.string.error_permission))
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.openMedia(uri)
        }
    }

    var pendingMode by remember { mutableStateOf(CaptureMode.PLAYBACK) }
    val projectionManager = remember(context) {
        context.getSystemService(MediaProjectionManager::class.java)
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            LiveSession.startPlayback(context, result.resultCode, data)
        } else {
            showMessage(context.getString(R.string.projection_denied))
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!audioGranted) {
            showMessage(context.getString(R.string.permission_audio_denied))
            return@rememberLauncherForActivityResult
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            result[Manifest.permission.POST_NOTIFICATIONS] == false
        ) {
            showMessage(context.getString(R.string.notification_denied))
        }

        if (pendingMode == CaptureMode.PLAYBACK) {
            val intent = projectionManager?.createScreenCaptureIntent()
            if (intent != null) {
                projectionLauncher.launch(intent)
            } else {
                showMessage(context.getString(R.string.error_source_unsupported))
            }
        } else {
            LiveSession.startMic(context)
        }
    }

    val startLive: (CaptureMode) -> Unit = { mode ->
        if (LiveSession.state.value.isRunning) {
            LiveSession.stop(context)
        } else {
            pendingMode = mode
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(localState.error) {
        val error = localState.error ?: return@LaunchedEffect
        showMessage(context.getString(error.messageRes()))
        viewModel.clearLocalError()
    }

    LaunchedEffect(liveState.error) {
        val error = liveState.error ?: return@LaunchedEffect
        showMessage(context.getString(error.messageRes()))
        LiveSession.clearError()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = selectedTab == TAB_LOCAL,
                    onClick = { selectedTab = TAB_LOCAL },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_music),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_local), maxLines = 1) }
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_LIVE,
                    onClick = { selectedTab = TAB_LIVE },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_live), maxLines = 1) }
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_SETTINGS,
                    onClick = { selectedTab = TAB_SETTINGS },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_settings), maxLines = 1) }
                )
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                TAB_LOCAL -> ExtendedFloatingActionButton(
                    onClick = { filePicker.launch(arrayOf("audio/*", "video/*")) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                if (localState.uri == null) R.string.choose_media
                                else R.string.choose_file
                            )
                        )
                    }
                )

                TAB_LIVE -> {
                    val isRunning = liveState.isRunning
                    ExtendedFloatingActionButton(
                        onClick = { startLive(liveMode) },
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isRunning) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (isRunning) R.drawable.ic_stop else R.drawable.ic_mic
                                ),
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(
                                stringResource(
                                    if (isRunning) R.string.live_stop else R.string.live_start
                                )
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                TAB_LOCAL -> LocalScreen(
                    state = localState,
                    features = localFeatures,
                    profile = settings.profile,
                    onPickFile = { filePicker.launch(arrayOf("audio/*", "video/*")) },
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeek = viewModel::seekTo,
                    onAttachSurface = viewModel::attachVideoSurface,
                    onDetachSurface = viewModel::detachVideoSurface,
                    modifier = Modifier.fillMaxSize()
                )

                TAB_LIVE -> LiveScreen(
                    state = liveState,
                    features = liveFeatures,
                    selectedMode = liveMode,
                    onModeChange = { liveModeName = it.name },
                    onToggle = startLive,
                    onOpenSettings = openAppSettings,
                    modifier = Modifier.fillMaxSize()
                )

                TAB_SETTINGS -> SettingsScreen(
                    settings = settings,
                    onIntensityChange = viewModel::setIntensity,
                    onSensitivityChange = viewModel::setSensitivity,
                    onSyncDelayChange = viewModel::setSyncDelay,
                    onProfileChange = viewModel::setProfile,
                    onThemeChange = viewModel::setThemeMode,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    onTelemetryEnabledChange = viewModel::setTelemetryEnabled,
                    onTelemetryHostChange = viewModel::setTelemetryHost,
                    onReset = {
                        viewModel.resetSettings()
                        showMessage(context.getString(R.string.reset_done))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
