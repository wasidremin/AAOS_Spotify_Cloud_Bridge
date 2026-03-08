package com.cloudbridge.spotify.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ─── Entities ────────────────────────────────────────────────────────────────

/**
 * Cached playlist entry: stores just the fields needed to render a [AlbumArtTile]
 * in the Library screen without hitting the network.
 */
@Entity(tableName = "cached_playlists")
data class CachedPlaylist(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String?,
    val description: String?,
    val trackCount: Int
)

/**
 * Cached saved-album entry: stores the fields rendered in the Library albums row.
 */
@Entity(tableName = "cached_albums")
data class CachedAlbum(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String?,
    val artistName: String?,
    val albumType: String?,
    val releaseDate: String?
)

/**
 * Cached saved-show (podcast) entry: stores fields rendered in the Library podcasts row.
 */
@Entity(tableName = "cached_shows")
data class CachedShow(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String?,
    val publisher: String?,
    val description: String?
)

@Entity(tableName = "pinned_items")
data class PinnedItem(
    @PrimaryKey val uri: String,
    val id: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    val type: String,
    val orderIndex: Int
)

@Entity(tableName = "clean_track_mappings")
data class CleanTrackMapping(
    @PrimaryKey val explicitUri: String,
    val replacementUri: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val clientId: String,
    val clientSecret: String?,
    val refreshToken: String,
    val accessToken: String?,
    val tokenExpiryEpochMs: Long,
    val profileImageUrl: String?
)

// ─── DAO ─────────────────────────────────────────────────────────────────────

@androidx.room.Dao
interface LibraryCacheDao {

    // ── Playlists ─────────────────────────────────────────────────────

    @androidx.room.Query("SELECT * FROM cached_playlists")
    suspend fun getAllPlaylists(): List<CachedPlaylist>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<CachedPlaylist>)

    @androidx.room.Query("DELETE FROM cached_playlists")
    suspend fun clearPlaylists()

    // ── Albums ────────────────────────────────────────────────────────

    @androidx.room.Query("SELECT * FROM cached_albums")
    suspend fun getAllAlbums(): List<CachedAlbum>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<CachedAlbum>)

    @androidx.room.Query("DELETE FROM cached_albums")
    suspend fun clearAlbums()

    // ── Shows ─────────────────────────────────────────────────────────

    @androidx.room.Query("SELECT * FROM cached_shows")
    suspend fun getAllShows(): List<CachedShow>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertShows(shows: List<CachedShow>)

    @androidx.room.Query("DELETE FROM cached_shows")
    suspend fun clearShows()

    @androidx.room.Transaction
    suspend fun clearAll() {
        clearPlaylists()
        clearAlbums()
        clearShows()
    }
}

@androidx.room.Dao
interface PinnedItemDao {

    @androidx.room.Query("SELECT * FROM pinned_items ORDER BY orderIndex ASC")
    fun getAllPinned(): Flow<List<PinnedItem>>

    @androidx.room.Query("SELECT * FROM pinned_items ORDER BY orderIndex ASC")
    suspend fun getAllPinnedSync(): List<PinnedItem>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(item: PinnedItem)

    @androidx.room.Query("DELETE FROM pinned_items WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @androidx.room.Update
    suspend fun updateAll(items: List<PinnedItem>)

    @androidx.room.Query("DELETE FROM pinned_items")
    suspend fun clearAll()
}

@androidx.room.Dao
interface CleanTrackMappingDao {

    @androidx.room.Query("SELECT replacementUri FROM clean_track_mappings WHERE explicitUri = :explicitUri LIMIT 1")
    suspend fun getReplacementUri(explicitUri: String): String?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: CleanTrackMapping)
}

@androidx.room.Dao
interface UserProfileDao {

    @androidx.room.Query("SELECT * FROM user_profiles ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<UserProfile>>

    @androidx.room.Query("SELECT * FROM user_profiles ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<UserProfile>

    @androidx.room.Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserProfile?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfile)

    @androidx.room.Update
    suspend fun update(profile: UserProfile)

    @androidx.room.Delete
    suspend fun delete(profile: UserProfile)

    @androidx.room.Query("DELETE FROM user_profiles")
    suspend fun clearAll()
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [CachedPlaylist::class, CachedAlbum::class, CachedShow::class, PinnedItem::class, CleanTrackMapping::class, UserProfile::class],
    version = 4,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun libraryCacheDao(): LibraryCacheDao

    abstract fun pinnedItemDao(): PinnedItemDao

    abstract fun cleanTrackMappingDao(): CleanTrackMappingDao

    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile private var INSTANCE: CacheDatabase? = null

        fun getInstance(context: Context): CacheDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "cloudbridge_cache.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
