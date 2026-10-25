package com.wichosu.simplerunner.ui.run

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wichosu.simplerunner.R
import com.wichosu.simplerunner.SimpleRunnerApplication
import com.wichosu.simplerunner.domain.Formatters
import com.wichosu.simplerunner.domain.RunSnapshot
import com.wichosu.simplerunner.domain.RunState
import com.wichosu.simplerunner.tracking.RunTrackingService
import com.wichosu.simplerunner.ui.map.RouteMap
import com.wichosu.simplerunner.ui.map.RouteMapMode
import com.wichosu.simplerunner.ui.map.RoutePoint
import com.wichosu.simplerunner.ui.map.toRoutePoint
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The live run screen. It renders the application-scoped session and sends intents to
 * the tracking service; it never touches the tracker itself, so leaving the screen or
 * rotating cannot disturb a run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRunScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as SimpleRunnerApplication
    val session = application.container.runSession
    val snapshot by session.state.collectAsStateWithLifecycle()
    val currentRunId by session.currentRunId.collectAsStateWithLifecycle()
    // The map draws from the database, so it needs its own subscription: the run id
    // arrives when the run starts (or is adopted) and the point list grows during it.
    val routePoints by remember(currentRunId) {
        val id = currentRunId
        if (id == null) {
            flowOf(emptyList())
        } else {
            application.container.runRepository.points(id)
                .map { points -> points.map { it.toRoutePoint() } }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var hasLocation by remember { mutableStateOf(hasFineLocation(context)) }
    var locationEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var entryHandled by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requested = true
        hasLocation = hasFineLocation(context)
        locationEnabled = isLocationEnabled(context)
    }

    // The user may grant permission or turn location on in system settings and come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocation = hasFineLocation(context)
                locationEnabled = isLocationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Toggling location from quick settings does not resume the activity, so listen for
    // the switch directly and update the banner and the GPS hint immediately.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    locationEnabled = isLocationEnabled(context)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Entering this screen means "start or resume the run". The service decides which,
    // based on whether an unfinished row exists, and ignores this if already running.
    LaunchedEffect(hasLocation) {
        if (hasLocation && !entryHandled) {
            entryHandled = true
            RunTrackingService.start(context)
        }
    }

    // A run can end without this screen asking for it: finished from the notification,
    // discarded on Home, or still finishing when the screen was entered. Either way the
    // screen has nothing left to offer, so leave as soon as the run is over.
    var sawActiveRun by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(snapshot.state) {
        when (snapshot.state) {
            RunState.IDLE -> if (sawActiveRun) onFinished()
            RunState.FINISHED -> onFinished()
            else -> sawActiveRun = true
        }
    }

    // A run screen that goes dark mid-run is useless.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.active_run_title)) })
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (!hasLocation) {
            PermissionGate(
                denied = requested,
                onRequest = { permissionLauncher.launch(locationPermissions()) },
                onOpenSettings = { context.openAppSettings() },
                modifier = modifier,
            )
        } else {
            RunContent(
                snapshot = snapshot,
                routePoints = routePoints,
                locationEnabled = locationEnabled,
                onPause = { RunTrackingService.pause(context) },
                onResume = { RunTrackingService.resume(context) },
                onFinish = {
                    // Finish() drives navigation through the state change, so the screen
                    // never leaves a run that is still being persisted.
                    RunTrackingService.finish(context)
                },
                onOpenLocationSettings = { context.openLocationSettings() },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RunContent(
    snapshot: RunSnapshot,
    routePoints: List<RoutePoint>,
    locationEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = snapshot.state == RunState.RECORDING
    val isPaused = snapshot.state == RunState.PAUSED
    val pace = Formatters.pace(
        Formatters.paceSecondsPerKm(snapshot.movingMillis, snapshot.distanceM),
    )
    // One accepted fix is the point where GPS is actually working. Until then 0.00 km
    // looks broken, so say what is happening.
    val waitingForGps = isRecording && locationEnabled && snapshot.lastFixAt == null

    Column(
        modifier = modifier.padding(24.dp),
    ) {
        if (!locationEnabled) {
            LocationOffBanner(onOpenSettings = onOpenLocationSettings)
            Spacer(Modifier.height(16.dp))
        }

        if (isPaused) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.status_paused),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.label_distance),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Formatters.distanceKm(snapshot.distanceM),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.unit_km),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (waitingForGps) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gps_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatBlock(
                label = stringResource(R.string.label_time),
                value = Formatters.duration(snapshot.movingMillis),
                unit = null,
            )
            StatBlock(
                label = stringResource(R.string.label_pace),
                value = pace,
                unit = stringResource(R.string.unit_pace),
            )
        }

        Spacer(Modifier.height(24.dp))

        // The map takes the middle of the screen. It only appears once there is a point
        // to draw, so the screen does not flash a world view before the first fix. If the
        // available space gets too small the map steps aside rather than squeezing the
        // stats and the buttons, which are the things you need mid-run.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (routePoints.isNotEmpty() && maxHeight >= 140.dp) {
                RouteMap(
                    points = routePoints,
                    mode = RouteMapMode.Follow,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (isRecording) onPause() else onResume() },
                enabled = isRecording || isPaused,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(
                    stringResource(
                        if (isRecording) R.string.action_pause else R.string.action_resume,
                    ),
                )
            }
            Button(
                onClick = onFinish,
                enabled = isRecording || isPaused,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.active_run_finish))
            }
        }
    }
}

@Composable
private fun LocationOffBanner(
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.location_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.location_off_action))
            }
        }
    }
}

@Composable
private fun PermissionGate(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (denied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.permission_grant))
        }

        if (denied) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    unit: String?,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

/** A granted permission is not enough: the system location switch can still be off. */
private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}

private fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openLocationSettings() {
    startActivity(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
