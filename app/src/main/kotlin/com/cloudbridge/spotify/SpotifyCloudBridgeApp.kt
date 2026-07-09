package com.cloudbridge.spotify

import android.app.Application
import android.os.Build
import com.cloudbridge.spotify.auth.TokenManager
import com.cloudbridge.spotify.cache.CacheDatabase
import com.cloudbridge.spotify.data.SpotifyLibraryRepository
import com.cloudbridge.spotify.domain.CustomMixEngine
import com.cloudbridge.spotify.network.RetrofitProvider
import com.cloudbridge.spotify.player.AvrcpRecoveryStrategy
import com.cloudbridge.spotify.player.DeviceManager
import com.cloudbridge.spotify.player.HttpCompanionWake
import com.cloudbridge.spotify.player.PlaybackSessionManager
import com.cloudbridge.spotify.player.SpotifyPlaybackController
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * [Application] subclass for the Spotify Cloud Bridge.
 *
 * Serves as the manual dependency injection root. All singletons that
 * are shared between [MainActivity] and [SetupActivity] are created
 * here in [onCreate] and exposed as `lateinit` properties.
 *
 * Why manual DI instead of Hilt/Dagger:
 * This is a single-user AAOS appliance with a flat dependency graph.
 * A full DI framework would add build complexity (annotation processing,
 * generated code) without meaningful architectural benefit.
 *
 * Initialisation order matters:
 * 1. [TokenManager] — reads/writes OAuth tokens from DataStore.
 * 2. [RetrofitProvider] — creates OkHttp clients and Retrofit interfaces
 *    (depends on TokenManager for auth interceptors).
 * 3. [DeviceManager] — discovers the user’s phone (depends on API service).
 * 4. [SpotifyPlaybackController] — sends playback commands
 *    (depends on API service + DeviceManager).
 */
class SpotifyCloudBridgeApp : Application() {

    companion object {
        private const val TAG = "CloudBridgeApp"
        private const val LEGACY_PROFILE_ID = "legacy_default_profile"
    }

    /** Application-level coroutine scope for long-running background work. */
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var tokenManager: TokenManager
        private set

    lateinit var retrofitProvider: RetrofitProvider
        private set

    lateinit var deviceManager: DeviceManager
        private set

    lateinit var playbackController: SpotifyPlaybackController
        private set

    lateinit var playbackSessionManager: PlaybackSessionManager
        private set

    lateinit var cacheDatabase: CacheDatabase
        private set

    lateinit var libraryRepository: SpotifyLibraryRepository
        private set

    lateinit var customMixEngine: CustomMixEngine
        private set

    override fun onCreate() {
        super.onCreate()

        AppLogger.init(this)

        cacheDatabase = CacheDatabase.getInstance(this)

        tokenManager = TokenManager(
            context = this,
            userProfileDao = cacheDatabase.userProfileDao()
        )

        // Restore logging preference from DataStore
        if (!Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            runBlocking {
                val loggingWasEnabled = tokenManager.getLoggingEnabled()
                AppLogger.setEnabled(loggingWasEnabled)
            }
        }

        AppLogger.i(TAG, "SpotifyCloudBridgeApp starting")

        if (!Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            runBlocking {
                tokenManager.migrateLegacyCredentialsIfNeeded()
                if (tokenManager.removeProfile(LEGACY_PROFILE_ID)) {
                    cacheDatabase.libraryCacheDao().clearAll()
                    cacheDatabase.pinnedItemDao().clearAll()
                    AppLogger.w(TAG, "Removed seeded legacy profile placeholder and cleared stale profile cache")
                }
            }
        }

        retrofitProvider = RetrofitProvider(tokenManager)
        AppLogger.d(TAG, "RetrofitProvider initialized")

        deviceManager = DeviceManager()

        playbackController = SpotifyPlaybackController(
            api = retrofitProvider.spotifyApi
        )

        playbackSessionManager = PlaybackSessionManager(
            api = retrofitProvider.spotifyApi,
            deviceManager = deviceManager,
            recoveryStrategy = AvrcpRecoveryStrategy(this),
            lastKnownStore = tokenManager.asLastKnownDeviceStore(),
            companionWake = HttpCompanionWake(
                urlProvider = {
                    // Blocking read is fine: only called rarely on the wake ladder (IO thread).
                    runBlocking { tokenManager.getCompanionWakeUrl() }
                }
            ),
            scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO)
        )

        libraryRepository = SpotifyLibraryRepository(
            api = retrofitProvider.spotifyApi,
            cacheDb = cacheDatabase
        )
        customMixEngine = CustomMixEngine(libraryRepository)
        AppLogger.i(TAG, "All singletons initialized")
    }
}
