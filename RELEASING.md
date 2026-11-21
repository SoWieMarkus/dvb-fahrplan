# Releasing to Google Play

## Once per machine: signing

Release builds are signed with the **upload key** (alias `key0`,
certificate SHA-256 `70:CA:80:0E:0F:5F:66:16:8B:9E:FA:17:92:A1:0E:0C:DF:0B:DD:40:7D:AB:F1:42:CE:C9:53:D4:60:B8:86:3C`).

1. Copy `keystore.properties.example` to `keystore.properties` in the project root (it is git-ignored).
2. Set `storeFile` to the `.jks` file and fill in `storePassword` and `keyPassword`.

Without `keystore.properties`, `bundleRelease` still builds, but unsigned (Play rejects that).

## Every release

1. **Version:** raise `versionCode` (and usually `versionName`) in `app/build.gradle.kts`.
2. **Test the shrunk build** (R8 like release, debug-signed, installs next to the Play app):
   ```
   ./gradlew assembleStaging
   adb install -r app/build/outputs/apk/staging/app-staging.apk
   ```
3. **Tests:** `./gradlew testDebugUnitTest lintDebug`
4. **Build the bundle:** `./gradlew bundleRelease`
   → `app/build/outputs/bundle/release/app-release.aab`
5. **Release notes:** add `release-notes/<version>.md`.
6. **Play Console:** Test and release → Testing → Internal testing → Create new release →
   upload the `.aab`, paste the release notes, Save → Review → Roll out.
   Install via the internal test link and check the app on a real phone.
7. **Production:** in the internal release choose "Promote release" → Production,
   optionally with a staged rollout (e.g. 20 %), then raise it to 100 %.
8. Tag the commit: `git tag v<versionName> && git push --tags`

## If the upload key is lost

With Play App Signing (Play Console → Test and release → App integrity), Google keeps the app signing key.
Request an upload key reset there and register a new upload certificate; users keep receiving updates.
