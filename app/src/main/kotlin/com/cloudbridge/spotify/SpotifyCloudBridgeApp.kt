package com.cloudbridge.spotify

import android.app.Application
import android.os.Build
import com.cloudbridge.spotify.auth.TokenManager
import com.cloudbridge.spotify.cache.CacheDatabase
import com.cloudbridge.spotify.cache.UserProfile
import com.cloudbridge.spotify.data.SpotifyLibraryRepository
import com.cloudbridge.spotify.domain.CustomMixEngine
import com.cloudbridge.spotify.network.RetrofitProvider
import com.cloudbridge.spotify.player.DeviceManager
import com.cloudbridge.spotify.player.SpotifyPlaybackController
import kotlinx.coroutines.CoroutineScope
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
        private const val LEGACY_PROFILE_ID = "legacy_default_profile"
        private const val LEGACY_CLIENT_ID = "1a979df868544bcd8c69fd27492c0cb0"
        private const val LEGACY_CLIENT_SECRET = "78731cc4af5c42e7a2609f1419655490"
        private const val LEGACY_REFRESH_TOKEN = "AQA7TLxxhyfiYlq_5DJfWjZ2X7evsTvQbWnhuQkL6F76JeBlcj9QdndEvVlbmks6ASNpTMHuVTBEBOH1TzQO4UcFRPVo8WjmMY3qDkYvq5xEDdKK6hKFikMkY2LxkEbcG1E"
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

    lateinit var cacheDatabase: CacheDatabase
        private set

    lateinit var libraryRepository: SpotifyLibraryRepository
        private set

    lateinit var customMixEngine: CustomMixEngine
        private set

    override fun onCreate() {
        super.onCreate()

        cacheDatabase = CacheDatabase.getInstance(this)

        tokenManager = TokenManager(
            context = this,
            userProfileDao = cacheDatabase.userProfileDao()
        )
        if (!Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            runBlocking {
                tokenManager.migrateLegacyCredentialsIfNeeded()
            }
        }

        if (!isUnitTestEnvironment()) {
            runBlocking {
                seedLegacyProfileIfNeeded()
            }
        }

        retrofitProvider = RetrofitProvider(tokenManager)

        deviceManager = DeviceManager(retrofitProvider.spotifyApi)

        playbackController = SpotifyPlaybackController(
            api = retrofitProvider.spotifyApi,
            deviceManager = deviceManager
        )

        libraryRepository = SpotifyLibraryRepository(
            api = retrofitProvider.spotifyApi,
            cacheDb = cacheDatabase
        )
        customMixEngine = CustomMixEngine(libraryRepository)
    }

    private suspend fun seedLegacyProfileIfNeeded() {
        val userProfileDao = cacheDatabase.userProfileDao()
        if (userProfileDao.getAllOnce().isNotEmpty()) return

        userProfileDao.insert(
            UserProfile(
                id = LEGACY_PROFILE_ID,
                name = "Primary Profile",
                clientId = LEGACY_CLIENT_ID,
                clientSecret = LEGACY_CLIENT_SECRET,
                refreshToken = LEGACY_REFRESH_TOKEN,
                accessToken = null,
                tokenExpiryEpochMs = 0L,
                profileImageUrl = null
            )
        )
        tokenManager.setActiveProfileId(LEGACY_PROFILE_ID)
    }

    private fun isUnitTestEnvironment(): Boolean = try {
        Class.forName("org.robolectric.RuntimeEnvironment")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
