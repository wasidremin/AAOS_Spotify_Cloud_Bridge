# Architecture — AAOS Spotify Cloud-Bridge

> **Version 2.0** — Custom Jetpack Compose UI replacing native AAOS media templates.

## 1. High-Level Overview

```
┌──────────────────────────────────────────────────────────────┐
│                 AAOS Head-Unit (Emulator)                     │
│                                                              │
│  ┌──────────┬──────────────────────────────────────────────┐ │
│  │ Nav Rail │            Compose UI                        │ │
│  │          │  ┌──────────────────────────────────────────┐│ │
│  │ Home     │  │    HomeScreen (LazyVerticalGrid)         ││ │
│  │ Search   │  │    SearchScreen (query + result grid)     ││ │
│  │ Library  │  │    LibraryScreen (Playlists/Albums/      ││ │
│  │          │  │                  Artists/Pods/Books)      ││ │
│  │ Queue    │  │    NowPlayingScreen (blur + controls)    ││ │
│  │ Settings │  │    QueueScreen (swipe-to-dismiss)        ││ │
│  │          │  └──────────────────────────────────────────┘│ │
│  │          │                     ┌──────────────┐         │ │
│  │          │                     │  MiniPlayer  │         │ │
│  └──────────┴─────────────────────┴──────────────┴─────────┘ │
│                           │                                  │
│           SpotifyViewModel (state + navigation)              │
│                           │                                  │
│     SpotifyLibraryRepository + CustomMixEngine               │
│                           │                                  │
│         SpotifyPlaybackController + DeviceManager            │
│                           │                                  │
│              Retrofit + OkHttp (AuthInterceptor)             │
│                           │                                  │
│  ┌────────────────────────▼──────────────────────────────┐   │
│  │              Spotify Web API (HTTPS)                   │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│           Audio arrives via Bluetooth A2DP                    │
│              from user's phone                               │
└──────────────────────────────────────────────────────────────┘
```

The app **never plays audio locally**. All playback commands are relayed to the
Spotify Web API, which controls playback on the user's phone. The phone's audio
output reaches the car speakers over Bluetooth A2DP.

The app also deliberately avoids registering as a `MediaLibraryService`. On AAOS,
doing so can steal physical audio routing from the native Bluetooth source,
muting the phone path that this project depends on.

### Why Custom UI instead of MediaLibraryService?

| Problem with Templates | Solution |
|------------------------|----------|
| UXR scrolling limits (max items shown) | `distractionOptimized` Activity bypasses UXR |
| Rigid grid/list layouts | Full Compose freedom — large tiles, blur effects |
| Read-only queues | SwipeToDismissBox for queue management |
| No now-playing customization | Blurred background, hero art, fat slider |
| Bluetooth route can be hijacked by app-owned media sessions | Keep phone as native Bluetooth source |

Home podcast freshness is derived by fetching a small slice of each saved show's latest episodes and comparing them to the first episode with resume history. The UI only badges shows when the app can confidently infer that newer episodes exist above the listener's most recently played episode.

Pinning is intentionally limited to reusable library objects (`playlist`, `album`, and `show`). Tracks are excluded so the Home surface stays focused on durable destinations rather than one-off songs.

## 2. Package Layout

