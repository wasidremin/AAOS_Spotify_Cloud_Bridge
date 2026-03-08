---
description: These instruction are for the AAOS Spotify Cloud-Bridge agent, used for all development, build, test, and deployment tasks.
# applyTo: 'Describe when these instructions should be loaded' # when provided, instructions will automatically be added to the request context when the pattern matches an attached file
---
---
## 1. Project Overview

You are an expert Android Automotive OS (AAOS) developer maintaining the **Spotify Cloud-Bridge** — a custom AAOS media app that bypasses General Motors' removal of Apple CarPlay/Android Auto.

**The Cloud-Bridge Pattern:**
- This app is a **remote control**, not a media player. It never plays local audio.
- **Audio routing:** Phone → Bluetooth A2DP → car speakers (handled natively).
- **UI/Data routing:** AAOS app → Spotify Web API → renders library on the car screen.
- **Playback control:** Tap a track → REST API call → Spotify plays on the user's phone via `device_id` targeting.

**Reference Documents:**
- Architecture: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)
- Full SDS: [SOFTWARE_DESIGN_SPECIFICATION.md](../../SOFTWARE_DESIGN_SPECIFICATION.md)
- Testing: [docs/TESTING.md](../../docs/TESTING.md)
- Setup: [docs/SETUP_GUIDE.md](../../docs/SETUP_GUIDE.md)

## 2. Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.0 |
| Target OS | Android Automotive OS | API 30+ / targetSdk 35 |
| UI | Jetpack Compose + Material3 | BOM 2024.12.01 |
| Image Loading | Coil | 2.7.0 |
| State | ViewModel + StateFlow + Compose runtime | — |
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| JSON | Moshi (KSP codegen) | 1.15.1 |
| Media Session | AndroidX Media3 session + common | 1.5.1 |
| Caching | Room (KSP) | 2.6.1 |
| Storage | Jetpack DataStore Preferences | — |
| Async | Kotlin Coroutines | 1.9.0 |
| Build | Gradle Kotlin DSL + version catalog | — |

## 3. Project Structure

```
app/src/main/kotlin/com/cloudbridge/spotify/
├── SpotifyCloudBridgeApp.kt          # Application — manual DI root
├── auth/
│   ├── TokenManager.kt               # Active-profile preference + Room-backed credential storage
│   ├── AuthInterceptor.kt            # OkHttp Interceptor — adds Bearer header
│   ├── TokenRefreshAuthenticator.kt  # OkHttp Authenticator — auto-refresh with race-safe Mutex
│   └── SetupActivity.kt              # Credential entry (APPLICATION_PREFERENCES intent)
├── cache/
│   └── CacheDatabase.kt              # Room DB + DAO + entities for offline library + user profiles
├── data/
│   └── SpotifyLibraryRepository.kt   # Library paging + cache synchronization layer
├── domain/
│   └── CustomMixEngine.kt            # Generated Daily Drive / decade mix rules
├── network/
│   ├── model/AuthModels.kt           # TokenResponse
│   ├── model/CloudRelayModels.kt     # QR onboarding relay payloads
│   ├── model/SpotifyModels.kt        # All Spotify API data classes (Moshi @JsonClass)
│   ├── CloudRelayService.kt          # Retrofit — cloud relay polling for Add Profile
│   ├── SpotifyAuthService.kt         # Retrofit — accounts.spotify.com (/api/token)
│   ├── SpotifyApiService.kt          # Retrofit — api.spotify.com (library, devices, playback)
│   └── RetrofitProvider.kt           # Dual Retrofit instances + RateLimitRetryInterceptor
├── player/
│   ├── DeviceManager.kt              # Device discovery with priority selection (2-min cache)
│   ├── SpotifyPlaybackController.kt  # Playback command wrapper with 404 retry
│   └── MediaSessionManager.kt        # MediaSession + StubPlayer for steering wheel buttons
├── receiver/
│   └── BluetoothAutoLaunchReceiver.kt  # Auto-launches app on Bluetooth connect
├── ui/
│   ├── MainActivity.kt               # Compose entry point (distractionOptimized)
│   ├── AddProfileViewModel.kt        # QR onboarding session + polling state
│   ├── SpotifyViewModel.kt           # ViewModel — UI state and navigation orchestration
│   ├── theme/                         # Material3 dark theme, Spotify color tokens
│   ├── screens/                       # Home, Library, Search, NowPlaying, Queue, Settings, etc.
│   └── components/                    # MiniPlayer, AlbumArtTile, PlayerControls
└── util/
    └── ApiResult.kt                   # Sealed result wrapper

app/src/test/kotlin/com/cloudbridge/spotify/
├── auth/TokenManagerTest.kt
├── network/SpotifyAuthServiceTest.kt
├── network/SpotifyApiServiceTest.kt
└── player/DeviceManagerTest.kt
```

