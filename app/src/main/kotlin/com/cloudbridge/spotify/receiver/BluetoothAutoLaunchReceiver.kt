package com.cloudbridge.spotify.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloudbridge.spotify.SpotifyCloudBridgeApp
import com.cloudbridge.spotify.util.AppLogger
import com.cloudbridge.spotify.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Bluetooth ACL receiver.
 *
 * On every [BluetoothDevice.ACTION_ACL_CONNECTED]:
 * 1. Triggers [PlaybackSessionManager.onBluetoothConnected] so Connect
 *    session recovery runs after a short settle delay (independent of UI).
 * 2. If a MAC is saved in Settings and matches, brings [MainActivity]
 *    to the foreground (auto-launch).
 *
 * ## Permissions required (see AndroidManifest.xml)
 * - `android.permission.BLUETOOTH` (API ≤ 30)
 * - `android.permission.BLUETOOTH_CONNECT` (API ≥ 31)
 */
class BluetoothAutoLaunchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BTAutoLaunch"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val app = context.applicationContext as? SpotifyCloudBridgeApp ?: run {
            AppLogger.e(TAG, "Could not cast applicationContext to SpotifyCloudBridgeApp")
            return
        }

        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                // Always recover the Connect session after BT ACL (settled inside manager).
                AppLogger.i(TAG, "ACL_CONNECTED — scheduling playback session reconnect")
                app.playbackSessionManager.onBluetoothConnected()

                val savedMac = app.tokenManager.getBtAutoLaunchMac()
                if (savedMac.isNullOrBlank()) {
                    AppLogger.d(TAG, "BT auto-launch: no MAC configured (session reconnect only)")
                    return@launch
                }

                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                val deviceMac = try {
                    device?.address?.uppercase()?.trim()
                } catch (se: SecurityException) {
                    AppLogger.w(TAG, "Cannot read device address — BLUETOOTH_CONNECT not granted", se)
                    null
                }

                if (deviceMac == null) {
                    AppLogger.d(TAG, "BT auto-launch: could not read device MAC (session reconnect only)")
                    return@launch
                }

                if (deviceMac == savedMac) {
                    AppLogger.i(TAG, "BT auto-launch: matched $deviceMac — launching MainActivity")
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    }
                    context.startActivity(launchIntent)
                } else {
                    AppLogger.d(TAG, "BT auto-launch: $deviceMac ≠ $savedMac (session reconnect only)")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
