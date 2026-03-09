package com.cloudbridge.spotify.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.cloudbridge.spotify.SpotifyCloudBridgeApp
import com.cloudbridge.spotify.ui.components.MiniPlayer
import com.cloudbridge.spotify.ui.screens.*
import com.cloudbridge.spotify.ui.theme.CloudBridgeTheme
import com.cloudbridge.spotify.ui.theme.ErrorRed
import com.cloudbridge.spotify.ui.theme.SpotifyBlack
import com.cloudbridge.spotify.ui.theme.SpotifyDarkSurface
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyWhite
import com.cloudbridge.spotify.ui.theme.WarningOrange

/**
 * Primary entry point for the custom Compose UI on AAOS.
 *
 * **`distractionOptimized` bypass**: This Activity is declared with
 * `<meta-data android:name="distractionOptimized" android:value="true" />`
 * in the AndroidManifest. This tells the Android Automotive OS to allow
 * full interactivity (scrolling, text input, unlimited list lengths)
 * while the vehicle is in motion. Without this flag, the OS would
 * impose strict UXR (User Experience Restrictions) that limit scroll
 * depth, disable text fields, and simplify layouts — defeating the
 * purpose of a custom Compose UI.
 *
 * The ViewModel is created via [ViewModelProvider] with a custom
 * [SpotifyViewModel.Factory] so it survives configuration changes
 * (e.g., day/night theme switch when headlights turn on).
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SpotifyViewModel
    private lateinit var addProfileViewModel: AddProfileViewModel

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            viewModel.navigateTo(SpotifyViewModel.Screen.Search)
            viewModel.updateSearchQuery(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: transparent system bars, content draws behind them
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val app = application as SpotifyCloudBridgeApp

        /* Use ViewModelProvider with a factory so the ViewModel
           survives configuration changes automatically. */
        viewModel = ViewModelProvider(
            this,
            SpotifyViewModel.Factory(
                api = app.retrofitProvider.spotifyApi,
                playbackController = app.playbackController,
                deviceManager = app.deviceManager,
                tokenManager = app.tokenManager,
                cacheDb = app.cacheDatabase,
                libraryRepository = app.libraryRepository,
                customMixEngine = app.customMixEngine,
                context = app.applicationContext
            )
        )[SpotifyViewModel::class.java]

        addProfileViewModel = ViewModelProvider(
            this,
            AddProfileViewModel.Factory(
                cloudRelayService = app.retrofitProvider.cloudRelay,
                userProfileDao = app.cacheDatabase.userProfileDao(),
                tokenManager = app.tokenManager
            )
        )[AddProfileViewModel::class.java]

        setContent {
            CloudBridgeTheme {
                CloudBridgeApp(
                    viewModel = viewModel,
                    addProfileViewModel = addProfileViewModel,
                    onLaunchVoiceSearch = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        voiceSearchLauncher.launch(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPlaybackStateNow()
        }
    }
}

// ── Top-Level App Layout ─────────────────────────────────────────────

private enum class NavItem(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Library("Library", Icons.Filled.LibraryMusic),
    Queue("Queue", Icons.AutoMirrored.Filled.QueueMusic),
    Settings("Settings", Icons.Filled.Settings),
    NowPlaying("Playing", Icons.Filled.PlayCircle) // <-- ADD THIS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudBridgeApp(
    viewModel: SpotifyViewModel,
    addProfileViewModel: AddProfileViewModel,
    onLaunchVoiceSearch: () -> Unit = {}
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val userProfiles by viewModel.userProfiles.collectAsState()
    val showNowPlaying by viewModel.showNowPlaying.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val requiresReauth by viewModel.requiresReauth.collectAsState()
    val deviceNotFound by viewModel.deviceNotFoundError.collectAsState()
    val rateLimitUntilEpochMs by viewModel.rateLimitUntilEpochMs.collectAsState()
    val rightPadding by viewModel.rightPadding.collectAsState()
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = rateLimitUntilEpochMs) {
        value = System.currentTimeMillis()
        while (rateLimitUntilEpochMs > value) {
            kotlinx.coroutines.delay(1000L)
            value = System.currentTimeMillis()
        }
    }
    val rateLimitActive = rateLimitUntilEpochMs > now
    val hasProfiles = userProfiles.isNotEmpty()

    BackHandler(enabled = hasProfiles && (showNowPlaying || currentScreen !is SpotifyViewModel.Screen.Home)) {
        if (showNowPlaying) {
            viewModel.closeNowPlaying()
        } else {
            viewModel.navigateBack()
        }
    }

    // Respect automotive system bars (status bar, climate bar)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .systemBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Navigation Rail (ALWAYS visible, even during Now Playing) ─
            NavigationRail(
                modifier = Modifier.fillMaxHeight().width(140.dp), // <-- INCREASE TO 130
                containerColor = SpotifyDarkSurface,
                contentColor = SpotifyGreen
            ) {
                Spacer(Modifier.height(12.dp))

                NavItem.entries.forEach { item ->
                    val selected = when (item) {
                        NavItem.Home -> currentScreen is SpotifyViewModel.Screen.Home
                        NavItem.Search -> currentScreen is SpotifyViewModel.Screen.Search
                        NavItem.Library -> currentScreen is SpotifyViewModel.Screen.Library ||
                                currentScreen is SpotifyViewModel.Screen.PlaylistDetail ||
                                currentScreen is SpotifyViewModel.Screen.AlbumDetail ||
                                currentScreen is SpotifyViewModel.Screen.ArtistDetail ||
                                currentScreen is SpotifyViewModel.Screen.PodcastDetail
                        NavItem.Queue -> currentScreen is SpotifyViewModel.Screen.Queue
                        NavItem.Settings -> currentScreen is SpotifyViewModel.Screen.Settings ||
                            currentScreen is SpotifyViewModel.Screen.HomeLayoutSettings ||
                                currentScreen is SpotifyViewModel.Screen.AddProfile
                        NavItem.NowPlaying -> showNowPlaying // <-- ADD THIS
                    }

                    NavigationRailItem(
                        selected = selected,
                        enabled = hasProfiles || item == NavItem.Settings,
                        onClick = {
                            if (!hasProfiles && item != NavItem.Settings) return@NavigationRailItem
                            when (item) {
                                NavItem.Home -> viewModel.navigateTopLevel(SpotifyViewModel.Screen.Home)
                                NavItem.Search -> viewModel.navigateTopLevel(SpotifyViewModel.Screen.Search)
                                NavItem.Library -> viewModel.navigateTopLevel(SpotifyViewModel.Screen.Library)
                                NavItem.Queue -> viewModel.navigateTopLevel(SpotifyViewModel.Screen.Queue)
                                NavItem.Settings -> viewModel.navigateTopLevel(SpotifyViewModel.Screen.Settings)
                                NavItem.NowPlaying -> viewModel.openNowPlaying() // <-- ADD THIS
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(48.dp)) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = SpotifyGreen.copy(alpha = 0.12f)
                        )
                    )
                }
            }

            // ── Content Area (Scaffold + Now Playing overlay) ─────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Scaffold(
                    modifier = Modifier.padding(end = rightPadding.dp), // Safe zone for curved hardware bezel
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (playback?.item != null && !showNowPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                MiniPlayer(
                                    playback = playback,
                                    onTap = { viewModel.openNowPlaying() },
                                    onPlayPause = { viewModel.togglePlayPause() }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        label = "screen_transition",
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { screen ->
                        when (screen) {
                            is SpotifyViewModel.Screen.Home -> HomeScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.Search -> SearchScreen(viewModel, innerPadding, onLaunchVoiceSearch)
                            is SpotifyViewModel.Screen.Library -> LibraryScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.ManagePins -> ManagePinsScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.PlaylistDetail -> PlaylistDetailScreen(viewModel, screen, innerPadding)
                            is SpotifyViewModel.Screen.AlbumDetail -> PlaylistDetailScreen(
                                viewModel,
                                SpotifyViewModel.Screen.PlaylistDetail(screen.id, screen.name, screen.uri ?: ""),
                                innerPadding
                            )
                            is SpotifyViewModel.Screen.ArtistDetail -> ArtistDetailScreen(viewModel, screen, innerPadding)
                            is SpotifyViewModel.Screen.PodcastDetail -> PodcastDetailScreen(viewModel, screen, innerPadding)
                            is SpotifyViewModel.Screen.Queue -> QueueScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.Settings -> SettingsScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.HomeLayoutSettings -> HomeLayoutSettingsScreen(viewModel, innerPadding)
                            is SpotifyViewModel.Screen.AddProfile -> AddProfileScreen(
                                viewModel = addProfileViewModel,
                                onBack = { if (hasProfiles) viewModel.navigateBack() },
                                onCompleted = {
                                    viewModel.onProfileAdded()
                                    viewModel.navigateTopLevel(SpotifyViewModel.Screen.Settings)
                                }
                            )
                        }
                    }
                }

                // ── Now Playing Overlay (only covers content area, not sidebar) ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = showNowPlaying,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    NowPlayingScreen(viewModel)
                }
            }
        }

        // ── Banners (full width, above everything) ───────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = rateLimitActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = rightPadding.dp)
                    .background(WarningOrange.copy(alpha = 0.96f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.buildRateLimitMessage(rateLimitUntilEpochMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = SpotifyWhite
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = isOffline && !rateLimitActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = rightPadding.dp) // Safe zone for curved hardware bezel
                    .background(ErrorRed.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Offline Mode - Reconnecting...",
                    style = MaterialTheme.typography.titleSmall,
                    color = SpotifyWhite
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = requiresReauth && !rateLimitActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = rightPadding.dp) // Safe zone for curved hardware bezel
                    .background(ErrorRed.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Spotify access denied (401/403). Reconnect in Settings.",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpotifyWhite
                    )
                    TextButton(onClick = {
                        viewModel.navigateTo(SpotifyViewModel.Screen.Settings)
                        viewModel.dismissReauthBanner()
                    }) {
                        Text("Open", color = SpotifyWhite)
                    }
                    IconButton(onClick = { viewModel.dismissReauthBanner() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SpotifyWhite)
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = deviceNotFound && !rateLimitActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = rightPadding.dp) // Safe zone for curved hardware bezel
                    .background(WarningOrange.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device not found. Check connection and retry.",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpotifyWhite
                    )
                    IconButton(onClick = { viewModel.dismissDeviceNotFoundError() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SpotifyWhite)
                    }
                }
            }
        }
    }
}
