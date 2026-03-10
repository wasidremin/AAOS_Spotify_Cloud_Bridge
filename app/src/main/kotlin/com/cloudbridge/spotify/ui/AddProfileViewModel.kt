package com.cloudbridge.spotify.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cloudbridge.spotify.auth.TokenManager
import com.cloudbridge.spotify.cache.UserProfile
import com.cloudbridge.spotify.cache.UserProfileDao
import com.cloudbridge.spotify.network.CloudRelayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

class AddProfileViewModel(
    private val cloudRelayService: CloudRelayService,
    private val userProfileDao: UserProfileDao,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val POLL_INTERVAL_MS = 3000L
        private const val WEB_APP_BASE_URL = "https://wasidremin.github.io/AAOS_Spotify_Cloud_Bridge/"
        private val SESSION_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()
    }

    class Factory(
        private val cloudRelayService: CloudRelayService,
        private val userProfileDao: UserProfileDao,
        private val tokenManager: TokenManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddProfileViewModel(cloudRelayService, userProfileDao, tokenManager) as T
        }
    }

    private val _sessionCode = MutableStateFlow("")
    val sessionCode: StateFlow<String> = _sessionCode.asStateFlow()

    private val _qrCodeUrl = MutableStateFlow("")
    val qrCodeUrl: StateFlow<String> = _qrCodeUrl.asStateFlow()

    private val _isWaitingForProfile = MutableStateFlow(false)
    val isWaitingForProfile: StateFlow<Boolean> = _isWaitingForProfile.asStateFlow()

    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    private val _isRefreshSession = MutableStateFlow(false)
    val isRefreshSession: StateFlow<Boolean> = _isRefreshSession.asStateFlow()

    private val _refreshTargetName = MutableStateFlow<String?>(null)
    val refreshTargetName: StateFlow<String?> = _refreshTargetName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null
    private val secureRandom = SecureRandom()

    fun startNewSession(refreshProfileId: String? = null) {
        pollingJob?.cancel()
        val code = buildSessionCode()
        _sessionCode.value = code
        _qrCodeUrl.value = ""
        _isWaitingForProfile.value = true
        _isCompleting.value = false
        _isCompleted.value = false
        _isRefreshSession.value = !refreshProfileId.isNullOrBlank()
        _refreshTargetName.value = null
        _errorMessage.value = null

        pollingJob = viewModelScope.launch {
            val targetProfile = refreshProfileId?.let { userProfileDao.getById(it) }
            if (!refreshProfileId.isNullOrBlank() && targetProfile == null) {
                _isWaitingForProfile.value = false
                _errorMessage.value = "Refresh target was not found. Return to Settings and try again."
                return@launch
            }

            _isRefreshSession.value = targetProfile != null
            _refreshTargetName.value = targetProfile?.name
            _qrCodeUrl.value = buildSessionUrl(code, targetProfile)

            while (isActive && !_isCompleted.value) {
                try {
                    val payload = cloudRelayService.getSession(code)
                    if (payload != null) {
                        _isCompleting.value = true
                        val existingProfile = (payload.targetProfileId ?: refreshProfileId)
                            ?.let { userProfileDao.getById(it) }
                        val profile = (existingProfile ?: UserProfile(
                            id = UUID.randomUUID().toString(),
                            name = payload.profileName.ifBlank { "Spotify Profile" },
                            clientId = payload.clientId,
                            clientSecret = payload.clientSecret,
                            refreshToken = payload.refreshToken,
                            accessToken = null,
                            tokenExpiryEpochMs = 0L,
                            profileImageUrl = payload.profileImageUrl
                        )).copy(
                            name = payload.profileName.ifBlank {
                                existingProfile?.name ?: "Spotify Profile"
                            },
                            clientId = payload.clientId,
                            clientSecret = payload.clientSecret ?: existingProfile?.clientSecret,
                            refreshToken = payload.refreshToken,
                            accessToken = null,
                            tokenExpiryEpochMs = 0L,
                            profileImageUrl = payload.profileImageUrl ?: existingProfile?.profileImageUrl
                        )
                        userProfileDao.insert(profile)
                        tokenManager.setActiveProfileId(profile.id)
                        cloudRelayService.deleteSession(code)
                        _isCompleted.value = true
                        _isWaitingForProfile.value = false
                        _isCompleting.value = false
                        break
                    }
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Profile polling failed"
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private fun buildSessionCode(): String = buildString {
        repeat(6) {
            append(SESSION_ALPHABET[secureRandom.nextInt(SESSION_ALPHABET.size)])
        }
    }

    private fun buildSessionUrl(sessionCode: String, targetProfile: UserProfile?): String {
        val builder = Uri.parse(WEB_APP_BASE_URL).buildUpon()
            .appendQueryParameter("session", sessionCode)

        if (targetProfile != null) {
            builder
                .appendQueryParameter("mode", "refresh")
                .appendQueryParameter("profile_id", targetProfile.id)
                .appendQueryParameter("client_id", targetProfile.clientId)

            targetProfile.clientSecret
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.appendQueryParameter("client_secret", it) }
        }

        return builder.build().toString()
    }
}