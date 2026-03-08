package com.cloudbridge.spotify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The colour scheme mapping Spotify brand colours to Material 3 semantic roles.
 *
 * Uses [darkColorScheme] exclusively — the Spotify brand is dark-mode only,
 * and automotive displays benefit from reduced glare at night.
 */
private val CloudBridgeColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = SpotifyBlack,
    secondary = SpotifyGreen,
    onSecondary = SpotifyBlack,
    background = SpotifyBlack,
    onBackground = SpotifyWhite,
    surface = SpotifyDarkSurface,
    onSurface = SpotifyWhite,
    surfaceVariant = SpotifyCardSurface,
    onSurfaceVariant = SpotifyLightGray,
    error = ErrorRed,
    onError = SpotifyWhite,
    outline = SpotifyMediumGray
)

/**
 * Top-level Material 3 theme wrapper for all Compose screens.
 *
 * Applies [CloudBridgeColorScheme] (Spotify brand dark palette) and
 * [CloudBridgeTypography] (automotive-optimised text sizes) to every
 * descendant composable.
 *
 * @param content The composable tree to theme.
 */
@Composable
fun CloudBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CloudBridgeColorScheme,
        typography = CloudBridgeTypography,
        content = content
    )
}
