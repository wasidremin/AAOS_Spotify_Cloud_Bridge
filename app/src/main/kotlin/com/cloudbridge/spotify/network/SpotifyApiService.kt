package com.cloudbridge.spotify.network

import com.cloudbridge.spotify.network.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Spotify Web API (api.spotify.com).
 *
 * All methods are suspend functions for Coroutine integration.
 * The Bearer token is automatically added by [AuthInterceptor].
 * 401 responses trigger automatic refresh via [TokenRefreshAuthenticator].
 *
 * Key Cloud-Bridge insight: Every playback control endpoint includes
 * a `device_id` parameter to target the user's phone. Without this,
 * Spotify would try to play on the last active device, which might
 * be the car's built-in Spotify (if installed) or another device entirely.
 */
interface SpotifyApiService {

    // ── Library Browsing ─────────────────────────────────────────────

    @GET("v1/me/playlists")
    suspend fun getPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PlaylistsResponse

    @GET("v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PlaylistTracksResponse

    @GET("v1/playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") playlistId: String
    ): SpotifyPlaylist

    @GET("v1/me/albums")
    suspend fun getSavedAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SavedAlbumsResponse

    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SavedTracksResponse

    @GET("v1/albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): AlbumTracksResponse

    @GET("v1/albums/{id}")
    suspend fun getAlbum(
        @Path("id") albumId: String
    ): SpotifyAlbum

    @GET("v1/tracks/{id}")
    suspend fun getTrack(
        @Path("id") trackId: String
    ): SpotifyTrack

    @GET("v1/me/shows")
    suspend fun getSavedShows(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SavedShowsResponse

    // ── Home / Discovery ─────────────────────────────────────────────

    /** Recently played tracks — powers "Jump Back In" / "Recently Played" */
    @GET("v1/me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 20
    ): RecentlyPlayedResponse

    /** Featured / editorial playlists — powers "Made for You" section */
    // NOTE: /v1/browse/featured-playlists is deprecated for dev apps (returns 403).
    // ViewModel falls back to a top-artist search-based approach instead.
    @GET("v1/browse/featured-playlists")
    suspend fun getFeaturedPlaylists(
        @Query("limit") limit: Int = 20
    ): FeaturedPlaylistsResponse

    /** Albums by a specific artist — used to synthesise "New Releases from Your Artists" */
    @GET("v1/artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") artistId: String,
        @Query("limit") limit: Int = 4,
        @Query("include_groups") includeGroups: String = "album,single"
    ): ArtistAlbumsResponse

    /** User's top artists — powers "Your Top Artists" */
    @GET("v1/me/top/artists")
    suspend fun getTopArtists(
        @Query("limit") limit: Int = 20,
        @Query("time_range") timeRange: String = "medium_term"
    ): TopArtistsResponse

    /** Followed artists — cursor-based paging */
    @GET("v1/me/following")
    suspend fun getFollowedArtists(
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 50,
        @Query("after") after: String? = null
    ): FollowedArtistsResponse

    /** Artist's top tracks */
    @GET("v1/artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(
        @Path("id") artistId: String,
        @Query("market") market: String = "US"
    ): ArtistTopTracksResponse

    @GET("v1/artists/{id}")
    suspend fun getArtist(
        @Path("id") artistId: String
    ): SpotifyArtist

    /** Episodes for a specific show (podcast) */
    @GET("v1/shows/{id}/episodes")
    suspend fun getShowEpisodes(
        @Path("id") showId: String,
        @Query("limit") limit: Int = 1,
        @Query("offset") offset: Int = 0
    ): ShowEpisodesResponse

    /** User's top tracks — powers "Your Top Tracks" */
    @GET("v1/me/top/tracks")
    suspend fun getTopTracks(
        @Query("limit") limit: Int = 20,
        @Query("time_range") timeRange: String = "medium_term"
    ): TopTracksResponse

    /** New album releases — powers "New Releases" section */
    @GET("v1/browse/new-releases")
    suspend fun getNewReleases(
        @Query("limit") limit: Int = 20
    ): NewReleasesResponse

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type", encoded = true) type: String = "track,album,playlist",
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("v1/recommendations")
    suspend fun getRecommendations(
        @Query("limit") limit: Int = 50,
        @Query("seed_tracks", encoded = true) seedTracks: String
    ): RecommendationsResponse

    /** Browse categories — powers "Browse" tab */
    @GET("v1/browse/categories")
    suspend fun getCategories(
        @Query("limit") limit: Int = 50
    ): CategoriesResponse

    /** Playlists for a specific browse category */
    @GET("v1/browse/categories/{id}/playlists")
    suspend fun getCategoryPlaylists(
        @Path("id") categoryId: String,
        @Query("limit") limit: Int = 20
    ): FeaturedPlaylistsResponse

    // ── Device Discovery ─────────────────────────────────────────────

    /**
     * Fetch available Spotify Connect devices.
     * We filter for `type == "Smartphone"` to find the user's phone.
     */
    @GET("v1/me/player/devices")
    suspend fun getDevices(): DevicesResponse

    // ── Playback Control ─────────────────────────────────────────────
    // These are the "cloud-bridge" commands: instead of playing audio
    // locally, we send REST commands to Spotify to play on the phone.

    /**
     * Transfer playback to a new device and optionally start playing.
     * This is an aggressive wake-up command that bypasses the active session requirement.
     */
    @PUT("v1/me/player")
    suspend fun transferPlayback(
        @Body request: com.cloudbridge.spotify.network.model.TransferPlaybackRequest
    ): retrofit2.Response<Unit>

    /**
     * Start or resume playback on the target device.
     *
     * @param deviceId The phone's Spotify device ID
     * @param body Optional: specify what to play (playlist URI + track offset)
     */
    @PUT("v1/me/player/play")
    suspend fun play(
        @Query("device_id") deviceId: String? = null,
        @Body body: PlayRequest? = null
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pause(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("v1/me/player/next")
    suspend fun next(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun previous(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    /** Toggle shuffle on/off */
    @PUT("v1/me/player/shuffle")
    suspend fun setShuffle(
        @Query("state") state: Boolean,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    /** Set repeat mode: "track", "context", or "off" */
    @PUT("v1/me/player/repeat")
    suspend fun setRepeat(
        @Query("state") state: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    /** Seek to position in ms within the current track */
    @PUT("v1/me/player/seek")
    suspend fun seek(
        @Query("position_ms") positionMs: Long,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    // ── Playback State (for metadata sync) ───────────────────────────

    /**
     * Get the user's current playback state.
     * Used to sync the car screen's now-playing card with what's
     * actually playing on the phone.
     *
     * Returns 204 No Content if nothing is playing.
     */
    @GET("v1/me/player")
    suspend fun getCurrentPlayback(
        @Query("additional_types") additionalTypes: String = "track,episode,chapter"
    ): Response<CurrentPlaybackResponse>

    // ── Queue Management ─────────────────────────────────────────────

    /** Get the user's current playback queue. */
    @GET("v1/me/player/queue")
    suspend fun getQueue(): QueueResponse

    /** Add an item to the end of the user's playback queue. */
    @POST("v1/me/player/queue")
    suspend fun addToQueue(
        @Query("uri") uri: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    // ── Library — Save / Remove Tracks ───────────────────────────────

    /** Save (heart) one or more tracks. Requires user-library-modify scope. */
    @PUT("v1/me/tracks")
    suspend fun saveTracks(
        @Query("ids") ids: String   // comma-separated track IDs
    ): Response<Unit>

    /** Un-save (un-heart) one or more tracks. Requires user-library-modify scope. */
    @DELETE("v1/me/tracks")
    suspend fun removeTracks(
        @Query("ids") ids: String
    ): Response<Unit>

    /** Check if one or more tracks are saved in the user's library. */
    @GET("v1/me/tracks/contains")
    suspend fun checkSavedTracks(
        @Query("ids") ids: String   // comma-separated track IDs
    ): List<Boolean>
}
