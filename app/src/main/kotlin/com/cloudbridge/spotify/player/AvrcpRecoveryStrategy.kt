package com.cloudbridge.spotify.player

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.cloudbridge.spotify.util.AppLogger

/**
 * Optional recovery path that dispatches AVRCP KEYCODE_MEDIA_PLAY to wake a
 * sleeping phone Spotify session over Bluetooth.
 *
 * Only [PlaybackSessionManager] / [DeviceWakeCoordinator] should invoke this,
 * and only on phone-oriented targets when Connect discovery has failed.
 *
 * @return true if a media-play key pair was dispatched; false if skipped
 *         (no BT media link, call audio active, etc.).
 */
fun interface RecoveryStrategy {
    /** Best-effort wakeup. Implementations must not throw. */
    fun attemptWakeup(): Boolean
}

/**
 * Android AudioManager-backed AVRCP kickstart.
 *
 * Skips when:
 * - No A2DP/HEADSET device is connected (emulator / phone not paired)
 * - Call or ringtone audio mode is active
 */
class AvrcpRecoveryStrategy(
    private val context: Context,
    private val mediaLinkCheck: () -> Boolean = {
        BluetoothMediaLink.isMediaDeviceConnected(context)
    }
) : RecoveryStrategy {

    companion object {
        private const val TAG = "AvrcpRecovery"
    }

    override fun attemptWakeup(): Boolean {
        return try {
            if (!mediaLinkCheck()) {
                AppLogger.i(
                    TAG,
                    "Kickstart skipped: no Bluetooth A2DP/HEADSET device connected " +
                        "(pair the phone to the car, or use optional wake webhook)"
                )
                return false
            }

            val audioManager = context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                AppLogger.w(TAG, "Kickstart skipped: AudioManager unavailable")
                return false
            }

            if (audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_RINGTONE
            ) {
                AppLogger.w(TAG, "Kickstart skipped: call or ringtone audio is active")
                return false
            }

            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            AppLogger.i(TAG, "Dispatched AVRCP Bluetooth kickstart")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to dispatch AVRCP kickstart: ${e.message}", e)
            false
        }
    }
}

/** No-op strategy for unit tests and environments without audio routing. */
object NoOpRecoveryStrategy : RecoveryStrategy {
    override fun attemptWakeup(): Boolean = false
}

/**
 * Test double that records calls and always "succeeds" at dispatch.
 * Use when tests need to assert AVRCP was *attempted* regardless of BT.
 */
class RecordingRecoveryStrategy(
    var allowDispatch: Boolean = true
) : RecoveryStrategy {
    var calls = 0
        private set

    override fun attemptWakeup(): Boolean {
        calls++
        return allowDispatch
    }
}