## 4. Architecture Rules

### Rule A: NO LOCAL AUDIO PLAYBACK
Never implement ExoPlayer or local audio rendering. The `StubPlayer` in `MediaSessionManager.kt` implements the full Media3 `Player` interface with no-op stubs. Transport commands (`play`, `pause`, `seekToNext`, `seekToPrevious`) fire coroutines that call the Spotify Web API via `SpotifyPlaybackController`.

### Rule B: Manual Refresh Token Auth
Spotify doesn't support Device Authorization (RFC 8628). We use manually generated `refresh_token` values stored per-profile in Room, while DataStore stores only the active profile pointer and app preferences. An `OkHttp Authenticator` with `Mutex` intercepts 401s, refreshes the active profile token, and retries.

### Rule C: Device ID Targeting
Every playback API call MUST include `device_id` targeting the user's smartphone. `DeviceManager` discovers and caches the target device.

### Rule D: Custom Compose UI (Not MediaLibraryService Templates)
This app uses a custom `distractionOptimized` Activity with full Jetpack Compose UI — **not** the rigid AAOS media templates. All screens are custom composables in the `ui/screens/` package.

### Rule E: Global Rate-Limit Lockout
Long Spotify `429 Retry-After` penalties must be persisted globally in `TokenManager` and enforced before any further `api.spotify.com` requests are sent. Do not implement per-screen sleep loops as the primary defense; the lockout belongs in the shared auth/network layer so every feature respects it consistently.

### Rule F: Phase 1 Multi-Profile Isolation
Until library cache rows and pinned items are fully profile-scoped, switching the active Spotify profile must clear cached library tables and pinned items before loading the new account. Do not leave previous-account content visible after a profile switch.

## 5. Development Workflow (MANDATORY)

**Every prompt/change MUST follow this cycle:**

### Step 1: Document
- Update relevant documentation (README.md, TESTING.md, SOFTWARE_DESIGN_SPECIFICATION.md) to reflect changes.

### Step 2: Build
- Run: `& .\gradlew.bat clean assembleDebug --console=plain *>&1 | Tee-Object -FilePath build_output.log`
- On failure, read the log and fix compile errors before proceeding.

### Step 3: Test
- Run: `& .\gradlew.bat testDebugUnitTest --console=plain *>&1 | Tee-Object -FilePath test_output.log`
- All 5 core test classes must pass: `TokenManagerTest`, `SpotifyAuthServiceTest`, `SpotifyApiServiceTest`, `DeviceManagerTest`, `CustomMixEngineTest`.

### Step 4: Test on Emulator
- Install: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start --user 10 -n com.cloudbridge.spotify/.ui.MainActivity`
- Screenshot: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > screenshots/<name>.png`

### Step 5: Update Revision
- Append to `revisions.txt` with the new revision entry (see Section 7).
- If you are producing a release artifact (`bundleRelease` / `assembleRelease`), also bump `versionCode` and `versionName` in `app/build.gradle.kts` before building so the AAB/APK is installable and uniquely versioned.

## 6. Revision Numbering

The project uses semantic revision numbers in the format `R<major>.<minor>`:

| Level | When to bump | Example |
|-------|-------------|---------|
| Major | Breaking API change, major feature, architecture change | R1.0 → R2.0 |
| Minor | Bug fix, UI tweak, new screen, model change | R2.0 → R2.1 |

Every successful build that introduces a change MUST get a new revision entry in `revisions.txt`.

## 7. Revisions File Format

The file `revisions.txt` in the project root tracks all changes. Format:

