package com.wichosu.simplerunner.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wichosu.simplerunner.MainActivity
import com.wichosu.simplerunner.R
import com.wichosu.simplerunner.SimpleRunnerApplication
import com.wichosu.simplerunner.data.RunStore
import com.wichosu.simplerunner.domain.Formatters
import com.wichosu.simplerunner.domain.RunSnapshot
import com.wichosu.simplerunner.domain.RunState
import com.wichosu.simplerunner.domain.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service that owns GPS for the duration of a run.
 *
 * It is the only mutator of [RunSession]. Every entry point is an intent: the UI, the
 * notification actions, and the system restarting a sticky service after process death.
 * Running as a location foreground service is what keeps updates flowing with the screen
 * off or the app in the background, which is the normal case while running.
 */
class RunTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var session: RunSession
    private lateinit var store: RunStore
    private lateinit var fused: FusedLocationProviderClient

    private var tracking = false
    private var foregroundStarted = false
    private var lastNotificationKey: String? = null
    private var providersReceiverRegistered = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                session.addPoint(location.toTrackPoint())
            }
        }
    }

    /**
     * The master location switch can silently drop the fused request. Re-register as soon
     * as it comes back, so a run keeps recording without a pause/resume cycle.
     */
    private val locationProvidersReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != LocationManager.PROVIDERS_CHANGED_ACTION) return

            if (!isLocationEnabled()) {
                stopLocationUpdates()
            } else if (session.state.value.state == RunState.RECORDING) {
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val container = (application as SimpleRunnerApplication).container
        session = container.runSession
        store = container.runRepository
        fused = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        ContextCompat.registerReceiver(
            this,
            locationProvidersReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        providersReceiverRegistered = true

        // Keep the notification in step with the run, whoever changed it.
        scope.launch {
            session.state.collect { snapshot ->
                if (snapshot.state != RunState.IDLE) updateNotification(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginRun()
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_FINISH -> {
                finishRun()
                return START_NOT_STICKY
            }
            ACTION_DISCARD -> {
                discardRun(intent.getStringExtra(EXTRA_RUN_ID))
                return START_NOT_STICKY
            }
            // The system restarted a sticky service: recover whatever run is open.
            else -> resumeAfterRestart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask="false": the run keeps going after the task is swiped away.
        if (session.isActive) updateNotification(session.state.value)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        if (providersReceiverRegistered) {
            unregisterReceiver(locationProvidersReceiver)
            providersReceiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ actions

    private fun beginRun() {
        if (!hasLocationPermission()) {
            // The service may have been started with startForegroundService; it must
            // enter the foreground once or the system kills it with an ANR.
            startForegroundCompat(RunSnapshot.Idle)
            stopSelf()
            return
        }

        // A run that finished but never got reset (the service can be destroyed before
        // that coroutine runs) would make this start a no-op and leave the live screen
        // showing a dead run. Start means "start a new run", so clear it.
        if (session.state.value.state == RunState.FINISHED) session.reset()

        startForegroundCompat(session.state.value)

        if (session.isActive) {
            // Already tracking (for example the screen was re-entered). Only restart GPS
            // if the run is actually recording; a paused run must stay paused.
            if (session.state.value.state == RunState.RECORDING) startLocationUpdates()
            updateNotification(session.state.value)
            return
        }

        scope.launch {
            // The closing write of the previous run may still be queued. Reading first
            // would see it as open and adopt a run that is already finished.
            session.flush()

            val openRun = store.activeRun()
            if (openRun == null) {
                session.start()
            } else {
                // An unfinished run from a previous process. The user pressed start,
                // which in this state means "pick it back up".
                session.adopt(openRun, store.maxSeq(openRun.id), resumeNow = true)
            }
            startLocationUpdates()
            updateNotification(session.state.value)
        }
    }

    private fun resumeAfterRestart() {
        if (!hasLocationPermission()) {
            startForegroundCompat(RunSnapshot.Idle)
            stopSelf()
            return
        }

        startForegroundCompat(session.state.value)

        if (session.isActive) {
            if (session.state.value.state == RunState.RECORDING) startLocationUpdates()
            updateNotification(session.state.value)
            return
        }

        scope.launch {
            val openRun = store.activeRun()
            if (openRun == null) {
                stopForegroundCompat()
                stopSelf()
                return@launch
            }

            // lastResumeAt is non-null only if the run was recording when the row was
            // written. A run that was paused stays paused.
            val wasRecording = openRun.lastResumeAt != null
            session.adopt(openRun, store.maxSeq(openRun.id), resumeNow = wasRecording)
            if (wasRecording) startLocationUpdates()
            updateNotification(session.state.value)
        }
    }

    private fun pauseRun() {
        startForegroundCompat(session.state.value)

        if (!session.isActive) {
            stopSelf()
            return
        }

        session.pause()
        stopLocationUpdates()
    }

    private fun resumeRun() {
        startForegroundCompat(session.state.value)

        if (session.state.value.state != RunState.PAUSED) {
            if (!session.isActive) stopSelf()
            return
        }

        session.resume()
        startLocationUpdates()
    }

    private fun finishRun() {
        stopLocationUpdates()

        if (!session.isActive) {
            stopForegroundCompat()
            stopSelf()
            return
        }

        scope.launch {
            session.finish()
            session.flush()
            session.reset()
            stopForegroundCompat()
            stopSelf()
        }
    }

    /**
     * Deletes a stored run. The row lives in the database and the run may also live in
     * memory, so this must go through the service: deleting the row from the UI while a
     * session still owns the run would leave the tracker writing points to a run that no
     * longer exists.
     */
    private fun discardRun(id: String?) {
        if (id == null) {
            stopSelf()
            return
        }

        stopLocationUpdates()
        scope.launch {
            // A queued write for this run would otherwise re-insert it after the delete.
            session.flush()
            if (session.currentRunId.value == id) session.forget()
            store.deleteRun(id)
            stopForegroundCompat()
            stopSelf()
        }
    }

    // ----------------------------------------------------------------- location

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (tracking || !hasLocationPermission()) return

        // Deliver every fix; TrackFilter is the only place that decides what is noise.
        // A min-update-distance here would let the system withhold fixes we still need
        // as the anchor for the next accepted sample.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MILLIS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            tracking = true
        } catch (e: SecurityException) {
            // Permission was revoked while running; stop cleanly, the run stays open.
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (!tracking) return
        fused.removeLocationUpdates(locationCallback)
        tracking = false
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private var lastFixWallClock = 0L

    private fun Location.toTrackPoint(): TrackPoint {
        val now = System.currentTimeMillis()
        // Trust GPS time when it is sane, but never let a stale or zeroed clock make
        // the filter drop good fixes for a non-advancing timestamp.
        val stamped = if (time in 1L..(now + 5_000L)) time else now
        lastFixWallClock = maxOf(stamped, lastFixWallClock + 1)

        return TrackPoint(
            lat = latitude,
            lon = longitude,
            accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
            recordedAt = lastFixWallClock,
            speedMps = if (hasSpeed()) speed else null,
            elevationM = if (hasAltitude()) altitude else null,
        )
    }

    // ------------------------------------------------------------- notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat(snapshot: RunSnapshot) {
        val notification = buildNotification(snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        lastNotificationKey = notificationKey(snapshot)
    }

    private fun stopForegroundCompat() {
        foregroundStarted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun updateNotification(snapshot: RunSnapshot) {
        if (!foregroundStarted) return

        val key = notificationKey(snapshot)
        if (key == lastNotificationKey) return
        lastNotificationKey = key

        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(snapshot))
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked: the foreground service still runs.
        }
    }

    /** Coarse on purpose: rebuilding the notification every tick would be wasteful. */
    private fun notificationKey(snapshot: RunSnapshot): String =
        "${snapshot.state}|${Formatters.distanceKm(snapshot.distanceM)}|${snapshot.movingMillis / 60_000}"

    private fun buildNotification(snapshot: RunSnapshot): Notification {
        val recording = snapshot.state != RunState.PAUSED
        val title = getString(
            if (recording) R.string.notification_title_recording else R.string.notification_title_paused,
        )
        val text = if (snapshot.state == RunState.IDLE) {
            getString(R.string.notification_starting)
        } else {
            getString(
                R.string.notification_text,
                Formatters.distanceKm(snapshot.distanceM),
                Formatters.duration(snapshot.movingMillis),
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(if (recording) R.string.action_pause else R.string.action_resume),
                if (recording) actionIntent(ACTION_PAUSE, REQUEST_PAUSE)
                else actionIntent(ACTION_RESUME, REQUEST_RESUME),
            )
            .addAction(
                0,
                getString(R.string.active_run_finish),
                actionIntent(ACTION_FINISH, REQUEST_FINISH),
            )
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RunTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val CHANNEL_ID = "run_tracking"
        private const val NOTIFICATION_ID = 41
        private const val UPDATE_INTERVAL_MILLIS = 1_000L

        private const val REQUEST_OPEN = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_RESUME = 2
        private const val REQUEST_FINISH = 3

        private const val ACTION_START = "com.wichosu.simplerunner.action.START"
        private const val ACTION_PAUSE = "com.wichosu.simplerunner.action.PAUSE"
        private const val ACTION_RESUME = "com.wichosu.simplerunner.action.RESUME"
        private const val ACTION_FINISH = "com.wichosu.simplerunner.action.FINISH"
        private const val ACTION_DISCARD = "com.wichosu.simplerunner.action.DISCARD"

        private const val EXTRA_RUN_ID = "com.wichosu.simplerunner.extra.RUN_ID"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RunTrackingService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).setAction(ACTION_RESUME),
            )
        }

        fun finish(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).setAction(ACTION_FINISH),
            )
        }

        fun discard(context: Context, runId: String) {
            context.startService(
                Intent(context, RunTrackingService::class.java)
                    .setAction(ACTION_DISCARD)
                    .putExtra(EXTRA_RUN_ID, runId),
            )
        }
    }
}