```
com.cloudbridge.spotify
├── SpotifyCloudBridgeApp.kt            # Application — manual DI root
├── auth/
│   ├── TokenManager.kt                 # Active-profile preference + Room-backed credential lookup
│   ├── AuthInterceptor.kt              # OkHttp interceptor — Bearer header
│   ├── TokenRefreshAuthenticator.kt     # OkHttp authenticator — auto-refresh
│   └── SetupActivity.kt                # Legacy credential entry UI (XML)
├── cache/
│   └── CacheDatabase.kt                # Room cache + user profile entities/DAO
├── data/
│   └── SpotifyLibraryRepository.kt     # Repository for Spotify library paging + cache sync
├── domain/
│   └── CustomMixEngine.kt              # Daily Drive / decade queue generation rules
├── network/
│   ├── model/
│   │   ├── AuthModels.kt               # TokenResponse
│   │   ├── CloudRelayModels.kt         # CloudSessionPayload for QR onboarding
│   │   └── SpotifyModels.kt            # All Spotify data classes + QueueResponse
│   ├── CloudRelayService.kt            # Retrofit — cloud relay polling
│   ├── SpotifyAuthService.kt           # Retrofit — accounts.spotify.com
│   ├── SpotifyApiService.kt            # Retrofit — api.spotify.com (+ queue/save endpoints)
│   └── RetrofitProvider.kt             # Dual Retrofit instances + retry
├── player/
│   ├── DeviceManager.kt                # Device discovery with priority
│   └── SpotifyPlaybackController.kt     # Playback command wrapper + retry
├── ui/
│   ├── MainActivity.kt                 # Compose entry point (distractionOptimized)
│   ├── AddProfileViewModel.kt          # QR onboarding session/polling state
│   ├── SpotifyViewModel.kt             # UI state, navigation, and orchestration only
│   │                                  # delegates paging and mix generation to data/domain layers
│   ├── theme/
│   │   ├── Color.kt                    # Spotify brand palette
│   │   ├── Type.kt                     # Automotive-optimized typography
│   │   └── Theme.kt                    # Material3 dark color scheme
│   ├── screens/
│   │   ├── AddProfileScreen.kt         # Smart-TV-style QR onboarding screen
│   │   ├── HomeScreen.kt               # LazyVerticalGrid with album art tiles + podcast freshness badges
│   │   ├── LibraryScreen.kt            # Playlists/Albums/Artists/Podcasts/Audiobooks tabs with grid/list + pinning
│   │   ├── ManagePinsScreen.kt         # Pinned favorites reordering UI
│   │   ├── PlaylistDetailScreen.kt     # Track list with Play All button
│   │   ├── NowPlayingScreen.kt         # Blurred bg + hero art + controls
│   │   └── QueueScreen.kt              # Swipe-to-dismiss queue
│   └── components/
│       ├── MiniPlayer.kt               # Floating pill-shaped mini player
│       ├── AlbumArtTile.kt             # Grid tile with gradient overlay
│       └── PlayerControls.kt           # Shuffle/prev/play-pause/next/repeat/heart
└── util/
    └── ApiResult.kt                    # Sealed result wrapper
```

## 3. Key Components

### 3.1 MainActivity (distractionOptimized)

The entry point for the Compose UI. Declared in the manifest with:
```xml
<meta-data android:name="distractionOptimized" android:value="true" />
```

This flag tells AAOS to allow full interactivity while the vehicle is in motion,
bypassing the default UXR restrictions that limit scrolling and touch input.

The Activity creates a `SpotifyViewModel` using a `ViewModelProvider.Factory` for
proper lifecycle scoping (survives configuration changes). The root layout applies
`Modifier.systemBarsPadding()` to avoid overlap with automotive system bars
(status bar, climate stripe). A `Scaffold` manages the MiniPlayer as a
`bottomBar`, eliminating hardcoded spacing hacks in child screens.

`BackHandler` is used to intercept hardware/system back. Back closes Now Playing
first, then pops the ViewModel screen history stack.

### 3.2 SpotifyViewModel

The ViewModel bridge that replaces the old `SpotifyBrowseTree`. It remains the UI-facing
orchestrator, but library paging/cache sync now lives in `SpotifyLibraryRepository` and
custom mix generation now lives in `CustomMixEngine`.

Primary `StateFlow`s exposed to Compose:

