package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY sortOrder ASC")
    fun observeSources(): Flow<List<SourceEntity>>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun countSources(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(sources: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: SourceEntity)

    @Query("UPDATE sources SET isVisible = :isVisible WHERE id = :id")
    suspend fun updateVisibility(id: String, isVisible: Boolean)

    @Query("UPDATE sources SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateColor(id: String, colorHex: String)

    @Query("DELETE FROM sources WHERE id = :id AND isPreset = 0")
    suspend fun deleteCustomSource(id: String)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY importedAtMillis DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET sourceId = :newSourceId WHERE id = :trackId")
    suspend fun updateTrackSource(trackId: String, newSourceId: String)

    @Query("UPDATE tracks SET sourceId = :fallbackSourceId WHERE sourceId = :deletedSourceId")
    suspend fun reassignSourceTracks(deletedSourceId: String, fallbackSourceId: String)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("DELETE FROM tracks WHERE sourceId = :sourceId")
    suspend fun deleteTracksBySource(sourceId: String)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
}
