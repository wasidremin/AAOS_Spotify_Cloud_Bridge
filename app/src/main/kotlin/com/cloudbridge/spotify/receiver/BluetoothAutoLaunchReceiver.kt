package com.cloudbridge.spotify.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cloudbridge.spotify.SpotifyCloudBridgeApp
import com.cloudbridge.spotify.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Bluetooth Auto-Launch Receiver.
 *
 * Listens for [BluetoothDevice.ACTION_ACL_CONNECTED] broadcasts.
 * If the connecting device's MAC address matches the one saved in
 * [com.cloudbridge.spotify.auth.TokenManager] (set by the user in
 * Settings), this receiver brings [MainActivity] to the foreground —
 * giving a Car OS "open music app when phone connects" experience.
 *
 * ## No-match behaviour
 * If no MAC is configured (feature disabled), or the address doesn't
 * match, the broadcast is silently ignored.
 *
 * ## Permissions required (see AndroidManifest.xml)
 * - `android.permission.BLUETOOTH` (API ≤ 30)
 * - `android.permission.BLUETOOTH_CONNECT` (API ≥ 31)
 *
 * ## Manifest registration
 * ```xml
 * <receiver android:name=".receiver.BluetoothAutoLaunchReceiver"
 *           android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.bluetooth.device.action.ACL_CONNECTED"/>
 *     </intent-filter>
 * </receiver>
 * ```
 */
class BluetoothAutoLaunchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BTAutoLaunch"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val app = context.applicationContext as? SpotifyCloudBridgeApp ?: run {
            Log.e(TAG, "Could not cast applicationContext to SpotifyCloudBridgeApp")
            return
        }

        // Use the application-level scope — goAsync() would work too but
        // the DataStore read is fast and we already have a scope available.
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                val savedMac = app.tokenManager.getBtAutoLaunchMac()

                if (savedMac.isNullOrBlank()) {
                    Log.d(TAG, "BT auto-launch: no MAC configured, ignoring connection event")
                    return@launch
                }

                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                val deviceMac = try {
                    device?.address?.uppercase()?.trim()
                } catch (se: SecurityException) {
                    // BLUETOOTH_CONNECT permission not granted at runtime — skip
                    Log.w(TAG, "Cannot read device address — BLUETOOTH_CONNECT not granted", se)
                    null
                }

                if (deviceMac == null) {
                    Log.d(TAG, "BT auto-launch: could not read device MAC, ignoring")
                    return@launch
                }

                if (deviceMac == savedMac) {
                    Log.i(TAG, "BT auto-launch: matched $deviceMac — launching MainActivity")
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    }
                    context.startActivity(launchIntent)
                } else {
                    Log.d(TAG, "BT auto-launch: $deviceMac ≠ $savedMac, ignoring")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
