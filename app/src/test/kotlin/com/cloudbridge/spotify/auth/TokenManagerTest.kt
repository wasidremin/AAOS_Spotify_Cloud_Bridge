package com.cloudbridge.spotify.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cloudbridge.spotify.cache.UserProfile
import com.cloudbridge.spotify.cache.UserProfileDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [TokenManager].
 *
 * Uses Robolectric to provide an Android [Context] and a temporary
 * [PreferenceDataStoreFactory]-backed DataStore so tests run on the
 * JVM without an emulator.
 *
 * Covers: credential save/load, access-token validity checks,
 * clear-all, and hardcoded-fallback behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TokenManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var testScope: TestScope

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var tokenManager: TokenManager
    private lateinit var userProfileDao: FakeUserProfileDao

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tmpFolder.newFile("test_prefs_${System.nanoTime()}.preferences_pb") }
        )
        val context = mockk<Context>(relaxed = true)
        userProfileDao = FakeUserProfileDao()
        tokenManager = TokenManager(context, userProfileDao, dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `saveCredentials stores clientId and refreshToken`() = testScope.runTest {
        tokenManager.saveCredentials("test_client_id", "test_refresh_token")

        val clientId = tokenManager.clientIdFlow.first()
        val refreshToken = tokenManager.refreshTokenFlow.first()

        assertEquals("test_client_id", clientId)
        assertEquals("test_refresh_token", refreshToken)
    }

    @Test
    fun `saveAccessToken stores token and expiry`() = testScope.runTest {
        tokenManager.saveCredentials("test_client_id", "test_refresh_token")
        tokenManager.saveAccessToken("access_123", 3600)

        val token = tokenManager.accessTokenFlow.first()
        assertEquals("access_123", token)
    }

    @Test
    fun `isAccessTokenValid returns false when no token saved`() = testScope.runTest {
        val valid = tokenManager.isAccessTokenValid()
        assertFalse(valid)
    }

    @Test
    fun `isAccessTokenValid returns true for fresh token`() = testScope.runTest {
        tokenManager.saveCredentials("test_client_id", "test_refresh_token")
        tokenManager.saveAccessToken("access_123", 3600)
        val valid = tokenManager.isAccessTokenValid()
        assertTrue(valid)
    }

    @Test
    fun `isAccessTokenValid returns false for expired token`() = testScope.runTest {
        // Save token with 0-second expiry — should be immediately expired
        tokenManager.saveCredentials("test_client_id", "test_refresh_token")
        tokenManager.saveAccessToken("access_123", 0)
        val valid = tokenManager.isAccessTokenValid()
        assertFalse(valid)
    }

    @Test
    fun `clearAll removes all stored data`() = testScope.runTest {
        tokenManager.saveCredentials("id", "refresh")
        tokenManager.saveAccessToken("access", 3600)

        tokenManager.clearAll()

        assertNull(tokenManager.clientIdFlow.first())
        assertNull(tokenManager.refreshTokenFlow.first())
        assertNull(tokenManager.accessTokenFlow.first())
    }

    @Test
    fun `hasCredentials returns false initially without profiles`() = testScope.runTest {
        val has = tokenManager.hasCredentials.first()
        assertFalse(has)
    }

    @Test
    fun `hasCredentials returns true after saving`() = testScope.runTest {
        tokenManager.saveCredentials("id", "refresh")
        val has = tokenManager.hasCredentials.first()
        assertTrue(has)
    }

    @Test
    fun `daily drive news id defaults and persists`() = testScope.runTest {
        assertEquals("1L1qK1Gvj5B0AItWlF1n9G", tokenManager.dailyDriveNewsIdFlow.first())

        tokenManager.saveDailyDriveNewsId("custom_show_id")
        assertEquals("custom_show_id", tokenManager.dailyDriveNewsIdFlow.first())

        tokenManager.saveDailyDriveNewsId("   ")
        assertEquals("1L1qK1Gvj5B0AItWlF1n9G", tokenManager.dailyDriveNewsIdFlow.first())
    }

    @Test
    fun `home section order defaults empty and persists custom order`() = testScope.runTest {
        assertEquals(emptyList<String>(), tokenManager.homeSectionOrderFlow.first())

        tokenManager.saveHomeSectionOrder(listOf("podcasts", "jump_back_in", "new_releases"))

        assertEquals(
            listOf("podcasts", "jump_back_in", "new_releases"),
            tokenManager.homeSectionOrderFlow.first()
        )
    }

    @Test
    fun `explicit filter defaults off and persists`() = testScope.runTest {
        assertFalse(tokenManager.explicitFilterEnabledFlow.first())

        tokenManager.saveExplicitFilterEnabled(true)
        assertTrue(tokenManager.explicitFilterEnabledFlow.first())

        tokenManager.saveExplicitFilterEnabled(false)
        assertFalse(tokenManager.explicitFilterEnabledFlow.first())
    }

    @Test
    fun `rate limit lockout persists and clears`() = testScope.runTest {
        assertFalse(tokenManager.isRateLimitLockoutActive())

        tokenManager.saveRateLimitLockout(60)
        assertTrue(tokenManager.isRateLimitLockoutActive())
        assertTrue(tokenManager.getRateLimitUntilEpochMs() > System.currentTimeMillis())
        assertEquals(60L, tokenManager.getRateLimitRetryAfterSeconds())

        tokenManager.clearRateLimitLockout()
        assertFalse(tokenManager.isRateLimitLockoutActive())
        assertEquals(0L, tokenManager.getRateLimitUntilEpochMs())
    }

    @Test
    fun `migrateLegacyCredentialsIfNeeded moves legacy datastore keys into a profile`() = testScope.runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("client_id")] = "legacy_client"
            prefs[stringPreferencesKey("client_secret")] = "legacy_secret"
            prefs[stringPreferencesKey("refresh_token")] = "legacy_refresh"
            prefs[stringPreferencesKey("access_token")] = "legacy_access"
            prefs[longPreferencesKey("token_expiry_epoch_ms")] = 12345L
        }

        val migrated = tokenManager.migrateLegacyCredentialsIfNeeded()

        assertTrue(migrated)

        val profiles = userProfileDao.getAllOnce()
        assertEquals(1, profiles.size)
        assertEquals("legacy_client", profiles.first().clientId)
        assertEquals("legacy_secret", profiles.first().clientSecret)
        assertEquals("legacy_refresh", profiles.first().refreshToken)
        assertEquals("legacy_access", profiles.first().accessToken)
        assertEquals(12345L, profiles.first().tokenExpiryEpochMs)
        assertEquals(profiles.first().id, tokenManager.activeProfileIdFlow.first())

        val prefs = dataStore.data.first()
        assertNull(prefs[stringPreferencesKey("client_id")])
        assertNull(prefs[stringPreferencesKey("client_secret")])
        assertNull(prefs[stringPreferencesKey("refresh_token")])
        assertNull(prefs[stringPreferencesKey("access_token")])
        assertNull(prefs[longPreferencesKey("token_expiry_epoch_ms")])
    }
}

private class FakeUserProfileDao : UserProfileDao {
    private val profilesFlow = MutableStateFlow<List<UserProfile>>(emptyList())

    override fun getAll(): Flow<List<UserProfile>> = profilesFlow

    override suspend fun getAllOnce(): List<UserProfile> = profilesFlow.value

    override suspend fun getById(id: String): UserProfile? = profilesFlow.value.firstOrNull { it.id == id }

    override suspend fun insert(profile: UserProfile) {
        profilesFlow.value = profilesFlow.value
            .filterNot { it.id == profile.id } + profile
    }

    override suspend fun update(profile: UserProfile) {
        insert(profile)
    }

    override suspend fun delete(profile: UserProfile) {
        profilesFlow.value = profilesFlow.value.filterNot { it.id == profile.id }
    }

    override suspend fun clearAll() {
        profilesFlow.value = emptyList()
    }
}
