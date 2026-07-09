package com.cloudbridge.spotify.ui

import com.cloudbridge.spotify.network.model.SpotifyPlayableItem

/**
 * UI-friendly representation of a recently-played context (playlist or album).
 *
 * Built by hydrating the raw [PlayContext] URIs returned from
 * `GET /v1/me/player/recently-played` into full metadata via
 * per-item API calls.
 *
 * @property id       The Spotify object ID (e.g. playlist or album ID).
 * @property uri      The full Spotify URI (`spotify:playlist:...`).
 * @property title    Human-readable name of the context.
 * @property subtitle Type label — `"Playlist"` or `"Album"`.
 * @property imageUrl Best-quality cover art URL (may be `null`).
 * @property type     Object type: `"playlist"` or `"album"`.
 */
data class RecentContextItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val type: String
)

/**
 * Unified search result item displayed in the [SearchScreen] grid.
 *
 * Flattens Spotify's separate track / album / playlist search results
 * into a single list for the 4-column grid layout.
 *
 * @property id       The Spotify object ID.
 * @property uri      The full Spotify URI.
 * @property title    Track, album, or playlist name.
 * @property subtitle Artist name(s) for tracks, or type label for albums/playlists.
 * @property imageUrl Best-quality artwork URL (may be `null`).
 * @property type     `"track"`, `"album"`, or `"playlist"`.
 */
data class SearchResultItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val type: String
)

data class QueueItem(
    val uniqueId: String = java.util.UUID.randomUUID().toString(),
    val track: SpotifyPlayableItem
)

data class CustomMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val colorHex: Long
)

data class PodcastUpdateInfo(
    val showId: String,
    val newEpisodeCount: Int,
    val latestEpisodeName: String? = null,
    val latestEpisodeReleaseDate: String? = null
) {
    val hasUpdates: Boolean get() = newEpisodeCount > 0

    val badgeText: String
        get() = when {
            newEpisodeCount <= 0 -> ""
            newEpisodeCount == 1 -> "NEW"
            newEpisodeCount > 9 -> "9+ NEW"
            else -> "$newEpisodeCount NEW"
        }

    fun subtitleSuffix(baseSubtitle: String?): String = when {
        newEpisodeCount <= 0 -> baseSubtitle ?: "Podcast"
        newEpisodeCount == 1 && !baseSubtitle.isNullOrBlank() -> "1 new episode · $baseSubtitle"
        newEpisodeCount == 1 -> "1 new episode"
        !baseSubtitle.isNullOrBlank() -> "$newEpisodeCount new episodes · $baseSubtitle"
        else -> "$newEpisodeCount new episodes"
    }
}
