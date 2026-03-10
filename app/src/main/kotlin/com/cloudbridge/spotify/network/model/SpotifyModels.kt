package com.cloudbridge.spotify.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Spotify Web API data models.
 *
 * Every data class in this file is annotated with Moshi's
 * `@JsonClass(generateAdapter = true)` for compile-time JSON adapter
 * generation via KSP. Field-level `@Json(name = ...)` maps the
 * snake_case API fields to idiomatic Kotlin camelCase properties.
 *
 * Grouped by domain area: Playlists, Tracks, Albums, Shows/Podcasts,
 * Recently Played, Top Items, Search, Browse Categories, Devices,
 * Playback State, Queue, and Recommendations.
 */

// ─── Playlists ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class PlaylistsResponse(
    @Json(name = "items") val items: List<SpotifyPlaylist?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylist(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null,
    @Json(name = "uri") val uri: String?,
    @Json(name = "tracks") val tracks: PlaylistTracksRef? = null,
    @Json(name = "owner") val owner: SpotifyPlaylistOwner? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistOwner(
    @Json(name = "id") val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null
)

/** Lightweight reference to tracks count (returned inside playlist objects). */
@JsonClass(generateAdapter = true)
data class PlaylistTracksRef(
    @Json(name = "total") val total: Int? = 0
)

// ─── Tracks ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class PlaylistTracksResponse(
    @Json(name = "items") val items: List<PlaylistTrackItem?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistTrackItem(
    @Json(name = "track") val track: SpotifyTrack?
)

@JsonClass(generateAdapter = true)
data class SpotifyTrack(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String,
    @Json(name = "duration_ms") val durationMs: Long,
    @Json(name = "artists") val artists: List<SpotifyArtist> = emptyList(),
    @Json(name = "album") val album: SpotifyAlbum? = null,
    @Json(name = "explicit") val explicit: Boolean = false,
    @Json(name = "is_playable") val isPlayable: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class SpotifyArtist(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null,
    @Json(name = "genres") val genres: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyAlbum(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null,
    @Json(name = "artists") val artists: List<SpotifyArtist>? = null,
    @Json(name = "album_type") val albumType: String? = null,
    @Json(name = "total_tracks") val totalTracks: Int? = null,
    @Json(name = "release_date") val releaseDate: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyImage(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

// ─── Albums ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SavedAlbumsResponse(
    @Json(name = "items") val items: List<SavedAlbumItem?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SavedAlbumItem(
    @Json(name = "added_at") val addedAt: String? = null,
    @Json(name = "album") val album: SpotifyAlbum
)

@JsonClass(generateAdapter = true)
data class SavedTracksResponse(
    @Json(name = "items") val items: List<SavedTrackItem?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SavedTrackItem(
    @Json(name = "added_at") val addedAt: String? = null,
    @Json(name = "track") val track: SpotifyTrack
)

@JsonClass(generateAdapter = true)
data class AlbumTracksResponse(
    @Json(name = "items") val items: List<SpotifyTrack?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

// ─── Shows / Podcasts ────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SavedShowsResponse(
    @Json(name = "items") val items: List<SavedShowItem?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SavedShowItem(
    @Json(name = "added_at") val addedAt: String? = null,
    @Json(name = "show") val show: SpotifyShow
)

@JsonClass(generateAdapter = true)
data class SpotifyShow(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "publisher") val publisher: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null,
    @Json(name = "uri") val uri: String
)

@JsonClass(generateAdapter = true)
data class SpotifyAudiobook(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String,
    @Json(name = "authors") val authors: List<SpotifyAuthor>? = null,
    @Json(name = "publisher") val publisher: String? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyAuthor(
    @Json(name = "name") val name: String
)

// ─── Recently Played ─────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RecentlyPlayedResponse(
    @Json(name = "items") val items: List<PlayHistoryItem?>,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class PlayHistoryItem(
    @Json(name = "track") val track: SpotifyTrack,
    @Json(name = "played_at") val playedAt: String? = null,
    @Json(name = "context") val context: PlayContext? = null
)

@JsonClass(generateAdapter = true)
data class PlayContext(
    @Json(name = "type") val type: String? = null,
    @Json(name = "uri") val uri: String? = null
)

// ─── Featured / Editorial Playlists ──────────────────────────────────

@JsonClass(generateAdapter = true)
data class FeaturedPlaylistsResponse(
    @Json(name = "message") val message: String? = null,
    @Json(name = "playlists") val playlists: PlaylistsResponse
)

// ─── Top Items ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class TopArtistsResponse(
    @Json(name = "items") val items: List<SpotifyArtist?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class TopTracksResponse(
    @Json(name = "items") val items: List<SpotifyTrack?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

// ─── New Releases ────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class NewReleasesResponse(
    @Json(name = "albums") val albums: NewReleasesAlbums
)

@JsonClass(generateAdapter = true)
data class NewReleasesAlbums(
    @Json(name = "items") val items: List<SpotifyAlbum?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

// ─── Artist Albums ───────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ArtistAlbumsResponse(
    @Json(name = "items") val items: List<SpotifyAlbum?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

// ─── Search ─────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "tracks") val tracks: SearchTracks? = null,
    @Json(name = "albums") val albums: SearchAlbums? = null,
    @Json(name = "playlists") val playlists: SearchPlaylists? = null
)

@JsonClass(generateAdapter = true)
data class SearchTracks(
    @Json(name = "items") val items: List<SpotifyTrack?> = emptyList(),
    @Json(name = "total") val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class SearchAlbums(
    @Json(name = "items") val items: List<SpotifyAlbum?> = emptyList(),
    @Json(name = "total") val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class SearchPlaylists(
    @Json(name = "items") val items: List<SpotifyPlaylist?> = emptyList(),
    @Json(name = "total") val total: Int = 0
)

// ─── Browse Categories ───────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CategoriesResponse(
    @Json(name = "categories") val categories: CategoriesList
)

@JsonClass(generateAdapter = true)
data class CategoriesList(
    @Json(name = "items") val items: List<SpotifyCategory?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyCategory(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "icons") val icons: List<SpotifyImage>? = null
)

// ─── Devices ─────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class DevicesResponse(
    @Json(name = "devices") val devices: List<SpotifyDevice>
)

@JsonClass(generateAdapter = true)
data class SpotifyDevice(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "is_restricted") val isRestricted: Boolean,
    @Json(name = "volume_percent") val volumePercent: Int? = null
)

// ─── Unified Playable Item (Track or Episode) ───────────────────────────────

/**
 * A unified model that can represent a Spotify track, podcast episode, or audiobook chapter.
 *
 * The Spotify Queue and Now Playing APIs return items that can be `type: "track"`
 * (with `album`, `artists`), `type: "episode"` (with `show`, `images`),
 * or `type: "chapter"` (with `audiobook`, `images`).
 * This single model accepts fields for both, with nullable defaults so Moshi
 * gracefully handles whichever type is returned.
 */
@JsonClass(generateAdapter = true)
data class SpotifyPlayableItem(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String,
    @Json(name = "type") val type: String = "track", // "track", "episode", or "chapter"
    @Json(name = "duration_ms") val durationMs: Long = 0L,
    @Json(name = "explicit") val explicit: Boolean = false,
    // Track-specific fields
    @Json(name = "artists") val artists: List<SpotifyArtist>? = null,
    @Json(name = "album") val album: SpotifyAlbum? = null,
    // Episode-specific fields
    @Json(name = "show") val show: SpotifyShow? = null,
    // Chapter-specific fields
    @Json(name = "audiobook") val audiobook: SpotifyAudiobook? = null,
    @Json(name = "images") val images: List<SpotifyImage>? = null
)

// ─── Playback State (for metadata sync) ──────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CurrentPlaybackResponse(
    @Json(name = "is_playing") val isPlaying: Boolean,
    @Json(name = "progress_ms") val progressMs: Long? = null,
    @Json(name = "item") val item: SpotifyPlayableItem? = null,
    @Json(name = "device") val device: SpotifyDevice? = null,
    @Json(name = "shuffle_state") val shuffleState: Boolean? = null,
    @Json(name = "repeat_state") val repeatState: String? = null
)

// ─── Play Request Body ───────────────────────────────────────────────

/**
 * Body for PUT /v1/me/player/play.
 *
 * Cloud-bridge strategy:
 * - To play a track within a playlist context, set [contextUri] to the
 *   playlist URI and [offset] to the track's position or URI.
 * - To play a standalone track, set [uris] to a single-element list.
 */
@JsonClass(generateAdapter = true)
data class PlayRequest(
    @Json(name = "context_uri") val contextUri: String? = null,
    @Json(name = "uris") val uris: List<String>? = null,
    @Json(name = "offset") val offset: PlayOffset? = null,
    @Json(name = "position_ms") val positionMs: Int? = null
)

@JsonClass(generateAdapter = true)
data class PlayOffset(
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "position") val position: Int? = null
)

// ─── Transfer Playback ───────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class TransferPlaybackRequest(
    @Json(name = "device_ids") val deviceIds: List<String>,
    @Json(name = "play") val play: Boolean = true
)

// ─── Queue ───────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class QueueResponse(
    @Json(name = "currently_playing") val currentlyPlaying: SpotifyPlayableItem?,
    @Json(name = "queue") val queue: List<SpotifyPlayableItem>
)

// ─── Recommendations ────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RecommendationsResponse(
    @Json(name = "tracks") val tracks: List<SpotifyTrack> = emptyList()
)

// ─── Followed Artists (cursor-based paging) ─────────────────────────

@JsonClass(generateAdapter = true)
data class FollowedArtistsResponse(
    @Json(name = "artists") val artists: ArtistsCursorPage
)

@JsonClass(generateAdapter = true)
data class ArtistsCursorPage(
    @Json(name = "items") val items: List<SpotifyArtist?>,
    @Json(name = "next") val next: String? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "cursors") val cursors: CursorObject? = null,
    @Json(name = "limit") val limit: Int? = null
)

@JsonClass(generateAdapter = true)
data class CursorObject(
    @Json(name = "after") val after: String? = null
)

// ─── Artist Top Tracks ───────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ArtistTopTracksResponse(
    @Json(name = "tracks") val tracks: List<SpotifyTrack> = emptyList()
)

// ─── Show Episodes ───────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ShowEpisodesResponse(
    @Json(name = "items") val items: List<SpotifyEpisode?>,
    @Json(name = "total") val total: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "offset") val offset: Int,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyEpisode(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "uri") val uri: String,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "duration_ms") val durationMs: Long = 0,
    @Json(name = "images") val images: List<SpotifyImage>? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "resume_point") val resumePoint: ResumePoint? = null
)

@JsonClass(generateAdapter = true)
data class ResumePoint(
    @Json(name = "fully_played") val fullyPlayed: Boolean,
    @Json(name = "resume_position_ms") val resumePositionMs: Long
)
