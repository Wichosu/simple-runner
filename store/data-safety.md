# Data safety form

Play Console → **App content → Data safety**. Play compares these answers against the
APK's manifest and permissions, so every line here is tied to something in the build.

## The answers

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not shown (nothing is collected) |
| Do you provide a way for users to request that their data is deleted? | Not shown (nothing is collected) |
| Does your app allow users to create accounts? | **No** |
| Ads | **No ads** |
| App access | All functionality available without special access |

Selecting **No** to collection collapses the rest of the questionnaire — no data types to
declare, no purpose to justify. That is the whole form for this app.

## Why "No" is accurate

Google defines collection as transmitting data off the device. Nothing the app records
is ever transmitted:

- **Location** — `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` are read only while a
  run is recording or paused. Every fix is filtered and written to a local Room database
  (`app/src/main/java/com/wichosu/simplerunner/data/`) on the device. No location field
  exists in any request the app makes.
- **No background location** — `AndroidManifest.xml` declares no
  `ACCESS_BACKGROUND_LOCATION`, and the foreground service is typed `location`, so the
  system grants access only while the notification is up.
- **No identifiers** — no advertising ID, no installation ID, no account. The app has no
  login of any kind.
- **No analytics or crash reporting** — the dependency list contains no telemetry SDK.
  `MapLibre` renders locally; it is a renderer, not a data collector.
- **Backup off** — `android:allowBackup="false"`, so the OS does not upload the database
  to Google's servers either.

## The one network request, and why it is not "collection"

`INTERNET` exists for map tiles: MapLibre fetches them from
`https://tiles.openfreemap.org/`. That request carries the tile coordinates for the area
being viewed, a User-Agent, and the device's IP address — the same information any image
request carries.

This is content delivery, not collection of user data by the app or by the developer:
the developer never receives, stores or has access to those requests, and no run data is
attached to them. The privacy note discloses it in plain language
(`docs/privacy.html`, "The map, and the one network request"), including that OpenFreeMap
sees the IP address and can infer the approximate map area.

**If a reviewer questions it,** the fallback declaration is: *Location → approximate and
precise → App functionality → not shared → not used for tracking*. That is a more
conservative reading, and it would still be consistent with the privacy note. It should
not be necessary: no location value is transmitted, only a map tile URL.

## Content rating questionnaire

Play Console → **App content → Content rating**. Answers that apply:

- Category: **Utility / Productivity / Communication** (not a game).
- Violence, sexuality, language, controlled substances: **No** to all.
- Gambling, simulated gambling: **No**.
- User-generated content or social features: **No** — there is no sharing, feed or
  messaging of any kind.
- Shares the user's current location with other users: **No**.
- Allows users to share personal information: **No**.
- Digital purchases: **No**.
- Ads: **No**.

Expected result: rated for everyone (PEGI 3 / ESRB E), no interactive elements beyond
"shares location" being absent.

## Other App content declarations

| Declaration | Answer |
| --- | --- |
| Privacy policy URL | `https://wichosu.github.io/simple-runner/privacy.html` |
| Ads | No ads |
| App access | No restrictions — nothing requires a login |
| News app | No |
| COVID-19 contact tracing | No |
| Data deletion | Not applicable (no accounts); uninstalling clears all data |
| Target audience | 13+ / general; not directed at children |
| Government app | No |
| Financial features | No |
| Health features | No — it records activity metrics, not health data, and integrates with nothing |
| Foreground service | See `store/foreground-service.md` |
