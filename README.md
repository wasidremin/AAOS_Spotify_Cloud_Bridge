# AAOS Spotify Cloud-Bridge

An Android Automotive OS (AAOS) media app that provides a fully custom Jetpack Compose UI for Spotify browsing and playback control — without playing any audio locally.



## The Concept

This app provides a pixel-perfect, CarPlay-inspired music interface for your car's infotainment system. Unlike the native AAOS media templates (which have rigid layouts and UXR scrolling limits), this uses a custom `distractionOptimized` Activity to deliver a premium automotive experience:

1. **Browsing**: A large-tile grid home screen with recently played, top tracks, featured playlists, and new releases — all fetched via the **Spotify Web API**.
2. **Playback**: When you tap play, a **REST API command** targets Spotify on your phone as the playback device.
3. **Audio**: Your phone plays the audio → Bluetooth A2DP → car speakers.

The app intentionally does not register as an AAOS media source. Native Bluetooth remains the active audio route so phone playback, steering wheel controls, and speaker output stay stable. This "cloud-bridge" pattern bypasses the need for Android Auto or Apple CarPlay while giving you full control over the car's UI.

## Configuring the Spotify API

Because this app utilizes a "Smart TV" style QR-code login flow to bypass the car's lack of a web browser, your Spotify Developer application must be configured exactly as follows to ensure the OAuth flow works seamlessly.

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and create an app.
2. Note your **Client ID** and **Client Secret**.
3. **Crucial Step:** In your app's Settings, add the following exact URL to your **Redirect URIs** (the trailing slash is mandatory):
   `https://wasidremin.github.io/AAOS_Spotify_Cloud_Bridge/`
4. Ensure your app has access to the Web API. The web app companion will automatically request the following required scopes during login: `user-library-read`, `user-library-modify`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-currently-playing`, `user-read-recently-played`, `user-top-read`, and `playlist-read-private`.

## Adding Accounts (The QR "Cloud Relay" Workflow)



To solve the problem of authenticating on a car screen without a built-in browser, this app uses a Multi-Profile "Cloud Relay" architecture powered by Firebase and GitHub Pages. 

Here is the exact workflow for adding an account:
1. **Initiation:** Tap "Add profile with QR code" in the app's Settings. 
2. **Session Generation:** The car generates a random 6-character session code (e.g., `FPHTZK`) and displays a QR Code.
3. **Scanning:** The user scans the QR code with their phone, which opens the companion web app hosted on GitHub Pages.
4. **Authentication:** The user enters their Spotify Developer `Client ID` and `Client Secret` into the secure web page on their phone, which redirects them to Spotify to authorize the app.
5. **The Relay:** Once authorized, the web app securely drops the resulting `refresh_token`, `client_id`, and the user's profile metadata into a temporary Firebase Realtime Database node linked to the 6-character session code.
6. **Completion:** The car, which has been polling the Firebase endpoint, detects the payload, downloads the credentials into a local encrypted Room database (`user_profiles`), sets the profile to active, and instantly deletes the payload from the cloud. 

*Note: You can add multiple profiles (e.g., "Primary Profile", "Spouse", "Guest") and seamlessly switch between them in the Settings tab. Doing so swaps the active token and instantly reloads the Home and Library grids with that user's customized data.*

## Quick Start

1. Clone the repo and open in Android Studio.
2. Set up an AAOS emulator (API 33+ Automotive image).
3. Create a [Spotify Developer App](https://developer.spotify.com/dashboard) (configured with the Redirect URI mentioned above).
4. Set up a free Firebase Realtime Database in Test Mode and update `CLOUD_RELAY_BASE_URL` in `RetrofitProvider.kt` with your Firebase URL.
5. Build and install: `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk`
6. Launch "Spotify Cloud-Bridge" from the AAOS app launcher.
7. You will immediately be presented with the QR Code onboarding screen. Scan it with your phone, enter your API credentials, and log in.

*Alternative (Legacy) Login:* For CI or manual terminal setups, you can still inject credentials directly via ADB: 
`adb shell am start -n com.cloudbridge.spotify/.auth.SetupActivity --es client_id <id> --es refresh_token <token>`

## Architecture

```text
AAOS Launcher → MainActivity (distractionOptimized)
                      ↓
               Jetpack Compose UI
                      ↓
         SpotifyViewModel (state + navigation)
                      ↓
      SpotifyLibraryRepository + CustomMixEngine
                      ↓
               SpotifyPlaybackController → Spotify Web API
                      ↓
               Phone plays audio → Bluetooth → Car speakers

