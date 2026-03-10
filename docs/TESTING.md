# Testing Guide — AAOS Spotify Cloud-Bridge v2.0

## 1. Unit Tests

### Running Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run a specific test class
./gradlew testDebugUnitTest --tests "com.cloudbridge.spotify.auth.TokenManagerTest"

# Run with verbose output
./gradlew testDebugUnitTest --info
```

### Test Coverage

| Test Class | Module | What's Tested |
|-----------|--------|---------------|
| `TokenManagerTest` | auth | Active-profile selection, Room-backed credential lifecycle, token validity, Clean Swapper preference persistence, and rate-limit lockout persistence |
| `SpotifyAuthServiceTest` | network | Token refresh with Basic auth header, 401 error handling |
| `SpotifyApiServiceTest` | network | Playlist items, generic library save/check/remove endpoints, devices, playback, queue, and explicit-track/audiobook-chapter payload parsing |
| `DeviceManagerTest` | player | Smartphone priority selection, caching, refresh, error handling |
| `CustomMixEngineTest` | domain | Daily Drive / decade mix generation rules, ordering, and deduplication |

### Test Stack

- **JUnit 4** — test runner
- **MockK** — Kotlin mocking (coEvery/coVerify for coroutines)
- **Robolectric** — Android framework emulation (for DataStore, Context)
- **MockWebServer** — HTTP-level Retrofit testing
- **Turbine** — Flow assertion library (available for DataStore flow tests)
- **Coroutines Test** — `runTest`, `TestScope`, `backgroundScope`

### Build Config for Tests

The `app/build.gradle.kts` includes:
```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true   // Robolectric support
        isReturnDefaultValues = true       // android.util.Log returns 0 instead of throwing
    }
}
```

## 2. Emulator Testing Checklist

### Prerequisites

- [ ] AAOS emulator running (API 33+ x86_64 Automotive image)
- [ ] App installed (`adb install -r app/build/outputs/apk/debug/app-debug.apk`)
- [ ] Spotify running on phone (same account)
- [ ] Token has required scopes (see SETUP_GUIDE.md §3)

### Launch

```bash
adb shell am start --user 10 -n com.cloudbridge.spotify/.ui.MainActivity
```

### Manual Test Cases

#### TC-01: Home Screen Loads
1. Launch the app
2. **Expected**: Home screen shows grid tiles for Jump Back In, Suggested For You,
    Your Custom Mixes, Pinned Favorites (when present), and New Releases
3. **Expected**: Album artwork loads via Coil (may take a moment on first launch)

#### TC-01E: Global Rate-Limit Lockout Banner
1. Trigger or simulate a Spotify 429 response with a long `Retry-After` window
2. **Expected**: A top warning banner appears explaining that Spotify has rate limited the app and includes the remaining retry window
3. Try Search, Home refresh, opening Library detail screens, or transport actions during the lockout
4. **Expected**: The app does not silently keep sending Spotify API requests during the lockout window
5. Wait until the lockout expires and retry
6. **Expected**: Requests resume automatically without needing to restart the app

#### TC-01D: Custom Mix Generation and Daily Drive Configuration
1. Launch the app on Home with a valid Spotify connection
2. **Expected**: The **Your Custom Mixes** row appears with Daily Drive plus decade-mix tiles
3. Tap a decade mix tile such as **90s Mix**
4. **Expected**: Playback starts on the phone with a generated queue built from liked songs and Spotify recommendations
5. Open a podcast from **Library → Podcasts**
6. Tap **Set as News**
7. **Expected**: The button changes to **News Podcast** and remains selected when you revisit that show
8. Tap **Daily Drive** from Home
9. **Expected**: Playback starts with a generated queue that includes podcast episodes and music

#### TC-01C: Suggested Playlists Fallback
1. Launch the app with a valid Spotify connection
2. Stay on the **Home** screen until discovery sections load
3. **Expected**: The **Suggested For You** section renders playlist cards sourced from the user's playlist library
4. **Expected**: Logcat no longer loops on featured `HTTP 400` search failures during Home load

#### TC-01B: Podcast Freshness Badges
1. Launch the app with at least one followed podcast that has a newer episode than your last listened episode
2. Navigate to the **Your Podcasts** section on Home
3. **Expected**: The podcast tile shows a green `NEW` or `N NEW` badge in the top-right corner
4. **Expected**: The subtitle changes to `1 new episode · Publisher` or `N new episodes · Publisher`
5. **Expected**: Shows with no confidently detected new episodes do not show a badge

#### TC-02: NavigationRail Navigation
1. Tap the **Library** icon in the left NavigationRail
2. **Expected**: Library screen shows with Playlists/Albums/Artists/Podcasts/Audiobooks tabs
3. Tap **Queue** icon
4. **Expected**: Queue screen shows current track and upcoming items
5. Tap **Home** icon
6. **Expected**: Returns to home grid

#### TC-02B: Multi-Profile Switching
1. Open **Settings** with at least two stored profiles
2. Tap an inactive profile
3. **Expected**: The tapped profile becomes active immediately
4. **Expected**: Cached library content and pinned items are cleared, then repopulate for the newly active account
5. Visit **Home** and **Library**
6. **Expected**: Previously active-profile content is no longer visible

#### TC-02C: Add Profile QR Onboarding
1. Open **Settings → Add profile with QR code**
2. **Expected**: A large QR code, 6-character session code, and companion instructions are visible
3. Complete the companion phone/browser sign-in flow
4. **Expected**: The app returns to **Settings** automatically after relay completion
5. **Expected**: The new profile appears in the list and becomes active

#### TC-03: Play from Home Grid
1. Tap any tile on the Home screen
2. **Expected**: Playback starts on the phone
3. **Expected**: MiniPlayer appears at bottom-right with track info

#### TC-04: Library → Playlist Detail
1. Navigate to Library → Playlists tab
2. Tap a playlist
3. **Expected**: Playlist detail screen shows track list with numbers and durations
4. Tap "Play All" button
5. **Expected**: First track starts playing

#### TC-04E: Playlist Track Queue Button
1. Navigate to **Library → Playlists** and open any playlist
2. Tap the visible queue icon on an individual track row
3. **Expected**: The song is added to the phone's Spotify queue without requiring a long-press gesture

#### TC-04F: Audiobook Library and Chapter Detail
1. Open **Library → Audiobooks** with a Spotify account that has at least one saved audiobook
2. **Expected**: Audiobooks render in both grid and list modes with working filter/sort controls
3. Tap an audiobook
4. **Expected**: The audiobook detail screen shows chapter rows with chapter numbers, duration text, and progress bars for partially played chapters
5. Tap a chapter
6. **Expected**: Playback starts on the phone and Queue / Now Playing display the chapter metadata correctly

#### TC-04C: Newly Added Playlists Refresh
1. Add or follow a playlist on the Spotify account from another device
2. Return to the app and open **Library → Playlists**
3. **Expected**: Cached playlists appear immediately, then refresh from Spotify without needing to clear app data
4. **Expected**: The newly added playlist appears after the network refresh completes

#### TC-04B: Pinning in Grid and List Views
1. Open Library → Playlists, Albums, and Podcasts
2. In each tab, long-press one item in **grid view**
3. **Expected**: The item immediately shows a pin indicator and appears in **Pinned Favorites** on Home
4. Switch that same tab to **list view** and long-press a different item
5. **Expected**: List rows also toggle pinned state with a visible trailing pin indicator
6. **Expected**: Long-pressing the item again removes the pin
7. **Expected**: Liked Songs and track-only search results do not expose pinning

#### TC-04D: Library Sort and Filter
1. Open **Library → Playlists** and type part of a playlist name into the filter field
2. **Expected**: The list/grid narrows immediately without any loading spinner or network wait
3. Open the **Sort** menu and switch between **Recently Added**, **Alphabetical**, and **Creator**
4. **Expected**: The visible playlist order changes locally and remains smooth while scrolling
5. Repeat on **Albums**, **Artists**, and **Podcasts** using artist/genre/publisher text
6. **Expected**: Each tab filters against its own metadata and shows a clear empty state when nothing matches

#### TC-05: Now Playing Screen
1. While music is playing, tap the MiniPlayer
2. **Expected**: Now Playing screen slides up with blurred background
3. **Expected**: Hero album art, track title, artist name visible
4. **Expected**: Seek slider shows current position
5. Tap the collapse chevron
6. **Expected**: Now Playing slides down, MiniPlayer returns

#### TC-06: Playback Controls
1. On Now Playing screen, tap Pause
2. **Expected**: Playback pauses on phone, button changes to Play
3. Tap Play
4. **Expected**: Playback resumes
5. Tap Next
6. **Expected**: Next track starts, artwork and info update
7. Tap Previous
8. **Expected**: Previous track resumes

#### TC-07: Shuffle & Repeat
1. On Now Playing screen, tap Shuffle icon
2. **Expected**: Icon turns Spotify green, shuffle enabled on phone
3. Tap Repeat icon
4. **Expected**: Cycles through Off → Context → Track (icon changes)

#### TC-08: Queue Management
1. Navigate to Queue screen
2. **Expected**: Currently playing track highlighted in green
3. **Expected**: "Up Next" section shows upcoming tracks
4. Swipe a queued track to the left
5. **Expected**: Track removed from queue (SwipeToDismiss animation)
6. **Expected**: Requires a deliberate long horizontal drag (~50% width); diagonal scroll gestures do not dismiss

#### TC-13: Library Tab Persistence
1. Navigate to Library and select **Podcasts** (or any non-default tab)
2. Open a podcast/detail screen from that tab
3. Tap Back
4. **Expected**: Library returns to the same previously selected tab (no reset to Playlists)

#### TC-14: Search Keyboard & Debounce
1. Open Search and type slowly (one character at a time)
2. **Expected**: Requests fire after a longer delay (750ms debounce), reducing per-keystroke calls
3. Open the on-screen keyboard and scroll results
4. **Expected**: Results remain reachable/scrollable with keyboard visible (`imePadding` applied)
5. Long-press an album result
6. **Expected**: It pins/unpins successfully and reflects the pin indicator immediately
7. **Expected**: Long-pressing a track or playlist result does not add it to Custom Mixes

#### TC-15: Active Track Accessibility
1. Open Playlist Detail or Artist Detail while a track is playing
2. **Expected**: Active row shows a speaker icon next to track number area
3. **Expected**: Active-state is identifiable even without relying on color alone

#### TC-16: MiniPlayer Responsiveness
1. Enter split-screen or reduced-width window mode (if OEM/emulator supports it)
2. **Expected**: MiniPlayer shrinks proportionally and does not clip offscreen
3. **Expected**: On very wide layouts, width caps cleanly (no over-elongated pill)

#### TC-09: Heart / Save Track
1. On Now Playing screen, tap the heart icon
2. **Expected**: Heart fills green, track saved to library
3. Tap again
4. **Expected**: Heart unfills, track removed from library
5. **Note**: Requires `user-library-modify` scope — will 403 without it

#### TC-10: Token Auto-Refresh
1. Wait for access token to expire (~1 hour) or manually clear it
2. Perform any action (browse, play)
3. **Expected**: New access token is obtained automatically (check logcat)
4. **Expected**: Action succeeds without user intervention

#### TC-11: No Spotify Device Available
1. Close Spotify on all devices
2. Try to play a track
3. **Expected**: Graceful failure, error logged (check `DeviceManager` tag)

#### TC-12: Rate Limiting Recovery
1. Rapidly tap play/pause many times
2. **Expected**: If 429 occurs, app waits and retries automatically
3. **Expected**: Check logcat for `RateLimitRetryInterceptor` messages

#### TC-18: Navigation & MiniPlayer UI Fixes
1. Start on any screen (Home, Library, Search, etc.)
2. Tap the same NavigationRail tab multiple times
3. **Expected**: No back-stack accumulation — tapping back should go to previous screen, not infinitely loop
4. Navigate between different tabs (Home → Library → Queue → Search)
5. **Expected**: Each tab switch clears back-stack — back navigation works correctly
6. While playing music, check the MiniPlayer at bottom-right
7. **Expected**: MiniPlayer is larger (112dp height vs 96dp, 88dp album art vs 72dp)
8. **Expected**: Play/pause button is larger (52dp icon vs 40dp, 80dp touch target vs 64dp)
9. **Expected**: Text uses larger typography (titleLarge/bodyLarge vs titleMedium/bodyMedium)
10. From Now Playing screen, tap "View Queue" text
11. **Expected**: Navigates to Queue screen without adding to back-stack (top-level navigation)
12. **Expected**: Back navigation from Queue returns to Now Playing (not infinitely deep)

### Logcat Monitoring

Open a terminal alongside the emulator:

```bash
# All app logs
adb logcat | grep -E "SpotifyViewModel|SpotifyPlayback|DeviceManager|TokenRefresh"

