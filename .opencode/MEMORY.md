# Simple Runner — project memory

## Toolchain
- **Pinned Android toolchain (2026-10-25):** AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.4.21 / compileSdk 36. Do not bump AndroidX to 2026 artifacts — they require AGP 9.1+/SDK 37, and the installed Android Studio 2025.2 (AI-252) cannot sync AGP 9.x.

## Maps
- **Do not adopt osmdroid (2026-10-25):** the repo was archived Nov 2024, last release 6.1.20; raster providers that license apps (MapTiler, Stadia, Thunderforest) are non-commercial and key-bound. Phase 5 uses MapLibre 13.x + OpenFreeMap (`https://tiles.openfreemap.org/styles/positron`), no key. Public instance asks for a unique User-Agent on mobile.
- **The app had no INTERNET permission until phase 5:** recording must stay fully offline; the map is the only networked surface, and offline disclosure copy must say the map area is requested from OpenFreeMap.

## Distribution
- **Website APK = Play-signed universal APK (2026-10-25):** download from Play Console → Test & release → Latest releases and bundles → bundle → Downloads. Same cert as Play installs, so no self-managed app signing key and no uninstall to switch channels.
- **Play App Signing auto-registers apps for Android developer verification** (enforced from Sept 30 2026 in pilot regions, global 2027, sideloads included), so a Play-signed site APK installs without the unverified-developer flow. A self-signed site APK would need separate registration.

## Publishing
- **Public repo is `git@github.com:Wichosu/simple-runner`, site served from `/docs` via Pages (2026-11-11):** commits must keep the `54341712+Wichosu@users.noreply.github.com` author — the original commit was amended to keep a personal address out of public history, and repo-local `user.email` is already set to it. Don't `--reset-author` from a global gmail config, and don't hand-write the personal address into docs, the store listing's contact field, or the About panel unless asked.

## Emulator testing
- **UI automation needs a woken screen (2026-10-25):** the emulator sleeps and taps silently do nothing. Before any tap run `adb shell svc power stayon true`, `input keyevent KEYCODE_WAKEUP`, `wm dismiss-keyguard`, and confirm `mCurrentFocus` is the app.
- **Pull the DB with tar, not per-file copies (2026-10-25):** use `adb exec-out run-as com.wichosu.simplerunner tar -cf - databases`; copying files individually returns stale data (once showed 0 points while Room saw all inserts). Delete `*.db-shm` before opening with host `sqlite3`.