## Refactor Review Notes

- `SpotifyViewModel` had become a catch-all for navigation, paging, cache writes, and custom mix generation.
- `SpotifyLibraryRepository` now owns Spotify library paging and Room cache synchronization.
- `CustomMixEngine` now owns Daily Drive and decade mix queue construction.
- `SpotifyViewModel` remains the UI orchestrator, which keeps screen-facing state separate from domain rules.

See `docs/ARCHITECTURE.md` for the full architecture document.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| Target OS | Android Automotive OS (API 30+) |
| UI Framework | Jetpack Compose (Material3, BOM 2024.12.01) |
| Image Loading | Coil 2.7.0 (AsyncImage + blur) |
| State Management | ViewModel + StateFlow |
| Networking | Retrofit 2.11.0 + OkHttp 4.12.0 |
| JSON | Moshi 1.15.1 (KSP codegen) |
| Async | Kotlin Coroutines 1.9.0 |
| Storage | Jetpack DataStore Preferences + Room Database |
| Build | Gradle Kotlin DSL with version catalog |

## UI Screens

| Screen | Description |
|--------|-------------|
| **Home** | LazyVerticalGrid with a time-of-day greeting, persistent Clean Swapper toggle, hydrated "Jump Back In", generated "Your Custom Mixes" tiles, library-derived suggested playlists, pinned favorites, and podcast cards with new-episode badges |
| **Manage Pins** | Reorder and remove pinned playlists, albums, and podcasts surfaced on Home |
| **Search** | Dedicated automotive search tab with large text field, 4-column results grid, and album pinning |
| **Library** | Playlists / Albums / Artists / Podcasts tabs with local sort + filter controls, plus tab state preserved across back navigation |
| **Add Profile** | Smart-TV-style QR onboarding screen with a 6-character session code, QR code, and cloud-relay completion polling |
| **Now Playing** | Blurred background, hero album art, progress slider, controls, Start Radio action, explicit badge support, and chapter-safe audiobook metadata |
| **Queue** | Swipe-to-dismiss queue management with higher drag threshold; supports tracks, podcast episodes, and audiobook chapters with unified artwork/text |
| **Playlist Detail** | Track list with larger tap targets, explicit badges, now-playing speaker indicator, and quick Start Radio actions |

## Automotive Layout Notes