# Just playback flow
adb logcat -s SpotifyPlaybackController:V DeviceManager:V

# Network issues
adb logcat -s RetrofitProvider:V TokenRefreshAuth:V
```

## 3. Integration Test Strategy (Future)

For full end-to-end integration tests:

1. **Mock Spotify API** with a local MockWebServer running on the emulator
2. **Override RetrofitProvider** base URLs via build config
3. **Compose UI tests** with `createAndroidComposeRule<MainActivity>()`

```kotlin
// Example: Compose UI test
@get:Rule
val composeRule = createAndroidComposeRule<MainActivity>()

@Test
fun homeScreen_showsNavigationRail() {
    composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Library").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Queue").assertIsDisplayed()
}
```

## 4. CI/CD Considerations

```yaml
# Example GitHub Actions step
- name: Run unit tests
  run: ./gradlew testDebugUnitTest

# AAOS emulator tests require hardware acceleration
# Consider using Firebase Test Lab with automotive device profiles
```

Key points:
- Unit tests run without an emulator (Robolectric + MockK + MockWebServer)
- `isReturnDefaultValues = true` allows `android.util.Log` calls in non-Robolectric tests
- MockWebServer tests validate network serialization without hitting Spotify
- Emulator tests require manual execution (AAOS emulators not well-supported in CI)
- Token/credential tests use temp DataStore files via `backgroundScope` (no shared state)
- Token/profile tests use a temp DataStore plus a fake `UserProfileDao`, so the active-profile model is covered without a Room-backed emulator database

## 5. Screenshot Capture (Emulator Evidence)

Use this command pattern after navigating to each target screen:

```bash
adb exec-out screencap -p > screenshots/ux-<case>.png
```

Recommended captures for this UX pass:
- `screenshots/ux-library-tab-persist.png`
- `screenshots/ux-search-ime-padding.png`
- `screenshots/ux-playlist-now-playing-icon.png`
- `screenshots/ux-artist-now-playing-icon.png`
- `screenshots/ux-queue-swipe-threshold.png`
- `screenshots/ux-mini-player-responsive.png`
- `screenshots/ux-podcast-queue.png`
- `screenshots/ux-podcast-now-playing.png`
- `screenshots/ux-podcast-miniplayer.png`