| StateFlow | Source | Purpose |
|-----------|--------|---------|
| `recentContexts` | `GET /v1/me/player/recently-played` + hydrate via `GET /v1/playlists/{id}` / `GET /v1/albums/{id}` | Home "Jump Back In" |
| `customMixes` | Static ViewModel list + `CustomMixEngine` playback generation | Home custom mix tiles |
| `featuredPlaylists` | Curated `GET /v1/me/playlists` selection with cache/pin/recent exclusions | Home screen tiles |
| `topTracks` | `GET /v1/me/top/tracks` | Home screen tiles |
| `newReleases` | `GET /v1/me/top/artists` → `GET /v1/artists/{id}/albums` | Home "New Releases from your artists" tiles |
| `searchResults` | `GET /v1/search` | Search screen result grid |
| `searchQuery` | UI text input | Search state |
| `isSearchLoading` | Internal | Search spinner |
| `userProfiles` | Room `user_profiles` + active profile ID in DataStore | Settings profile management |
| `explicitFilterEnabled` | DataStore preference | Clean Swapper playback policy |
| `playlists` | `GET /v1/me/playlists` (paginated) | Library tab |
| `savedAlbums` | `GET /v1/me/albums` (paginated) | Library tab |
| `savedShows` | `GET /v1/me/shows` (paginated) | Library tab + Home podcast cards |
| `savedAudiobooks` | `GET /v1/me/audiobooks` (paginated) | Library audiobook tab |
| `playbackState` | `GET /v1/me/player` (3s poll) | Now Playing + MiniPlayer |
| `isOffline` | Metadata sync exception handling | Offline banner state |
| `requiresReauth` | API 401/403 detection | Reconnect banner state |
| `isTrackSaved` | `GET /v1/me/library/contains` | Heart button state |
| `queue` | `GET /v1/me/player/queue` | Queue screen |
| `detailTracks` | `GET /v1/playlists/{id}/items` + `GET /v1/albums/{id}/tracks` | Playlist/album detail screen |
| `detailChapters` | `GET /v1/audiobooks/{id}/chapters` | Audiobook detail screen |
| `isHomeLoading` | Internal | Home section spinner |
| `isLibraryLoading` | Internal | Library section spinner |

**Refactor rationale**:
- Keep Compose screens thin and driven by `StateFlow`.
- Keep the ViewModel focused on UI orchestration and lifecycle.
- Move paging/caching concerns behind a repository boundary.
- Move queue-generation rules into a testable domain component.
| `isDetailLoading` | Internal | Detail section spinner |

**Progressive pagination**: Library content (playlists, albums, shows) and
detail tracks use progressive loading — each page is appended to the
StateFlow immediately, so the UI renders the first batch while subsequent
pages load in the background.

**Metadata sync loop**: Polls `GET /v1/me/player` every 3 seconds to keep
the Now Playing UI in sync (moved from the deleted CloudBridgePlayer).
Network exceptions (`UnknownHostException`, `SocketTimeoutException`) toggle
`isOffline`, which drives a top-of-screen reconnect banner.
HTTP 401/403 errors from content endpoints set `requiresReauth`, which shows a
user-facing banner with a direct link to Setup.

**Global rate-limit lockout policy**: Long-running Spotify 429 penalties are persisted in `TokenManager` as an absolute lockout-until timestamp. `AuthInterceptor` consults that shared state before every API request and throws a typed lockout exception while the penalty is active, which prevents every screen and background loop from blindly re-hitting Spotify during the cooldown window.

**Multi-profile policy (Phase 1)**: DataStore now stores only the active profile pointer plus app preferences. Spotify credentials live in Room `user_profiles`, and `TokenManager` resolves client ID, refresh token, access token, and expiry from the currently active profile for interceptors, authenticators, and Settings.

**QR onboarding policy (Phase 1)**: `AddProfileViewModel` generates a 6-character session code, renders a QR URL, polls the cloud relay every 3 seconds, inserts a new `UserProfile`, switches it active, and deletes the relay payload after a successful transfer.

**Profile isolation policy (temporary)**: Library cache rows and pinned items remain globally keyed in Phase 1, so switching profiles clears those Room tables and resets in-memory UI state before the new account reloads.

**Liked Songs bridge**: Spotify does not expose "Liked Songs" as a playlist.
The app injects a synthetic tile in Library (`id = "liked-songs"`) and routes it
to `GET /v1/me/tracks`, while reusing the existing `PlaylistDetailScreen`.

**Library refresh policy**: `loadLibrary()` no longer short-circuits once playlists are non-empty. The UI still preloads cached content immediately, but every Library entry triggers a background Spotify refresh so new playlists and saved media show up reliably.

**Library sort/filter policy**: Library tabs perform filtering and sort-order changes entirely in Compose against the already-loaded in-memory lists. This keeps the feature responsive in-car and avoids extra Spotify API traffic while driving. Audiobooks sort by recently added, alphabetical title, or author.

