# Simple Runner

A small Android running app: distance, moving time, pace, and the GPS route on a map.
No account, no cloud, no analytics. Runs are stored on the phone and nowhere else.

**Install:** [Google Play](https://play.google.com/store/apps/details?id=com.wichosu.simplerunner)
&nbsp;·&nbsp; [Website](https://wichosu.github.io/simple-runner/) &nbsp;·&nbsp; [Releases](https://github.com/wichosu/simple-runner/releases)

## What it does

- Records distance, moving time, and pace (km and min/km).
- Draws the route on a map while you run and in the run's details afterwards.
- Pause and resume; paused time is excluded from pace, and the map breaks the line
  where you paused instead of drawing a straight jump.
- Keeps recording in the background with a foreground-service notification.
- Recovers an unfinished run if the process is killed mid-run.
- Works offline. Only the map needs a network — the streets are fetched from
  [OpenFreeMap](https://openfreemap.org/) (© OpenStreetMap contributors).

Deliberately **not** in the app: accounts, sync, leaderboards, splits, audio cues,
social sharing, ads, tracking. See the roadmap note below.

## Privacy

Runs never leave the device: the database is a local Room file, and there is no
server component. Location is read only while a run is recording or paused and the
foreground service is showing its notification. The one network request the app makes
is for map tiles, which sends the viewed area and your IP address to OpenFreeMap.
The full policy is at [docs/privacy.html](docs/privacy.html).

## Install

- **Google Play:** <https://play.google.com/store/apps/details?id=com.wichosu.simplerunner>
- **APK:** download the latest universal APK from
  [Releases](https://github.com/wichosu/simple-runner/releases/latest). It is the same
  artifact Google Play signs and ships, so it installs over a Play installation and
  updates from Play afterwards.
- **From source:** see below. Requires JDK 17 and Android SDK 36.

## Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:bundleRelease          # AAB for Play
```

The pinned toolchain is AGP 8.13.2, Gradle 8.14.3, Kotlin 2.4.21, compileSdk 36.
Do not bump the AndroidX artifacts to versions that require AGP 9: this project is
built with Android Studio 2025.2, which cannot sync AGP 9.

### Signing

Release builds read their upload key from `keystore.properties` in the repo root
(gitignored):

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=…
keyAlias=simplerunner-upload
keyPassword=…
```

Without that file the release build still completes, unsigned, so a fresh clone can
always build the app. The upload key is not the app-signing key: with Play App Signing,
Google holds the key that signs what users install, and the upload key only
authenticates your uploads. Website APKs are downloaded from Play Console, not signed
locally, so they match the Play certificate.

## Project layout

```
app/src/main/java/com/wichosu/simplerunner/
├── data/          Room entities, DAOs, repository, AppContainer (manual DI)
├── domain/        tracker state machine, distance/pace math, GPS filtering
├── tracking/      foreground service, RunSession, notifications
├── ui/            Compose screens (home, run, detail), theme, map
└── MainActivity.kt

app/src/test/      JVM tests for the tracker, filter, formatters, repository math
docs/              the static website (GitHub Pages, served from /docs)
store/             Play listing copy, data-safety answers, release checklist
```

All run mutations go through `RunSession` and `RunTrackingService`; screens observe
`RunSession.state` and send intents. Track points are written as they arrive on a
single-thread dispatcher, so a run row always precedes its points.

## Roadmap

Kept out of 1.0 on purpose: auto-pause, splits, audio cues, GPX export, elevation gain,
localization, and any form of sync. F-Droid's main repository would also require moving
off Play Services location to a free provider.

## Third-party

- [MapLibre Native](https://github.com/maplibre/maplibre-native) (BSD-2-Clause) for the map.
- [OpenFreeMap](https://openfreemap.org/) for tiles and style, data © OpenStreetMap
  contributors, [ODbL](https://www.openstreetmap.org/copyright).
- AndroidX, Jetpack Compose, Room, and Play Services Location under Apache-2.0.

## License

Apache-2.0. See [LICENSE](LICENSE). Copyright 2026 Wichosu.
