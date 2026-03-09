package com.cloudbridge.spotify.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudbridge.spotify.cache.UserProfile
import com.cloudbridge.spotify.cache.UserProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

// Single DataStore instance scoped to the application context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spotify_cloud_bridge_prefs"
)

/**
 * Manages Spotify OAuth tokens using AndroidX DataStore.
 *
 * Stores the user's Client ID, Refresh Token (long-lived, entered manually),
 * and the Access Token (short-lived, auto-refreshed by [TokenRefreshAuthenticator]).
 *
 * Why DataStore over SharedPreferences:
 * - Thread-safe by default (backed by coroutines)
 * - No risk of ANR from disk I/O on main thread
 * - Flow-based observation for reactive UI updates
 */
class TokenManager(
    private val context: Context,
    private val userProfileDao: UserProfileDao,
    private val testDataStore: DataStore<Preferences>? = null
) {

    private val dataStore: DataStore<Preferences>
        get() = testDataStore ?: context.dataStore

    companion object {
        private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val KEY_LEGACY_CLIENT_ID = stringPreferencesKey("client_id")
        private val KEY_LEGACY_CLIENT_SECRET = stringPreferencesKey("client_secret")
        private val KEY_LEGACY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_LEGACY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_LEGACY_TOKEN_EXPIRY = longPreferencesKey("token_expiry_epoch_ms")
        private val KEY_RATE_LIMIT_UNTIL = longPreferencesKey("rate_limit_until_epoch_ms")
        private val KEY_RATE_LIMIT_RETRY_AFTER_SECONDS = longPreferencesKey("rate_limit_retry_after_seconds")
        private val KEY_LOCKED_DEVICE_ID = stringPreferencesKey("locked_device_id")
        private val KEY_LOCKED_DEVICE_NAME = stringPreferencesKey("locked_device_name")
        private val KEY_BT_MAC = stringPreferencesKey("bt_auto_launch_mac")
        private val KEY_GRID_COLUMNS = androidx.datastore.preferences.core.intPreferencesKey("grid_columns")
        private val KEY_RIGHT_PADDING = androidx.datastore.preferences.core.intPreferencesKey("right_padding")
        private val KEY_PLAY_INSTANTLY = androidx.datastore.preferences.core.booleanPreferencesKey("play_instantly")
        private val KEY_EXPLICIT_FILTER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("explicit_filter_enabled")
        private val KEY_DAILY_DRIVE_NEWS_ID = stringPreferencesKey("daily_drive_news_id")
        private val KEY_HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        private const val DEFAULT_DAILY_DRIVE_NEWS_ID = "1L1qK1Gvj5B0AItWlF1n9G"
    }

    // ── Flows (for reactive observation) ─────────────────────────────

    val activeProfileIdFlow: Flow<String?> = dataStore.data.map { it[KEY_ACTIVE_PROFILE_ID] }
    val userProfilesFlow: Flow<List<UserProfile>> = userProfileDao.getAll()

    private val activeProfileFlow: Flow<UserProfile?> = combine(
        activeProfileIdFlow,
        userProfilesFlow
    ) { activeProfileId, profiles ->
        when {
            !activeProfileId.isNullOrBlank() -> profiles.firstOrNull { it.id == activeProfileId }
            else -> profiles.firstOrNull()
        }
    }.distinctUntilChanged()

    val clientIdFlow: Flow<String?> = activeProfileFlow.map { it?.clientId }
    val clientSecretFlow: Flow<String?> = activeProfileFlow.map { it?.clientSecret }
    val refreshTokenFlow: Flow<String?> = activeProfileFlow.map { it?.refreshToken }
    val accessTokenFlow: Flow<String?> = activeProfileFlow.map { it?.accessToken }
    val rateLimitUntilEpochMsFlow: Flow<Long> = dataStore.data.map { it[KEY_RATE_LIMIT_UNTIL] ?: 0L }
    val rateLimitRetryAfterSecondsFlow: Flow<Long> = dataStore.data.map { it[KEY_RATE_LIMIT_RETRY_AFTER_SECONDS] ?: 0L }

    val lockedDeviceIdFlow: Flow<String?> = dataStore.data.map { it[KEY_LOCKED_DEVICE_ID] }
    val lockedDeviceNameFlow: Flow<String?> = dataStore.data.map { it[KEY_LOCKED_DEVICE_NAME] }

    val gridColumnsFlow: kotlinx.coroutines.flow.Flow<Int> = dataStore.data.map { it[KEY_GRID_COLUMNS] ?: 4 }
    val rightPaddingFlow: kotlinx.coroutines.flow.Flow<Int> = dataStore.data.map { it[KEY_RIGHT_PADDING] ?: 160 }
    val playInstantlyFlow: kotlinx.coroutines.flow.Flow<Boolean> = dataStore.data.map { it[KEY_PLAY_INSTANTLY] ?: false }
    val explicitFilterEnabledFlow: Flow<Boolean> = dataStore.data.map { it[KEY_EXPLICIT_FILTER_ENABLED] ?: false }
    val dailyDriveNewsIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_DRIVE_NEWS_ID]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_DAILY_DRIVE_NEWS_ID
    }
    val homeSectionOrderFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_HOME_SECTION_ORDER]
            ?.split(',')
            ?.mapNotNull { entry -> entry.trim().takeIf { it.isNotBlank() } }
            ?: emptyList()
    }

    val hasCredentials: Flow<Boolean> = activeProfileFlow.map { profile ->
        !profile?.clientId.isNullOrBlank() && !profile?.refreshToken.isNullOrBlank()
    }

    // ── Suspend getters (for one-shot reads in interceptors) ─────────

    suspend fun getClientId(): String? = resolveActiveProfile()?.clientId
    suspend fun getClientSecret(): String? = resolveActiveProfile()?.clientSecret
    suspend fun getRefreshToken(): String? = resolveActiveProfile()?.refreshToken
    suspend fun getAccessToken(): String? = resolveActiveProfile()?.accessToken
    suspend fun getTokenExpiry(): Long = resolveActiveProfile()?.tokenExpiryEpochMs ?: 0L
    suspend fun getActiveProfileId(): String? = activeProfileIdFlow.first()
    suspend fun getRateLimitUntilEpochMs(): Long = dataStore.data.first()[KEY_RATE_LIMIT_UNTIL] ?: 0L
    suspend fun getRateLimitRetryAfterSeconds(): Long = dataStore.data.first()[KEY_RATE_LIMIT_RETRY_AFTER_SECONDS] ?: 0L

    /**
     * Returns `true` if the stored access token is still valid.
     *
     * Applies a **60-second safety margin** so that a token nearing its
     * expiry is treated as expired. This prevents the edge case where a
     * token is technically valid when the request starts but expires
     * mid-flight (round-trip latency on cellular networks can be high).
     *
     * @return `true` if a non-blank token exists and its expiry is at
     *         least 60 seconds in the future; `false` otherwise.
     */
    suspend fun isAccessTokenValid(): Boolean {
        val token = getAccessToken()
        val expiry = getTokenExpiry()
        // 60_000 ms = 60-second safety margin; avoids mid-flight expiry.
        return !token.isNullOrBlank() && System.currentTimeMillis() < (expiry - 60_000)
    }

    suspend fun isRateLimitLockoutActive(): Boolean {
        val until = getRateLimitUntilEpochMs()
        val active = until > System.currentTimeMillis()
        if (!active && until != 0L) {
            clearRateLimitLockout()
        }
        return active
    }

    // ── Writers ───────────────────────────────────────────────────────

    /**
     * Save the user-provided credentials against the active profile.
     * If there is no active profile yet, a new manual profile is created.
     */
    suspend fun saveCredentials(clientId: String, refreshToken: String, clientSecret: String? = null) {
        val existing = resolveActiveProfile()
        val profile = (existing ?: UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Manual Profile",
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = refreshToken,
            accessToken = null,
            tokenExpiryEpochMs = 0L,
            profileImageUrl = null
        )).copy(
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = refreshToken
        )

        userProfileDao.insert(profile)
        setActiveProfileId(profile.id)
    }

    /**
     * Save a freshly obtained access token and its expiry.
     * Called by [TokenRefreshAuthenticator] after a successful refresh.
     *
     * @param accessToken The new Bearer token.
     * @param expiresInSeconds The TTL from Spotify's token response (typically 3600).
     */
    suspend fun saveAccessToken(accessToken: String, expiresInSeconds: Int) {
        val activeProfile = resolveActiveProfile() ?: return
        userProfileDao.update(
            activeProfile.copy(
                accessToken = accessToken,
                tokenExpiryEpochMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            )
        )
    }

    suspend fun setActiveProfileId(profileId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROFILE_ID] = profileId
        }
    }

    suspend fun saveRateLimitLockout(retryAfterSeconds: Long) {
        val boundedRetryAfter = retryAfterSeconds.coerceAtLeast(1L)
        dataStore.edit { prefs ->
            prefs[KEY_RATE_LIMIT_UNTIL] = System.currentTimeMillis() + (boundedRetryAfter * 1000L)
            prefs[KEY_RATE_LIMIT_RETRY_AFTER_SECONDS] = boundedRetryAfter
        }
    }

    suspend fun clearRateLimitLockout() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_RATE_LIMIT_UNTIL)
            prefs.remove(KEY_RATE_LIMIT_RETRY_AFTER_SECONDS)
        }
    }

    /**
     * Clear all stored data. Used when the user taps "Clear" in SetupActivity.
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
        userProfileDao.clearAll()
    }

    suspend fun migrateLegacyCredentialsIfNeeded(): Boolean {
        if (userProfileDao.getAllOnce().isNotEmpty()) return false

        val prefs = dataStore.data.first()
        val clientId = prefs[KEY_LEGACY_CLIENT_ID]?.trim().orEmpty()
        val refreshToken = prefs[KEY_LEGACY_REFRESH_TOKEN]?.trim().orEmpty()
        if (clientId.isBlank() || refreshToken.isBlank()) return false

        val migratedProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Primary Profile",
            clientId = clientId,
            clientSecret = prefs[KEY_LEGACY_CLIENT_SECRET]?.trim()?.takeIf { it.isNotBlank() },
            refreshToken = refreshToken,
            accessToken = prefs[KEY_LEGACY_ACCESS_TOKEN]?.trim()?.takeIf { it.isNotBlank() },
            tokenExpiryEpochMs = prefs[KEY_LEGACY_TOKEN_EXPIRY] ?: 0L,
            profileImageUrl = null
        )

        userProfileDao.insert(migratedProfile)
        setActiveProfileId(migratedProfile.id)

        dataStore.edit { editablePrefs ->
            editablePrefs.remove(KEY_LEGACY_CLIENT_ID)
            editablePrefs.remove(KEY_LEGACY_CLIENT_SECRET)
            editablePrefs.remove(KEY_LEGACY_REFRESH_TOKEN)
            editablePrefs.remove(KEY_LEGACY_ACCESS_TOKEN)
            editablePrefs.remove(KEY_LEGACY_TOKEN_EXPIRY)
        }

        return true
    }

    /**
     * Lock playback to a specific Spotify Connect device.
     * When locked, DeviceManager will always use this device ID
     * instead of auto-discovering the phone.
     */
    suspend fun lockDevice(deviceId: String, deviceName: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCKED_DEVICE_ID] = deviceId
            prefs[KEY_LOCKED_DEVICE_NAME] = deviceName
        }
    }

    /**
     * Unlock device selection — return to automatic phone discovery.
     */
    suspend fun unlockDevice() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LOCKED_DEVICE_ID)
            prefs.remove(KEY_LOCKED_DEVICE_NAME)
        }
    }

    suspend fun getLockedDeviceId(): String? = lockedDeviceIdFlow.first()
    suspend fun getLockedDeviceName(): String? = lockedDeviceNameFlow.first()

    // ── Bluetooth Auto-Launch MAC ─────────────────────────────────────

    val btAutoLaunchMacFlow: Flow<String?> = dataStore.data.map { it[KEY_BT_MAC] }

    /**
     * Save the Bluetooth MAC address of the car head unit or phone.
     * When this MAC connects, [BluetoothAutoLaunchReceiver] will bring
     * [MainActivity] to the foreground.
     * Pass `null` to clear (disable auto-launch).
     */
    suspend fun saveBtAutoLaunchMac(mac: String?) {
        dataStore.edit { prefs ->
            if (mac.isNullOrBlank()) prefs.remove(KEY_BT_MAC)
            else prefs[KEY_BT_MAC] = mac.uppercase().trim()
        }
    }

    suspend fun saveGridColumns(columns: Int) {
        dataStore.edit { it[KEY_GRID_COLUMNS] = columns }
    }

    suspend fun saveRightPadding(padding: Int) {
        dataStore.edit { it[KEY_RIGHT_PADDING] = padding }
    }

    suspend fun savePlayInstantly(play: Boolean) {
        dataStore.edit { it[KEY_PLAY_INSTANTLY] = play }
    }

    suspend fun saveExplicitFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_EXPLICIT_FILTER_ENABLED] = enabled }
    }

    suspend fun saveDailyDriveNewsId(id: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_DRIVE_NEWS_ID] = id.trim().ifBlank { DEFAULT_DAILY_DRIVE_NEWS_ID }
        }
    }

    suspend fun saveHomeSectionOrder(order: List<String>) {
        dataStore.edit { prefs ->
            val normalized = order.mapNotNull { it.trim().takeIf { key -> key.isNotBlank() } }
            if (normalized.isEmpty()) {
                prefs.remove(KEY_HOME_SECTION_ORDER)
            } else {
                prefs[KEY_HOME_SECTION_ORDER] = normalized.joinToString(separator = ",")
            }
        }
    }

    suspend fun getBtAutoLaunchMac(): String? = btAutoLaunchMacFlow.first()

    private suspend fun resolveActiveProfile(): UserProfile? {
        val activeId = getActiveProfileId()
        if (!activeId.isNullOrBlank()) {
            userProfileDao.getById(activeId)?.let { return it }
        }

        val fallbackProfile = userProfileDao.getAllOnce().firstOrNull()
        if (fallbackProfile != null && activeId != fallbackProfile.id) {
            setActiveProfileId(fallbackProfile.id)
        }
        return fallbackProfile
    }
}
