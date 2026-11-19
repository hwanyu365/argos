# argos

An app that lets a parent's Android device see the **app currently in use**, the **video title and web address** being viewed, and **per-app usage time over the past month** on a child's Android device.

- There is no server. It runs on a single free (Spark) Firebase project that you create yourself
- The child's device always shows a notification that it is being monitored
- Because it reads browser addresses through an accessibility service, it can't be published on the Play Store; install the APK directly

> Status: in development. Requirements and behavior rules are in [docs/specification.md](docs/specification.md).

## Install and use

### 1. Prepare a Firebase project (free)

1. Create a project in the [Firebase console](https://console.firebase.google.com). Keep the Spark (free) plan
2. **Authentication** > Sign-in method > enable **Anonymous**
3. **Realtime Database** > Create database (start in locked mode)
4. Project settings > Your apps > **Add Android app**, package name `io.github.hwanyu365.argos`
   - You don't need to download `google-services.json`. Only the API key, app ID and project ID shown on screen are needed
5. Deploy the security rules

   ```sh
   npm --prefix firebase ci
   npm --prefix firebase exec -- firebase login
   npm --prefix firebase exec -- firebase deploy --only database --project <project ID>
   ```
6. (Recommended) Restrict the **Android key** so only the argos app can use it
   1. Get the signing key's SHA-1: `./gradlew signingReport` (debug key, and the release key in `local.properties`)
   2. In the [Google Cloud console](https://console.cloud.google.com/apis/credentials), select the same account and project as Firebase → APIs & Services → Credentials
      - If the project doesn't show up, open `https://console.cloud.google.com/apis/credentials?project=<project ID>` directly
   3. `Android key (auto created by Firebase)` (the key whose value equals `ARGOS_FIREBASE_API_KEY`) → Application restrictions **Android apps** → add package name `io.github.hwanyu365.argos` + SHA-1 for each signing key
      - Debug builds use the package `io.github.hwanyu365.argos.debug`, so add the debug key's SHA-1 with this package name
   - argos doesn't use `Browser key (auto created by Firebase)`, but **do not delete it**. It was created automatically by Firebase and may be used by console features
   - An APK signed with an unregistered key fails to sign in (`Requests from this Android client application ... are blocked`). Also add the debug key of each other PC and the CI release key
   - This restriction only reduces key misuse; it is not complete security. A Firebase API key is an identifier, not a password; data is protected by the security rules (`database.rules.json`) and per-family authentication

### 2. Build

- Required tools: JDK 21, Android SDK (API 37), Node.js 22 (for rules tests and deployment)
- Copy `local.properties.example` to `local.properties` and fill in the values. They can also be given as environment variables with the same names
  - If `local.properties` already exists because you opened the project in Android Studio, don't copy; just add the `ARGOS_FIREBASE_*` lines (overwriting would remove `sdk.dir`)
- If JDK 21 is not on PATH, set it before building (PowerShell: `$env:JAVA_HOME = "<JDK 21 path>"`)

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

If you build without the settings, the app shows a "Firebase setup required" screen.

### 3. Device setup

1. Install the APK on both the parent and child devices
2. Guardian device: `Create a family` → enter the user's name → `Create` → `Invite` → `Child` → the invite QR and code are shown
3. Child device: `Join with invite code` → `Scan QR` or type the code → `Join` → grant the permissions in `Required permissions` in order
   - On Android 13 and later, **Settings > Apps > Argos > ⋮ > Allow restricted settings** is required before enabling accessibility and notification access
   - On child devices managed by Family Link, installing apps or allowing "Usage access" may be blocked. In that case, connect the device to a PC over USB and install and grant permissions with adb

     ```sh
     adb install argos.apk
     adb shell appops set io.github.hwanyu365.argos GET_USAGE_STATS allow
     # Allow restricted settings: without this, accessibility enabled via adb turns off on reboot
     adb shell cmd appops set io.github.hwanyu365.argos ACCESS_RESTRICTED_SETTINGS allow
     # Optional: video title and web address (if other accessibility services exist, join them with ':')
     adb shell settings put secure enabled_accessibility_services io.github.hwanyu365.argos/io.github.hwanyu365.argos.child.UrlAccessibilityService
     adb shell cmd notification allow_listener io.github.hwanyu365.argos/io.github.hwanyu365.argos.child.MediaListenerService
     ```

## Development

This repository is run with humans handling issues and reviews, and Claude Code handling implementation and verification. Rules are in [CLAUDE.md](CLAUDE.md).

```sh
./gradlew check            # ktlint + Android lint + unit tests
npm --prefix firebase test # security rules tests (Firebase Emulator, requires JDK 21)
```

- Workflow: write an issue → local Claude Code or `@claude` in an issue comment → draft PR → CI + Claude review → merge
- Release: pushing a `v*` tag distributes a signed APK to the **Firebase App Distribution** tester group
  - Why not GitHub Releases or Actions artifacts: anyone can download them from a public repository, and the release APK contains the owner's Firebase settings
  - Testers accept the invitation email, then install and update via the Firebase App Tester app (or the email link)

### Repository owner initial setup (when running a fork)

| Task | How |
| --- | --- |
| Install the Claude GitHub App | `/install-github-app` in local Claude Code |
| `CLAUDE_CODE_OAUTH_TOKEN` | Generate with `claude setup-token` → add to Secrets |
| `ARGOS_FIREBASE_API_KEY`, `_APP_ID`, `_PROJECT_ID`, `_DATABASE_URL` | For release builds |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | For release signing. `base64 -w0 release.jks` |
| `FIREBASE_SERVICE_ACCOUNT` | Service account JSON for automatic rules deployment and App Distribution upload (roles: Firebase Realtime Database Admin, Firebase App Distribution Admin) |
| App Distribution tester group | Firebase console → App Distribution → Testers & Groups → create group `family` and add family Google accounts. If you use another alias, set it in the repository variable `FIREBASE_TESTER_GROUPS` |
| main branch protection | Require CI (`android`, `rules`) to pass, merge via PR only |

## License

MIT
