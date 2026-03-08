package com.cloudbridge.spotify.ui

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
        private const val WEB_APP_BASE_URL = "https://your-web-app.com"
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null
    private val secureRandom = SecureRandom()

    fun startNewSession() {
        pollingJob?.cancel()
        val code = buildSessionCode()
        _sessionCode.value = code
        _qrCodeUrl.value = "$WEB_APP_BASE_URL?code=$code"
        _isWaitingForProfile.value = true
        _isCompleting.value = false
        _isCompleted.value = false
        _errorMessage.value = null

        pollingJob = viewModelScope.launch {
            while (isActive && !_isCompleted.value) {
                try {
                    val payload = cloudRelayService.getSession(code)
                    if (payload != null) {
                        _isCompleting.value = true
                        val profile = UserProfile(
                            id = UUID.randomUUID().toString(),
                            name = payload.profileName.ifBlank { "Spotify Profile" },
                            clientId = payload.clientId,
                            clientSecret = payload.clientSecret,
                            refreshToken = payload.refreshToken,
                            accessToken = null,
                            tokenExpiryEpochMs = 0L,
                            profileImageUrl = payload.profileImageUrl
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
}