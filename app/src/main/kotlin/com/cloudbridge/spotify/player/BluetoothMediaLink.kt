package com.cloudbridge.spotify.player

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.cloudbridge.spotify.util.AppLogger

/**
 * Detects whether a phone (or other media device) is connected over classic
 * Bluetooth audio profiles. Used to gate AVRCP wake so we do not fire media
 * keys on emulator / when nothing is paired.
 */
object BluetoothMediaLink {
    private const val TAG = "BluetoothMediaLink"

    /**
     * @return true if A2DP and/or HEADSET/HFP is connected.
     *         false if Bluetooth is off, unavailable, permission denied,
     *         or no media profile is connected (typical on emulator).
     */
    fun isMediaDeviceConnected(context: Context): Boolean {
        return try {
            val adapter = bluetoothAdapter(context) ?: run {
                AppLogger.d(TAG, "No Bluetooth adapter")
                return false
            }
            if (!adapter.isEnabled) {
                AppLogger.d(TAG, "Bluetooth disabled")
                return false
            }

            val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
            val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            val connected =
                a2dp == BluetoothProfile.STATE_CONNECTED ||
                    headset == BluetoothProfile.STATE_CONNECTED

            AppLogger.d(
                TAG,
                "Media BT: a2dp=${stateName(a2dp)} headset=${stateName(headset)} connected=$connected"
            )
            connected
        } catch (se: SecurityException) {
            AppLogger.w(TAG, "Bluetooth permission denied; treating as not connected")
            false
        } catch (e: Exception) {
            AppLogger.w(TAG, "Bluetooth probe failed: ${e.message}")
            false
        }
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.applicationContext
                .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> "UNKNOWN($state)"
    }
}