- On some OEM AAOS builds (including GM), media apps render inside a bounded app region while the right side of the display is reserved for OEM widgets. A right-side black/system area is expected behavior, not a layout bug.
- Home grid uses `GridCells.Fixed(4)` with larger spacing/padding for stable tile geometry on ultra-wide in-car displays.
- Long-press supported playlists, albums, and podcasts anywhere they appear in Home, Search, or Library to pin/unpin them.
- Pinned items now show a visible push-pin indicator in both grid and list layouts so the action has immediate feedback.
- Home podcast cards show a green `NEW` / `N NEW` badge only when newer unplayed episodes exist above the most recently listened episode for that show.
- "Jump Back In" is hydrated from recently played **contexts** (playlist/album URI), not raw tracks, to match CarPlay behavior.
- Home suggested playlists are now derived from the user's own library because Spotify discovery/search endpoints are currently returning HTTP 400/403 for this app configuration.
- Home "Your Custom Mixes" is generated locally from the user's liked songs, Spotify recommendations, and saved podcasts instead of relying on inaccessible Spotify-owned personalized playlists.
- Daily Drive uses a configurable news podcast source plus a second saved show, liked songs, and recommendation tracks to build a mixed spoken-word/music queue.
- Podcast detail screens include a "Set as News" action so the preferred Daily Drive news source persists in DataStore.
- Home adds a time-of-day greeting card plus a persistent Clean Swapper toggle that stores the explicit-filter preference in DataStore.
- Clean Swapper resolves explicit tracks to cached clean replacements when Spotify search can find a playable non-explicit equivalent, then reuses the mapping from Room.
- Album tiles are forced square (`aspectRatio(1f)`) with `ContentScale.Crop` to avoid stretched artwork.
- Album tiles include a `SpotifyCardSurface` fallback background so missing artwork still renders as a visible card.
- Library Playlists always shows an official "Liked Songs" tile first, backed by `GET /v1/me/tracks`.
- Settings now shows all stored Spotify profiles and provides an **Add profile with QR code** entry point.
- Phase 1 multi-profile auth stores only the active profile ID in DataStore; each profile's Spotify credentials now live in Room as `user_profiles` rows.
- The Add Profile relay now targets the Firebase Realtime Database endpoint.
- Upgrades from the older single-profile build migrate any legacy DataStore credentials into the new Room-backed profile store on startup so saved keys are preserved.
- Search uses a 750ms debounce and `imePadding()` so the on-screen keyboard does not obscure results.
- Playlist/Artist detail rows show a speaker icon for the active track (not color-only), improving glanceability.
- MiniPlayer width is responsive (`fillMaxWidth(0.55f)` with `widthIn(max = 600.dp)`) for split-screen resilience and increased to 112dp height with 88dp album art and 52dp play/pause icon for better automotive visibility.
- Podcast episodes and audiobook chapters in Queue, Now Playing, and MiniPlayer use `SpotifyPlayableItem` with artwork fallback chain (item images → album/show/audiobook) and type-aware subtitle text.

## Runtime Resiliency

- Metadata sync now tracks offline conditions (`UnknownHostException`, `SocketTimeoutException`) and exposes `isOffline` in `SpotifyViewModel`.
- Spotify 429 penalties now trigger a global lockout persisted in `TokenManager`, surfaced through a top warning banner, and enforced by the API auth/interceptor stack so the app stops issuing outbound Spotify API requests until the lockout expires.
- `TokenManager` now resolves auth from the active Room-backed profile before interceptors and token refresh paths read credentials.
- The app displays an in-app top red banner: **"Offline Mode - Reconnecting..."** when connectivity drops.
- The app also surfaces Spotify auth/scope failures (HTTP 401/403) with a top banner and direct **Open** action into Setup.

## Request Budget Audit

- The heaviest request paths were audited before the lockout change.
- `syncPlaybackState()` was previously making both `GET /v1/me/player` and `GET /v1/me/tracks/contains` on every poll; saved-track status is now refreshed only when the current track changes.
- Home suggested-playlist loading previously forced a full playlist refresh even when playlists were already cached in memory; it now reuses loaded or cached playlists first.
- Recently played hydration now uses loaded/cached playlist and album metadata before falling back to per-item hydration requests.
- Podcast freshness checks remain a bounded hotspot because they intentionally inspect recent episodes per saved show, but concurrency is capped and the work is limited to a small per-show episode window.

## Testing

```bash
# Unit tests
./gradlew test


##Project Structure

app/src/main/kotlin/com/cloudbridge/spotify/
├── SpotifyCloudBridgeApp.kt          # Application — DI root
├── auth/                              # Token management, interceptors, Setup UI
├── data/                              # Repository layer (SpotifyLibraryRepository)
├── domain/                            # CustomMixEngine (Decade mixes, Daily Drive)
├── network/                           # Retrofit services & Moshi data models
├── player/                            # SpotifyPlaybackController & DeviceManager
├── receiver/                          # Bluetooth auto-launch capabilities
├── cache/                             # Room DB entities and DAOs
├── ui/
│   ├── MainActivity.kt               # Compose entry point (distractionOptimized)
│   ├── SpotifyViewModel.kt           # StateFlow + navigation orchestration
│   ├── AddProfileViewModel.kt        # QR polling orchestration
│   ├── theme/                         # Material3 dark theme, Typography, Colors
│   ├── screens/                       # Home, Library, NowPlaying, Queue, Settings, etc.
│   └── components/                    # MiniPlayer, AlbumArtTile, PlayerControls
└── util/                              # Sealed ApiResult wrappers