**Custom mix generation policy**: Because Spotify-owned personalized playlists are no longer reliably accessible for this app, Home exposes a fixed set of generated mixes instead of cached Spotify playlist IDs. Decade mixes page liked songs from `GET /v1/me/tracks`, filter them by album release decade, seed up to five liked tracks into `GET /v1/recommendations`, and interleave owned plus recommended tracks in a 2:1 pattern before calling `SpotifyPlaybackController.play(uris = ...)`.

**Daily Drive policy**: Daily Drive persists a preferred news show ID in DataStore, pulls the latest episode from that show plus a second saved podcast, then combines those episodes with liked songs and recommendation tracks into a single URI queue for remote playback on the phone.

**Clean Swapper policy**: When the explicit filter is enabled, direct URI playback paths resolve explicit tracks through `SpotifyLibraryRepository`. The repository checks Room for a cached explicit-to-clean mapping, falls back to a `GET /v1/search?type=track&limit=10` lookup when needed, and stores the replacement URI so later plays avoid repeat searches.

**Playable metadata policy**: `SpotifyPlayableItem` now models Spotify `track`, `episode`, and `chapter` payloads so Queue, MiniPlayer, and Now Playing can render podcast and audiobook content without type-cast assumptions. Playlist detail loading now consumes Spotify's February 2026 `items.item` payload and maps track rows from that generic item container.

**High-volume request audit**: The worst offenders were (1) metadata polling plus per-poll saved-track checks, (2) Home playlist suggestion loading forcing a full playlist refresh, and (3) recently played context hydration fanning out into per-item playlist/album fetches. The current architecture now caches or reuses in-memory results for those flows first, while leaving the podcast-freshness fan-out deliberately bounded because it powers a visible Home badge feature.

### 3.3 Automotive UI Scaling

- **Home grid**: `GridCells.Fixed(4)` for stable tile math in constrained OEM app windows.
- **Jump Back In hydration**: recently played track contexts are resolved into canonical playlist/album cards.
- **Pinning model**: long-press toggles durable pins for playlists, albums, and podcasts across Home, Library, and Search; pinned state is reflected directly on cards/rows.
- **Custom mix tiles**: Home renders generated Daily Drive / decade mixes as square tiles with branded color fills and icon artwork when no remote album image exists.
- **Greeting + safety toggle**: Home renders a time-of-day greeting card with an always-visible Clean Swapper switch for family-safe playback.
- **Tile geometry**: `AlbumArtTile` enforces square tiles (`aspectRatio(1f)`) with `ContentScale.Crop`.
- **List touch targets**: detail rows use 64dp artwork with larger row padding, explicit badges, and current-track highlighting.
- **Library tabs**: playlists/albums/artists/podcasts/audiobooks can switch between 4-column grids and large list rows while preserving long-press pinning, and all Library tabs expose local sort/filter controls above the content.
- **Insets**: Screen lists/grids consume `Scaffold` `innerPadding` to keep content above MiniPlayer.

### 3.4 OEM Window Constraints

On some AAOS OEM builds (for example GM), media apps run in a bounded content
region while the right side of the panel is reserved for system widgets. A
right-side dark/system area is expected platform behavior.

### 3.5 Auth Strategy

```
┌───────────────┐  manual entry / QR   ┌──────────────┐   activeProfileId   ┌──────────────┐
│ SetupActivity │ ───────────────────► │ TokenManager │ ───────────────────► │ UserProfile  │
│ AddProfile UI │                      │ (DataStore)  │                      │ Room table   │
└───────────────┘                      └──────┬───────┘                      └──────┬───────┘
                                             │                                     │
                 auto-refresh on 401         │                                     │
┌──────────────┐ ◄────────────────────────────┴─────────────────────────────────────┘
│ TokenRefresh │ ──► POST /api/token (accounts.spotify.com)
│ Authenticator│ ──► Updates active profile access_token
└──────────────┘
```

- **No OAuth flow** — user obtains a refresh token externally (see SETUP_GUIDE)
- **Active profile ID** stored in DataStore preferences
- **Per-profile Spotify credentials** stored in Room `user_profiles`
- **OkHttp Authenticator** transparently refreshes on 401
- **Mutex** prevents concurrent refresh storms

### 3.6 Network Layer

Two Retrofit instances share a Moshi JSON parser (with KSP codegen):

