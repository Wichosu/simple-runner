# Website download

`simple-runner-1.0.0.apk` belongs here and is what the **Download APK** button on
`docs/index.html` points at.

It is not in the repository yet, because it has to be the APK **Google Play signs**, not
one built locally:

1. Publish the release to Play.
2. Play Console → **Test & release → Latest releases and bundles →** the bundle **→
   Downloads**, and download the universal APK.
3. Save it here as `simple-runner-1.0.0.apk`.

Locally built APKs are signed with the upload key, so they will not install over a Play
installation and will not receive Play updates. That is why this file is missing rather
than filled with `app/build/outputs/apk/release/app-release.apk`.

`*.apk` is gitignored with an exception for this folder, so the file will be committed
once it exists. See `store/release-checklist.md` §7.