```
═══════════════════════════════════════════════════════════════
 R<major>.<minor>  —  <YYYY-MM-DD>
═══════════════════════════════════════════════════════════════
 Summary: <one-line description>

 Changes:
   • <change 1>
   • <change 2>
   • ...

 Files Modified:
   • <file path 1>
   • <file path 2>
   • ...

 Build: PASS | FAIL
 Tests: PASS | FAIL | SKIPPED
 Emulator: VERIFIED | NOT TESTED
═══════════════════════════════════════════════════════════════
```

## 8. Build & Environment Notes

### Reliable Build Procedure (ALWAYS follow this order)

**Step 1 — Stop all daemons** (prevents stale lock and incremental-cache corruption):
```powershell
& .\gradlew.bat --stop *>&1 | Tee-Object -FilePath gradle_stop.log
```

**Step 2 — Clear Kotlin incremental cache** (mandatory when you see cascading "Unresolved reference", "Overload resolution ambiguity", or `MatchGroup` type-mismatch errors that don't match the actual source code):
```powershell
Remove-Item -Recurse -Force app\build\kotlin\compileDebugKotlin\ -ErrorAction SilentlyContinue
```

**Step 3 — Build with PowerShell-native logging** (forces a fresh APK and keeps the full log on disk):
```powershell
& .\gradlew.bat clean assembleDebug --console=plain *>&1 | Tee-Object -FilePath build_output.log
```

**Step 4 — Check the result and verify the APK was regenerated**:
```powershell
# Quick verdict
Get-Content build_output.log -Tail 10

# All compile errors only
Get-Content build_output.log | Select-String "^e:"

# Confirm a fresh APK exists
Get-Item .\app\build\outputs\apk\debug\app-debug.apk |
  Format-List FullName, Length, LastWriteTime
```

**Step 5 — Run unit tests**:
```powershell
& .\gradlew.bat testDebugUnitTest --console=plain *>&1 | Tee-Object -FilePath test_output.log
Get-Content test_output.log -Tail 10
```

### Why Incremental Cache Goes Bad
Kotlin's incremental compilation cache (`app/build/kotlin/compileDebugKotlin/`) stores tab-indexed symbol tables per `.kt` file.
When a file is partially written (agent crash, interrupted build, mid-edit save), the cache entry corrupts.
The Kotlin compiler then reads the stale cache and reports errors against **old** line numbers and **wrong** types rather than the current source — producing confusing messages like:
- `Argument type mismatch: actual type is 'kotlin.text.MatchGroup?'` (on normal list swap code)
- `Overload resolution ambiguity` (on basic arithmetic like `current[index + 1]`)
- `'operator' modifier is required on 'compareTo'` (on a simple `<` comparison)

**Fix:** stop daemons + delete the cache dir + rebuild. Takes ~4 minutes for a full recompile.

### Gradle Commands
- **Stop daemons** before builds if encountering KSP lock errors: `.\gradlew.bat --stop`
- **Use native PowerShell invocation** for Gradle: `& .\gradlew.bat <tasks> --console=plain *>&1 | Tee-Object -FilePath <log>`
- **Use `clean assembleDebug`** when you need a guaranteed fresh debug APK in `app/build/outputs/apk/debug/`
- **Clean KSP cache** if compile fails on generated code: `Remove-Item -Recurse -Force app\build\generated\ksp`

### ADB Path
`adb` is NOT on PATH. Always use the full path:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" <command>
```

### AAOS Emulator
- Current AVD: `Automotive_Ultrawide_2`
- Current serial: `emulator-5554`
- Do not hardcode stale emulator names across docs/scripts. Confirm the active serial with:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```
- User: `--user 10` (required for AAOS)
- Package: `com.cloudbridge.spotify`
- Activity: `.ui.MainActivity`

### Terminal Management
- Do NOT accumulate terminals. Kill background terminals when done.
- Prefer native PowerShell invocation for Gradle.
- Persist logs with `Tee-Object` so output is visible live and saved to disk.

## 9. Known Gotchas & Hard-Won Lessons

### Kotlin Flow — `first()` Requires an Explicit Import
`kotlinx.coroutines.flow.first` is a **top-level extension function**, not a member of `Flow`.
Wildcard `import kotlinx.coroutines.flow.*` does NOT include it in Kotlin 2.0.
```kotlin
// Required explicit import whenever .first() is called on a Flow:
import kotlinx.coroutines.flow.first
```
Missing this import causes an "Unresolved reference 'first'" error at every call site, which in turn triggers cascading type-inference failures that look completely unrelated (MatchGroup type mismatch, compareTo operator errors, etc.). Add the import, clear the incremental cache, and rebuild.

### TokenManager Storage Model
- `TokenManager` no longer provides hardcoded credential fallbacks. Tests and features should assume no Spotify credentials are available until at least one `UserProfile` exists.
- Phase 1 multi-profile auth resolves credentials through Room `user_profiles`; DataStore only keeps `active_profile_id` plus non-auth preferences.
- The Add Profile flow uses a cloud relay plus QR session code; do not reintroduce direct credential typing as the primary in-car onboarding path.

### Media3 Player Interface
- The `Player` interface has **~70+ abstract methods**. All must be implemented in `StubPlayer`.
- When the Media3 version changes, new abstract members may be added. Build will fail with "does not implement abstract member X" — add ALL missing members at once, not one at a time.
- Use `override fun getXxx()` (Java-style getters), NOT `override val xxx` (Kotlin properties). The Media3 Player interface uses Java method conventions.
- Deprecated Player methods still need implementations (e.g., `getCurrentWindowIndex`, `hasNext`, `next`). Annotate with `@Deprecated`.

### Moshi / KSP
- All data classes annotated `@JsonClass(generateAdapter = true)` get compile-time adapters via KSP.
- If KSP fails with file lock errors, stop all Gradle daemons first.
- Nullable fields with defaults properly handle missing JSON keys (critical for podcast vs track data).

### Spotify API Quirks
- **Podcasts vs Tracks**: The Queue and Now Playing APIs return `SpotifyPlayableItem` objects that can be either `type: "track"` (with `album`, `artists`) or `type: "episode"` (with `show`, `images`). Always check `type` before accessing type-specific fields.
- **Rate Limiting**: HTTP 429 with `Retry-After` header. Short delays can retry inside `RateLimitRetryInterceptor`, but long penalties must be promoted into the global `TokenManager` lockout so the whole app pauses outbound Spotify API traffic until the cooldown expires.
- **Search Limit**: In this app's current Spotify environment, `GET /v1/search` returns HTTP 400 `{"error":{"status":400,"message":"Invalid limit"}}` for general search when `limit > 10`. Emulator-backed API tests confirmed both `type=track,album,playlist` and `type=track%2Calbum%2Cplaylist` work at `limit=10`, so the real constraint is the limit, not comma encoding. Use `limit <= 10` for the app's general multi-type search.
- **Made For You access**: Spotify-owned personalized playlists are not reliably discoverable for this developer app, even when playlist search succeeds. Do not build features around cached "Made For You" playlist IDs; generate custom mixes from liked songs, recommendations, and saved podcasts instead.
- **No Queue Remove API**: Spotify doesn't support removing queue items by index. Swipe-to-dismiss is UI-only.
- **Device Transfer**: `PUT /v1/me/player/play` returns 404 when no active device; retry with `transferPlayback` first.
- **Token Refresh Race**: Multiple 401s can trigger simultaneous refreshes. `Mutex` with double-check in `TokenRefreshAuthenticator`.

### AAOS UI
- GM OEM builds reserve right-side screen area for widgets — a black bar on the right is normal, not a bug.
- All tap targets must be minimum 48dp (automotive safety requirement).
- Use `imePadding()` on screens with text input to avoid keyboard overlap.
- `distractionOptimized` Activity flag is required; without it, AAOS will restrict the app while driving.

### Build System
- Gradle Kotlin DSL with version catalog at `gradle/libs.versions.toml`.
- Use PowerShell-native Gradle invocation with `Tee-Object` for reliable live output plus a saved log.
- Clean before build if seeing stale errors: `.\gradlew.bat clean assembleDebug`

## 10. Spotify Scopes Required

```
user-read-playback-state
user-modify-playback-state
user-read-currently-playing
playlist-read-private
playlist-read-collaborative
user-library-read
user-library-modify
```

## 11. Self-Improvement Directive

When encountering undocumented behavior, API quirks, emulator issues, or build workarounds during implementation, **update this agent file immediately** with the finding under Section 9 so future sessions benefit from the knowledge.