package com.cloudbridge.spotify.ui.coordinator

import com.cloudbridge.spotify.cache.CacheDatabase
import com.cloudbridge.spotify.cache.PinnedItem
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyPlaylist
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.ui.RecentContextItem
import com.cloudbridge.spotify.ui.SearchResultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns pinned-favorites CRUD. Pinnable types are reusable library destinations only.
 */
class PinController(
    private val cacheDb: CacheDatabase,
    private val scope: CoroutineScope
) {
    companion object {
        private val PINNABLE_TYPES = setOf("playlist", "album", "show")
    }

    val pinnedItems: StateFlow<List<PinnedItem>> = cacheDb.pinnedItemDao().getAllPinned()
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    fun isPinnableType(type: String): Boolean = type in PINNABLE_TYPES

    fun togglePinForPlaylist(playlist: SpotifyPlaylist) {
        togglePin(
            id = playlist.id.orEmpty(),
            name = playlist.name.orEmpty(),
            uri = playlist.uri.orEmpty(),
            subtitle = "${playlist.itemCount} tracks",
            imageUrl = playlist.images?.firstOrNull()?.url,
            type = "playlist"
        )
    }

    fun togglePinForAlbum(album: SpotifyAlbum) {
        togglePin(
            id = album.id.orEmpty(),
            name = album.name,
            uri = album.uri.orEmpty(),
            subtitle = album.artists?.joinToString(", ") { it.name },
            imageUrl = album.images?.firstOrNull()?.url,
            type = "album"
        )
    }

    fun togglePinForShow(show: SpotifyShow) {
        togglePin(
            id = show.id,
            name = show.name,
            uri = show.uri,
            subtitle = show.publisher,
            imageUrl = show.images?.firstOrNull()?.url,
            type = "show"
        )
    }

    fun togglePinForRecentContext(item: RecentContextItem) {
        when (item.type) {
            "playlist", "album" -> togglePin(
                id = item.id,
                name = item.title,
                uri = item.uri,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                type = item.type
            )
        }
    }

    fun togglePinForSearchResult(item: SearchResultItem) {
        when (item.type) {
            "playlist", "album" -> togglePin(
                id = item.id,
                name = item.title,
                uri = item.uri,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                type = item.type
            )
        }
    }

    fun togglePin(
        id: String,
        name: String,
        uri: String,
        subtitle: String?,
        imageUrl: String?,
        type: String
    ) {
        scope.launch(Dispatchers.IO) {
            if (!isPinnableType(type) || id.isBlank() || uri.isBlank() || name.isBlank()) return@launch
            val dao = cacheDb.pinnedItemDao()
            val currentPins = dao.getAllPinnedSync()
            if (currentPins.any { it.uri == uri }) {
                dao.deleteByUri(uri)
                dao.updateAll(
                    currentPins.filterNot { it.uri == uri }
                        .mapIndexed { index, item -> item.copy(orderIndex = index) }
                )
            } else {
                dao.insert(PinnedItem(uri, id, name, subtitle, imageUrl, type, currentPins.size))
            }
        }
    }

    fun movePinUp(uri: String) {
        scope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            val current = dao.getAllPinnedSync().toMutableList()
            val index = current.indexOfFirst { it.uri == uri }
            if (index > 0) {
                val temp = current[index]
                current[index] = current[index - 1]
                current[index - 1] = temp
                current.forEachIndexed { i, item -> current[i] = item.copy(orderIndex = i) }
                dao.updateAll(current)
            }
        }
    }

    fun movePinDown(uri: String) {
        scope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            val current = dao.getAllPinnedSync().toMutableList()
            val index = current.indexOfFirst { it.uri == uri }
            if (index >= 0 && index < current.size - 1) {
                val temp = current[index]
                current[index] = current[index + 1]
                current[index + 1] = temp
                current.forEachIndexed { i, item -> current[i] = item.copy(orderIndex = i) }
                dao.updateAll(current)
            }
        }
    }

    fun removePin(uri: String) {
        scope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            dao.deleteByUri(uri)
            dao.updateAll(
                dao.getAllPinnedSync().filterNot { it.uri == uri }
                    .mapIndexed { index, item -> item.copy(orderIndex = index) }
            )
        }
    }
}
