# Setup Guide — Cloud-Bridge v2.8.0

Cloud-Bridge is an independent educational AAOS template for developers using the Spotify Web API. It is not affiliated with Spotify; you are expected to bring your own Spotify developer app credentials.

## Prerequisites

1. **Android Studio** Hedgehog (2023.1.1) or later
2. **JDK 17** (bundled with Android Studio)
3. **Android SDK 35** with Build Tools 35.x
4. **AAOS System Image** — API 33+ x86_64 (Automotive with Google APIs)

## 1. Clone & Open

```bash
git clone <repository-url> AAOS_Spotify_Cloud_Bridge
cd AAOS_Spotify_Cloud_Bridge
```

Open the project in Android Studio. Gradle sync should start automatically.

## Repository Sync Workflow

- Check for upstream updates before starting work:

```powershell
git fetch origin
git status -sb
git log --oneline HEAD..origin/main
```

- Rebase local work on the latest remote branch when needed:

```powershell
git pull --rebase origin main
```

- After build, tests, and emulator verification pass, upload changes:

```powershell
git status -sb
git add -A
git commit -m "<summary>"
git push origin main
```

## 2. Create Your Own Spotify Developer App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in the form:
   - **App name**: `AAOS Cloud Bridge` (or anything)
   - **Redirect URI**: `http://localhost:8888/callback` (needed for initial token)
   - **APIs**: Select **Web API**
4. Note your **Client ID** and **Client Secret**

## 3. Obtain a Refresh Token

QR onboarding is the primary path for the current template, but the local helper below is still useful for development, CI, and fallback troubleshooting.

If you're already upgrading from an older single-profile install, the app now migrates any previously saved DataStore credentials into the new profile database automatically on first launch so they are not lost.

### Option A — Automated Windows helper (recommended)

1. Open [scripts/spotify-oauth.config.xml](../scripts/spotify-oauth.config.xml)
2. Replace `YOUR_CLIENT_ID_HERE` and `YOUR_CLIENT_SECRET_HERE`
3. Make sure the same redirect URI is registered in your Spotify developer app:
  - `http://127.0.0.1:8888/callback`
4. Run from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\refresh-spotify-token.ps1
```

What it does:
- opens Spotify authorization in your browser
- captures the callback locally on `127.0.0.1:8888`
- exchanges the code for fresh tokens
- updates the hardcoded fallback constants in [app/src/main/kotlin/com/cloudbridge/spotify/auth/TokenManager.kt](../app/src/main/kotlin/com/cloudbridge/spotify/auth/TokenManager.kt) for local development convenience
- writes a backup `TokenManager.kt.bak` next to the source file

### Option B — Using the Spotify OAuth Tool

1. Go to https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
2. Follow the PKCE tutorial to get tokens with these **scopes**:

```
user-library-read
user-library-modify
user-read-playback-state
user-modify-playback-state
user-read-currently-playing
user-read-recently-played
user-top-read
playlist-read-private
playlist-read-collaborative
```

> **Important**: Current builds require `user-library-modify`,
> `user-read-recently-played`, `user-top-read`, `playlist-read-private`, and
> `playlist-read-collaborative` for the full feature set.

### Option C — Using curl (manual PKCE flow)

**Step 1: Generate PKCE challenge**

```bash
# Generate code_verifier (43-128 chars, URL-safe)
CODE_VERIFIER=$(python3 -c "import secrets; print(secrets.token_urlsafe(64))")
echo "Code Verifier: $CODE_VERIFIER"

