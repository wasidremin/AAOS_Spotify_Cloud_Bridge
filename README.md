# AAOS Spotify Cloud-Bridge

An Android Automotive OS (AAOS) media app that provides a fully custom Jetpack Compose UI for Spotify browsing and playback control — without playing any audio locally.

## The Concept

This app provides a pixel-perfect, CarPlay-inspired music interface for your car's infotainment system. Unlike the native AAOS media templates (which have rigid layouts and UXR scrolling limits), this uses a custom `distractionOptimized` Activity to deliver a premium automotive experience:

1. **Browsing**: A large-tile grid home screen with recently played, top tracks, featured playlists, and new releases — all fetched via the **Spotify Web API**
2. **Playback**: When you tap play, a **REST API command** targets Spotify on your phone as the playback device
3. **Audio**: Your phone plays the audio → Bluetooth A2DP → car speakers

The app intentionally does not register as an AAOS media source. Native Bluetooth remains the active audio route so phone playback, steering wheel controls, and speaker output stay stable.

This "cloud-bridge" pattern bypasses the need for Android Auto or Apple CarPlay while giving you full control over the car's UI.

## Quick Start

1. Clone the repo and open in Android Studio
2. Set up an AAOS emulator (API 33+ Automotive image)
3. Create a [Spotify Developer App](https://developer.spotify.com/dashboard) and obtain a refresh token
4. Build and install: `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk`
5. Enter your Client ID + Refresh Token in Settings
6. Launch "Spotify Cloud-Bridge" from the AAOS app launcher

See [docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md) for detailed instructions including how to obtain a refresh token.

For in-car release testing, generate the signed Android App Bundle with `./gradlew bundleRelease`. The artifact is written to [app/build/outputs/bundle/release/app-release.aab](app/build/outputs/bundle/release/app-release.aab). If you need a directly sideloadable binary for the car, build `./gradlew assembleRelease` and use [app/build/outputs/apk/release/app-release.apk](app/build/outputs/apk/release/app-release.apk).

For Windows, the repo now includes an automated refresh-token helper at [scripts/refresh-spotify-token.ps1](scripts/refresh-spotify-token.ps1). Fill in [scripts/spotify-oauth.config.xml](scripts/spotify-oauth.config.xml), run the script, approve Spotify in the browser, and it will update [app/src/main/kotlin/com/cloudbridge/spotify/auth/TokenManager.kt](app/src/main/kotlin/com/cloudbridge/spotify/auth/TokenManager.kt).

## Architecture

```
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
```

## Refactor Review Notes

- `SpotifyViewModel` had become a catch-all for navigation, paging, cache writes, and custom mix generation.
- `SpotifyLibraryRepository` now owns Spotify library paging and Room cache synchronization.
- `CustomMixEngine` now owns Daily Drive and decade mix queue construction.
- `SpotifyViewModel` remains the UI orchestrator, which keeps screen-facing state separate from domain rules.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full architecture document.

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
| Storage | Jetpack DataStore Preferences |
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
- Library and detail lists use larger touch targets (72dp and 64dp artwork rows) for safer in-car interaction.
- Library Playlists, Albums, and Podcasts can each switch between large-tile grids and roomy list rows without losing long-press pinning.
- Library always refreshes playlists/albums/podcasts from Spotify when opened, while still seeding from cache first so newly added playlists appear without requiring an app reinstall.
- Library Playlists always shows an official "Liked Songs" tile first, backed by `GET /v1/me/tracks`.
- Library tab selection persists via `SpotifyViewModel.libraryTab`, so returning from detail screens keeps context.
- Library tabs now support in-memory filtering plus local sort modes (for example alphabetical, creator/publisher, and recently added) without hitting Spotify again.
- Settings now shows all stored Spotify profiles and provides an **Add profile with QR code** entry point.
- Settings now includes a **Home screen order** editor so users can rearrange sections like Jump Back In, Podcasts, and New Releases.
- Phase 1 multi-profile auth stores only the active profile ID in DataStore; each profile's Spotify credentials now live in Room as `user_profiles` rows.
- The very first profile is now forced through QR onboarding before the rest of the app can be used.
- QR onboarding generates a one-time cloud-relay session code, opens the GitHub Pages companion at https://wasidremin.github.io/AAOS_Spotify_Cloud_Bridge/, polls for the relay payload, then switches the new profile active automatically.
- The Add Profile relay now targets the Firebase Realtime Database endpoint `https://aaosspotiftycloudbridge-default-rtdb.firebaseio.com/`.
- Upgrades from the older single-profile build migrate any legacy DataStore credentials into the new Room-backed profile store on startup so saved keys are preserved.
- Daily Drive now leads with a podcast, follows with a shorter music block, then returns to podcast content sooner.
- Profile switches currently clear cached library rows and pinned items before reload so one account's content cannot bleed into another account's UI.
- Search uses a 750ms debounce and `imePadding()` so the on-screen keyboard does not obscure results.
- Playlist/Artist detail rows show a speaker icon for the active track (not color-only), improving glanceability.
- MiniPlayer width is responsive (`fillMaxWidth(0.55f)` with `widthIn(max = 600.dp)`) for split-screen resilience and increased to 112dp height with 88dp album art and 52dp play/pause icon for better automotive visibility.
- Podcast episodes and audiobook chapters in Queue, Now Playing, and MiniPlayer use `SpotifyPlayableItem` with artwork fallback chain (item images → album/show/audiobook) and type-aware subtitle text.
- Playlist, album, and artist surfaces now expose Start Radio actions powered by `GET /v1/recommendations` seeds from the current context.

## Runtime Resiliency

- Metadata sync now tracks offline conditions (`UnknownHostException`, `SocketTimeoutException`) and exposes `isOffline` in `SpotifyViewModel`.
- Spotify 429 penalties now trigger a global lockout persisted in `TokenManager`, surfaced through a top warning banner, and enforced by the API auth/interceptor stack so the app stops issuing outbound Spotify API requests until the lockout expires.
- `TokenManager` now resolves auth from the active Room-backed profile before interceptors and token refresh paths read credentials.
- The app displays an in-app top red banner: **"Offline Mode - Reconnecting..."** when connectivity drops.
- The app also surfaces Spotify auth/scope failures (HTTP 401/403) with a top banner and direct **Open** action into Setup.
- Search and home hydration degrade gracefully to empty-state UI when API calls fail (no app crash).

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
```

See [docs/TESTING.md](docs/TESTING.md) for the full testing guide.

## Project Structure

```
app/src/main/kotlin/com/cloudbridge/spotify/
├── SpotifyCloudBridgeApp.kt          # Application — DI root
├── auth/                              # Token management & credential entry
├── data/                              # Repository layer for Spotify library/cache access
├── domain/                            # Custom mix generation and other domain logic
├── network/                           # Retrofit services & data models
├── player/                            # Playback controller & device management
├── ui/
│   ├── MainActivity.kt               # Compose entry point (distractionOptimized)
│   ├── SpotifyViewModel.kt           # UI state + navigation orchestration
│   ├── theme/                         # Material3 dark theme, Spotify colors
│   ├── screens/                       # Home, Library, NowPlaying, Queue, etc.
│   └── components/                    # MiniPlayer, AlbumArtTile, PlayerControls
└── util/                              # Shared utilities
```

## License

Private project — not for redistribution.
