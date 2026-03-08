package com.cloudbridge.spotify.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Spotify brand colour palette for the Cloud Bridge app.
 *
 * Colours are sourced from the official Spotify Design Guidelines.
 * The dark surface hierarchy (`SpotifyBlack` → `SpotifyDarkSurface` →
 * `SpotifyCardSurface` → `SpotifyElevatedSurface`) provides subtle
 * elevation cues without relying on shadows, matching the Spotify
 * desktop & mobile clients.
 */

// ── Spotify Brand Palette ────────────────────────────────────────────
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyDarkGreen = Color(0xFF1AA34A)

// ── Surface / Background ─────────────────────────────────────────────
val SpotifyBlack = Color(0xFF000000)
val SpotifyDarkSurface = Color(0xFF121212)
val SpotifyCardSurface = Color(0xFF1A1A1A)
val SpotifyElevatedSurface = Color(0xFF242424)

// ── Text / Icon ──────────────────────────────────────────────────────
val SpotifyWhite = Color(0xFFFFFFFF)
val SpotifyLightGray = Color(0xFFB3B3B3)
val SpotifyMediumGray = Color(0xFF535353)
val SpotifyDarkGray = Color(0xFF282828)

// ── Semantic ─────────────────────────────────────────────────────────
val HeartRed = Color(0xFF1DB954)   // Spotify uses green for saved state
val ErrorRed = Color(0xFFE57373)
val WarningOrange = Color(0xFFFF9800)