# Generate code_challenge (SHA-256 of verifier, base64url-encoded)
CODE_CHALLENGE=$(echo -n "$CODE_VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
echo "Code Challenge: $CODE_CHALLENGE"
```

**Step 2: Open authorization URL in browser**

```bash
CLIENT_ID="your_client_id_here"
SCOPES="user-library-read%20user-library-modify%20user-read-playback-state%20user-modify-playback-state%20user-read-currently-playing%20user-read-recently-played%20user-top-read%20playlist-read-private"

echo "Open this URL in your browser:"
echo "https://accounts.spotify.com/authorize?\
client_id=$CLIENT_ID&\
response_type=code&\
redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Fcallback&\
scope=$SCOPES&\
code_challenge_method=S256&\
code_challenge=$CODE_CHALLENGE"
```

After authorizing, you'll be redirected to:
`http://localhost:8888/callback?code=AUTHORIZATION_CODE`

Copy the `AUTHORIZATION_CODE` from the URL.

**Step 3: Exchange code for tokens**

```bash
AUTH_CODE="paste_authorization_code_here"

curl -X POST https://accounts.spotify.com/api/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$CLIENT_ID" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTH_CODE" \
  -d "redirect_uri=http://localhost:8888/callback" \
  -d "code_verifier=$CODE_VERIFIER"
```

Response:
```json
{
  "access_token": "BQD...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "AQA...",    ← THIS IS WHAT YOU NEED
  "scope": "user-library-read user-library-modify ..."
}
```

**Save the `refresh_token`** — it does not expire unless you revoke the app.

## 4. Set Up the AAOS Emulator

### Install the System Image

1. In Android Studio: **Tools → SDK Manager → SDK Platforms**
2. Check **Show Package Details**
3. Under **Android 13 (API 33)** (or higher), find:
   - `Automotive with Google APIs Intel x86_64 Atom System Image`
4. Install it

### Create the AVD

1. **Tools → Device Manager → Create Virtual Device**
2. Category: **Automotive**
3. Select **Automotive (1024p landscape)** or any automotive hardware profile
4. Select the AAOS system image you installed
5. Finish and **boot the emulator**

### Known Emulator Quirks

- The AAOS emulator has no Play Store — apps must be sideloaded
- First boot may take 2-3 minutes; subsequent boots with snapshots are faster
- Internet access should work out of the box (emulator uses host networking)

## 5. Install the App

```bash
# From the project root:
./gradlew assembleDebug

# Install on the running AAOS emulator (user 10 for automotive)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 6. Launch the App

The app runs as a standalone Compose Activity — no MediaBrowser integration.

```bash
# Launch from adb (automotive user 10)
adb shell am start --user 10 -n com.cloudbridge.spotify/.ui.MainActivity
```

Or find **Cloud-Bridge** in the AAOS launcher/app list.

> The Activity has `distractionOptimized` metadata, which allows it to remain
> visible while driving (bypassing AAOS UXR scrolling restrictions).

## 6B. Build a Signed Release AAB

Generate the Play Store Android App Bundle from the repo root:

**Linux / macOS**

```bash
./gradlew bundleRelease --console=plain
```

**Windows (PowerShell)**

```powershell
& .\gradlew.bat bundleRelease --console=plain *>&1 | Tee-Object -FilePath release_bundle_output.log
```

Expected output artifact:

- [app/build/outputs/bundle/release/app-release.aab](../app/build/outputs/bundle/release/app-release.aab)

Notes:

- Release (and debug) use the `local` signing config in [app/build.gradle.kts](../app/build.gradle.kts), preferring `release.keystore` at the repo root (gitignored — keep a secure backup of the **Play upload** key).
- Before any new release build, increment `versionCode` and `versionName` in [app/build.gradle.kts](../app/build.gradle.kts).
- AAB files are for Play Console upload, not direct `adb install`. For sideload testing use `./scripts/install-debug.sh` (same-cert upgrade, preserves OAuth profiles) or `assembleRelease` + `adb install -r`.
- If Play Console rejects the certificate, restore the original upload `release.keystore` that signed previous production bundles — do not generate a new one without resetting the upload key in Play App Signing.

## 7. Use the App

1. The **Home** screen shows Recently Played, Top Tracks, Featured Playlists,
   and New Releases as album art grids.
2. Use the **NavigationRail** (left side) to switch between Home, Library,
   Queue, and Settings.
3. Tap any tile to play it. The **MiniPlayer** appears at bottom-right.
4. Tap the MiniPlayer to expand the **Now Playing** screen with full controls.
5. Use the **Queue** tab to see upcoming tracks and swipe to remove items.

**Important**: Your phone must have Spotify running (even in background) and
be logged into the same account. The phone is the playback device — audio
reaches the car via Bluetooth A2DP.

## 8. Troubleshooting

| Problem | Solution |
|---------|----------|
| App crashes on launch | Check logcat for `MainActivity` / `SpotifyViewModel` tags |
| Home screen is empty | Verify scopes include `user-read-recently-played`, `user-top-read`, `playlist-read-private` |
| "No device found" | Ensure Spotify is open on your phone and logged into the same account/profile that Cloud-Bridge is controlling |
| Tracks don't play | Check logcat for `SpotifyPlaybackController` / `DeviceManager` tags |
| Heart button 403s | Token lacks `user-library-modify` scope — re-authorize with updated scopes |
| 401 errors looping | Refresh token may be revoked — generate a new one |
| 429 errors | Rate limited — the retry interceptor handles this automatically |
| Blank album art | Check network connectivity; Coil loads art via HTTPS |

## 9. Logcat Tags

Filter logcat with these tags to debug:

```
SpotifyViewModel
SpotifyPlaybackController
DeviceManager
TokenRefreshAuth
RetrofitProvider
```

Example filter:
```bash
adb logcat -s SpotifyViewModel:* SpotifyPlaybackController:* DeviceManager:*
```

## 10. In-App Logging

Cloud-Bridge includes an internal file-based logger (`AppLogger`) that can be enabled at runtime from **Settings > Logging**.

### Enabling Logging

1. Open the app and go to **Settings** (gear icon).
2. Scroll to the **Logging** section.
3. Toggle **Enable Logging** on.

When enabled, every API call, auth event, playback command, device discovery result, and error is written to a timestamped log file in addition to the standard Android logcat. The logger buffers entries in memory and flushes them to disk periodically to minimize I/O overhead.

### What Gets Logged

| Category | Details |
|----------|---------|
| **API calls** | Every Spotify Web API request/response (`→ GET /v1/me/playlists`, `← 200`) |
| **Auth / tokens** | Token refresh attempts and outcomes, rate limit lockouts |
| **Playback** | Play, pause, skip, seek, shuffle, repeat commands with device IDs |
| **Device discovery** | Found devices, priority selection, cache hits/misses, locked device usage |
| **Profile management** | Active profile switches, QR onboarding sessions, credential saves |
| **Library cache** | Playlist/album/show refresh counts, cache clears on profile switch |
| **Errors** | HTTP 401/403/404/429 errors, network exceptions, playback failures |

### Exporting Logs

1. In **Settings > Logging**, tap **Export**.
2. On AAOS builds, Cloud-Bridge opens the system document picker and asks where to save `cloudbridge_logs_export.txt`.
3. Choose a USB drive or another writable document location.
4. On non-AAOS images that do not expose a document picker, Cloud-Bridge falls back to Android's share sheet.

The AAOS emulator image used by this project does not ship any `ACTION_SEND` targets for `text/plain`, so USB export uses the document picker rather than the share chooser.

### Log File Details

- **Location**: `files/logs/cloudbridge.log` (app-private internal storage)
- **Max size**: 5 MB per file, auto-rotated. Up to 5 rotated files are kept.
- **Format**: `YYYY-MM-DD HH:mm:ss.SSS LEVEL/TAG: message`
- **Clear**: Tap **Clear** in Settings to delete all log files.

### Privacy

Log files contain Spotify API endpoint paths, HTTP status codes, device names, and profile names. They do **not** contain OAuth tokens, passwords, or Bearer header values. Review the export before sharing externally.
