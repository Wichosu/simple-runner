# Release checklist — 1.0.0

Ordered, with the commands that produce the evidence. Anything already verified is
marked with the value that was measured, so a repeat run can be compared rather than
trusted.

## 1. Pre-flight

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests green (tracker state machine,
      GPS filter, formatters, repository math).
- [ ] `versionCode` / `versionName` in `app/build.gradle.kts` are the intended pair.
      For 1.0.0: `versionCode = 1`, `versionName = "1.0.0"`. Play refuses an upload with
      a `versionCode` it has already seen.
- [ ] `android:allowBackup="false"` still set (it is what makes "nothing leaves the
      phone" true even against the OS backup).
- [ ] Privacy copy still matches the manifest. If a permission was added or removed,
      `docs/privacy.html` and `store/data-safety.md` change in the same commit.

## 2. Build

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

Outputs:

- `app/build/outputs/bundle/release/app-release.aab` — the file Play accepts.
- `app/build/outputs/apk/release/app-release.apk` — universal APK, useful for local
  testing only. **Do not publish this as the website download** (see §6).

Release builds are signed with the upload key from `keystore.properties`. Without that
file the build still succeeds, unsigned, which is the intended behaviour for a fresh
clone.

## 3. Verify the artifacts

```bash
BT=$HOME/Android/Sdk/build-tools/36.1.0
"$BT/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
"$BT/zipalign" -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk | tail -1
```

Expected for this build:

| Check | Expected |
| --- | --- |
| Signer | `CN=Wichosu, OU=Simple Runner, O=Wichosu, C=DE` |
| Certificate SHA-256 | `a45268817f86d65df35481c2e24c9238a64884261c1bdfbcdeb2cf77848be386` |
| zipalign 16 KB | `Verification successful` |
| APK size | 59,480,761 bytes (59.5 MB) |
| AAB size | 27,171,982 bytes (27.2 MB) |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |

The 16 KB page-size requirement (Play, from Nov 2025) is satisfied two ways: `zipalign
-P 16` passes, and every `LOAD` segment of `libmaplibre.so` has `p_align = 0x4000`.

```bash
unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/libmaplibre.so > /tmp/libmaplibre.so
readelf -lW /tmp/libmaplibre.so | grep LOAD
```

## 4. Play Console — create the app

- [ ] Pay the one-time $25 developer registration and complete identity verification.
- [ ] Create app: name **Simple Runner**, default language English (US), type **App**,
      **Free**.
- [ ] Enable **Play App Signing** (default). Google becomes the app-signing key; the local
      upload key only authenticates uploads.
- [ ] Upload the AAB to **Closed testing**.

## 5. Store presence and policy

Paste from `store/listing.md` and `store/data-safety.md`:

- [ ] Main store listing: name, short description, full description, contact email,
      website, privacy policy URL.
- [ ] Graphics: icon 512×512, feature graphic 1024×500, three phone screenshots
      (home, live, detail).
- [ ] Data safety: **No** collection, **No** sharing, no ads, no accounts.
- [ ] Content rating questionnaire → expected everyone / PEGI 3.
- [ ] App access: all functionality available without special access.
- [ ] Ads: no ads.
- [ ] Target audience: 13+, not designed for children.
- [ ] Foreground service declaration + demo video — `store/foreground-service.md`.
- [ ] Government app: no. Financial features: no. Health: no.

## 6. Closed testing, then production

1. [ ] Add at least **12 testers** (a Google Group or an email list) and run closed
       testing for **14 continuous days**. This is Play's requirement for new personal
       developer accounts and cannot be shortened.
2. [ ] Use the 14 days to confirm on the target device (Poco M7, Android 16): permission
       prompts, background recording with the screen off, notification actions, recovery
       after `adb shell am force-stop`, and that a run survives a reboot mid-run.
3. [ ] Apply for production access, answer the "how did you test" questions with the
       closed-test feedback.
4. [ ] Roll out to production (staged rollout is fine; 100% when confident).

## 7. Website download — the step that cannot be done early

`docs/download/simple-runner-1.0.0.apk` is **not in the repo yet**. The page's primary
button points at it, so until this section is done that link is a 404. It is deliberately
not filled with the local `app-release.apk`: that file is signed with the upload key,
whereas installs must come from Play's app-signing key to upgrade cleanly later.

After the release is live:

```bash
# Play Console → Test & release → Latest releases and bundles → bundle → Downloads
# choose "Universal APK" (or the arm64 APK if a universal one is not offered)
cp ~/Downloads/simple-runner-1.0.0.apk docs/download/
```

- [ ] Verify the downloaded APK's signer is Google's app-signing certificate, not
      `CN=Wichosu`:
      `"$BT/apksigner" verify --print-certs docs/download/simple-runner-1.0.0.apk`
- [ ] Check the real size and update the copy if it drifted from "About 60 MB"
      (`docs/index.html`, two places: hero note and download meta).
- [ ] Install over a Play build to prove the certificate matches.

## 8. Publish the site

- [ ] Push `docs/` to GitHub, then Settings → Pages → source **Deploy from a branch**,
      branch `main`, folder `/docs`.
- [ ] `https://wichosu.github.io/simple-runner/` loads, the screenshots resolve, no
      horizontal scrollbar at 390 px, and the `download/` file actually downloads.
- [ ] Replace the assumed GitHub slug everywhere if the account name differs:
      `store/listing.md`, `store/release-checklist.md`, `docs/index.html`,
      `docs/privacy.html`, `README.md`.
- [ ] Confirm the Play link resolves once the app is published (it 404s before).

## 9. After release

- [ ] Bump `versionCode` for every subsequent upload, including fixes to a rejected
      submission.
- [ ] Keep the upload keystore (`~/keystores/simple-runner-upload.jks`) and
      `keystore.properties` backed up somewhere that is not this machine. Losing the
      upload key is recoverable through Play support; losing the app-signing key is not
      (which is why Play holds it).
- [ ] Every new release repeats §2–§3 before upload: the 16 KB and signing checks are
      cheap and catch a broken dependency bump.
