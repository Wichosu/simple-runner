# Foreground service declaration (location)

Play Console → **App content → Foreground service permissions**. Required for apps that
target Android 14+ and declare a foreground service type — this app declares
`android:foregroundServiceType="location"` on `RunTrackingService`, and targets API 36.

The form asks for the type, a description of the use, and a **video link** showing the
feature working. Review rejects declarations whose video does not show the permission
prompt, the notification and the actual feature.

## Service type

```
Location
```

## Description to paste (limit is generous; keep it factual)

```
Simple Runner is a GPS run recorder. Distance, moving time and pace can only be measured
while the user is running with the screen off and the app in the background, so recording
must continue outside the foreground app.

The location foreground service starts only when the user taps "Start run" in the app.
While it runs, a persistent notification shows the run in progress with Pause and Finish
actions. The service stops when the user taps Finish or discards the run.

Location is read only while a run is recording or paused. There is no background location
permission in the app, no geofencing, no location history, and no location data is
transmitted off the device: every fix is stored in the app's private on-device database.
Runs are never uploaded, shared or synced.
```

## Video

Play requires a link to a video (unlisted YouTube is fine) that demonstrates the feature.
Record it on a device or emulator, in this order — the whole thing can be under 90
seconds:

1. Install, open the app, tap **Start run**.
2. The system location permission dialog appears — grant **While using the app**.
   *(This is the prompt reviewers look for; do not skip or pre-grant it.)*
3. The notification permission prompt, if it appears — allow.
4. The run screen appears and the notification "Recording" is visible when you pull down
   the shade. Show the notification's **Pause** and **Finish** actions.
5. Press home so the app is in the background, and show the notification still running
   with distance increasing.
6. Return to the app, tap **Finish**, and show the run saved in the history list with its
   route on the map.
7. Optional: show that Settings → Apps → Simple Runner → Permissions lists only location
   and notifications, with no "Allow all the time" option.

Upload as **unlisted** (not private — reviewers must be able to open it) and paste the URL
into the form.

## Consistency check before submitting

The declaration above has to match the build:

- `app/src/main/AndroidManifest.xml` — `foregroundServiceType="location"`, permissions
  `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`, no background location.
- The notification actions exist: Pause and Finish on the ongoing notification.
- The privacy note describes the same behaviour (`docs/privacy.html`, "Location").