| Instance | Base URL | Auth | Purpose |
|----------|----------|------|---------|
| `authRetrofit` | `accounts.spotify.com` | Basic (client_id:client_secret) | Token refresh |
| `apiRetrofit` | `api.spotify.com` | `AuthInterceptor` + `TokenRefreshAuthenticator` | All API calls |
| `cloudRelayRetrofit` | relay base URL | none | Add Profile session polling and cleanup |

A `RateLimitRetryInterceptor` handles HTTP 429 responses, respecting the
`Retry-After` header (max 3 retries, max 10s wait).

### 3.7 DeviceManager

Discovers the user's Spotify Connect devices and picks the best target:

1. Active smartphone (highest priority)
2. Any smartphone
3. Any active device
4. `null` (triggers error)

Cache TTL: 2 minutes. The `SpotifyPlaybackController` calls `refreshDeviceId()`
on 404 errors to recover from stale device IDs.

## 4. Data Flow — Play Request

```
1. User taps track tile in Compose UI
   │
2. HomeScreen → viewModel.playTrack(trackUri, contextUri)
   │
3. SpotifyViewModel launches coroutine:
   │  ├─ SpotifyPlaybackController.play(trackUri, contextUri)
   │  │   ├─ DeviceManager.getPhoneDeviceId() → "device_123"
   │  │   └─ SpotifyApiService.play(deviceId, body)
   │  │       ├─ AuthInterceptor adds Bearer header
   │  │       └─ On 401 → TokenRefreshAuthenticator refreshes token
   │  │
   │  └─ On success → syncPlaybackState() → update StateFlows
   │
4. Audio plays on phone → Bluetooth A2DP → car speakers
5. MiniPlayer + NowPlayingScreen observe playbackState StateFlow
```

## 5. Threading Model

| Thread / Dispatcher | Used By |
|---------------------|---------|
| `Dispatchers.Main` | Compose recomposition, StateFlow collection |
| `Dispatchers.IO` | All network calls (Retrofit suspend functions) |
| OkHttp thread pool | Interceptors, Authenticator (uses `runBlocking`) |
| `viewModelScope` | ViewModel coroutines (metadata sync, data loading) |

## 6. Error Handling

| Error | Handling |
|-------|----------|
| 401 Unauthorized | OkHttp Authenticator auto-refreshes token |
| 404 Device Not Found | Retry with refreshed device ID (1 attempt) |
| 429 Rate Limited | Wait `Retry-After` seconds, retry (max 3) |
| Network failure | Caught per-flow, logged, UI shows empty state |
| No devices available | `DeviceManager` returns null, play fails gracefully |

## 7. Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.12.01 | UI framework (Material3) |
| Coil | 2.7.0 | AsyncImage loading from Spotify CDN |
| Navigation Compose | 2.8.5 | (available, using manual nav via StateFlow) |
| Lifecycle ViewModel | 2.8.7 | ViewModel + Compose integration |
| Retrofit | 2.11.0 | HTTP client for Spotify Web API |
| OkHttp | 4.12.0 | HTTP transport + interceptors |
| Moshi | 1.15.1 | JSON serialization (KSP codegen) |
| Kotlin Coroutines | 1.9.0 | Async programming |
| DataStore Preferences | 1.1.1 | Token storage |
| ZXing Core | 3.5.3 | QR-code generation |

## 8. Required Spotify Scopes

```
playlist-read-private
playlist-read-collaborative
user-modify-playback-state
user-library-read
user-library-modify          ← NEW in v2.0 (heart/save button)
user-read-playback-state
user-read-currently-playing
user-read-email
user-read-recently-played
user-read-private
user-top-read
```

> **Note**: `user-library-modify` was added in v2.0 for the save/unsave track
> feature. Existing refresh tokens will need to be re-authorized with this scope.

## 9. Future Enhancements

- [x] Queue management (display + swipe-to-dismiss)
- [x] Seek bar support (fat progress slider)
- [x] Shuffle/repeat toggle
- [x] Heart/save track button
- [x] Search support (Spotify Search API)
- [ ] Drag-to-reorder queue
- [ ] Multiple account support
- [ ] WebSocket / SSE for real-time playback updates
