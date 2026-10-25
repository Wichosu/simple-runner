# Play Store listing

Everything to paste into Play Console → **Grow → Store presence → Main store listing**.
Field limits are Play's own; the character counts here are checked against them.

> **Two placeholders must be replaced before publishing.** The contact email and the
> GitHub slug are assumed, not confirmed — see *Open items* at the bottom.

---

## App name (30 characters max)

```
Simple Runner
```

13 characters.

## Short description (80 characters max)

```
GPS run recorder: distance, time, pace and your route. Local-only, no account.
```

78 characters. This is the line that shows in search results, so it carries the
three things people search for (GPS, distance, pace) and the one thing that makes the
app different (local-only).

## Full description (4000 characters max)

```
Simple Runner records a run with GPS and keeps it on your phone.

Press start, run, press finish. You get distance, moving time, pace and the route on a
map — and nothing else asked of you. No account, no sign-up, no subscription, no ads.
There is no server belonging to this app, so your runs cannot leave the device.

WHAT IT DOES

Distance — added up between accepted GPS fixes. A fix with worse than 35 m of accuracy
is dropped, a move under one metre is ignored, and a jump that would imply 43 km/h is
treated as GPS noise rather than speed, so standing at a crossing does not add metres.

Moving time — wall-clock time spent recording with pauses excluded. Pause at a light and
the clock and the distance stop together; the gap between the two halves is never
counted as running.

Pace — moving time divided by distance, in minutes per kilometre. It reads --:-- below
100 m, before the number means anything, and whenever the result would fall outside
2:00–30:00 per km.

The map — the route is drawn while you run, following you as you move, and afterwards
the whole run is fitted to the screen in its details. Where you paused, the line breaks
instead of drawing a straight jump across the gap.

Keeps recording in the background — a foreground-service notification shows the run in
progress and offers pause and finish. If Android kills the app mid-run, the next launch
offers to recover the unfinished run instead of losing it.

Works offline — recording, the numbers and your history need no connection at all. Only
the map does.

WHAT IT DOES NOT DO

No account. No cloud backup. No sync between devices. No analytics, no crash reporting,
no advertising, no third-party SDKs that collect data. No always-on location: location
is read only while a run is recording or paused, and there is no "allow all the time"
permission to grant. No coaching, splits, audio cues, heart rate or elevation gain. No
way to delete a finished run from inside the app yet — that is a gap in this version,
not a principle; uninstalling clears everything.

PRIVACY, PLAINLY

Runs live in the app's private database. Android's automatic cloud backup is switched
off, so the operating system does not copy them to Google's servers. Uninstalling the
app or clearing its storage deletes every run.

The app requests internet permission for one purpose: map tiles. Whenever a map is on
screen, the area you are looking at is requested from OpenFreeMap, which serves map data
from OpenStreetMap contributors. Like any web service it receives your IP address and
the tiles requested. No run data — not your route, not your speed, not the run's
identifier — is ever included.

Full privacy note: https://wichosu.github.io/simple-runner/privacy.html

GOOD TO KNOW

Size: about 60 MB. The map renderer is bundled so the map does not depend on Google Play
Services. Requires Android 8.0 or newer. Metric only in this version: kilometres and
minutes per kilometre.

Free and open source, Apache-2.0. Source, issues and the full license:
https://github.com/wichosu/simple-runner

Not affiliated with Google. Map data © OpenStreetMap contributors, tiles by OpenFreeMap.
```

Runtime permission note: the description promises no background location, which matches
`AndroidManifest.xml` — `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `INTERNET`
and nothing else.

## Categorisation and contact

| Field | Value |
| --- | --- |
| App or game | App |
| Category | Health & Fitness |
| Tags | Running, GPS, Fitness |
| Contact email | `REPLACE-WITH-YOUR-PLAY-EMAIL` (must be a real, monitored address) |
| Website | `https://wichosu.github.io/simple-runner/` |
| Privacy policy | `https://wichosu.github.io/simple-runner/privacy.html` |
| Phone | leave blank |

The contact email is public on the listing and Play verifies the account against it, so
it cannot be a placeholder at submission time.

## Graphics

| Asset | File | Size | Limit |
| --- | --- | --- | --- |
| App icon | `store/icon-512.png` | 512×512 | exactly 512×512, 32-bit PNG |
| Feature graphic | `store/feature-graphic.png` | 1024×500 | 1024×500, no transparency |
| Phone screenshots | `store/screenshot-home.png`, `-live.png`, `-detail.png` | 1080×1920 | 2–8 images, 9:16, 320–3840 px |

Screenshots were captured at 1080×1920 by resizing the emulator display, not by cropping,
so no interface element is cut off. Order them **home, live, detail**: what you see after
a run, what you see during one, what a run looks like afterwards.

## Release notes — version 1.0.0 (500 characters max)

```
First release.

Distance, moving time and pace with the route on a map. Pause and resume; paused time
is excluded and the route breaks at the pause. Keeps recording in the background and
recovers an unfinished run if Android kills the app. Runs are stored on the phone only,
with no account and no cloud. The map is the only feature that needs a connection.
```

## Open items

- **Contact email** — required, must be real.
- **GitHub slug** — every URL here assumes `github.com/wichosu/simple-runner` and
  `wichosu.github.io/simple-runner`. If the account name differs, replace it in
  `store/listing.md`, `docs/index.html`, `docs/privacy.html` and `README.md`.
- **Google Play URL** — `https://play.google.com/store/apps/details?id=com.wichosu.simplerunner`
  returns 404 until the app is published. The site links to it already, which is fine for
  a listing that goes live at the same time as the app.
