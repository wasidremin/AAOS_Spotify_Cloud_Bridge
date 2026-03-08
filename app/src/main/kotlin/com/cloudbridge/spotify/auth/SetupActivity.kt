package com.cloudbridge.spotify.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cloudbridge.spotify.R
import com.cloudbridge.spotify.SpotifyCloudBridgeApp
import com.cloudbridge.spotify.databinding.ActivitySetupBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings/Setup Activity for entering Spotify credentials.
 *
 * This is launched from the AAOS "settings gear" icon in the media player UI.
 * It uses the APPLICATION_PREFERENCES intent filter, NOT MAIN/LAUNCHER.
 *
 * IMPORTANT: This activity should NOT have `distractionOptimized` meta-data.
 * AAOS will only allow it to be used while the vehicle is parked.
 *
 * The user enters their:
 * 1. Spotify Client ID (from developer.spotify.com/dashboard)
 * 2. Refresh Token (obtained via a one-time Authorization Code flow in a browser)
 *
 * These are stored in DataStore via [TokenManager]. The OkHttp [TokenRefreshAuthenticator]
 * will use the refresh token to automatically obtain short-lived access tokens.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = (application as SpotifyCloudBridgeApp).tokenManager

        // Allow credentials to be injected via Intent extras (used by adb / CI):
        //   adb shell am start -n com.cloudbridge.spotify/.auth.SetupActivity \
        //     --es client_id <id> --es refresh_token <token>
        val extraClientId = intent.getStringExtra("client_id")
        val extraClientSecret = intent.getStringExtra("client_secret")
        val extraRefreshToken = intent.getStringExtra("refresh_token")
        if (!extraClientId.isNullOrBlank() && !extraRefreshToken.isNullOrBlank()) {
            lifecycleScope.launch {
                tokenManager.saveCredentials(extraClientId, extraRefreshToken, extraClientSecret)
                Toast.makeText(
                    this@SetupActivity,
                    "Credentials saved via adb",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            return
        }

        loadExistingCredentials()
        setupButtons()
    }

    private fun loadExistingCredentials() {
        lifecycleScope.launch {
            val clientId = tokenManager.clientIdFlow.first()
            val refreshToken = tokenManager.refreshTokenFlow.first()

            binding.editClientId.setText(clientId ?: "")
            binding.editRefreshToken.setText(refreshToken ?: "")

            updateStatus(
                if (!clientId.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                    "✓ Credentials configured"
                } else {
                    "✗ No credentials set"
                }
            )
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            val clientId = binding.editClientId.text?.toString()?.trim() ?: ""
            val refreshToken = binding.editRefreshToken.text?.toString()?.trim() ?: ""

            if (clientId.isBlank() || refreshToken.isBlank()) {
                Toast.makeText(this, R.string.toast_error_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                tokenManager.saveCredentials(clientId, refreshToken)
                Toast.makeText(this@SetupActivity, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                updateStatus("✓ Credentials saved")
                // Return to AAOS — the system will re-read the MediaLibraryService
                finish()
            }
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch {
                tokenManager.clearAll()
                binding.editClientId.setText("")
                binding.editRefreshToken.setText("")
                Toast.makeText(this@SetupActivity, R.string.toast_cleared, Toast.LENGTH_SHORT).show()
                updateStatus("✗ Credentials cleared")
            }
        }
    }

    private fun updateStatus(text: String) {
        binding.txtStatus.text = text
    }
}
