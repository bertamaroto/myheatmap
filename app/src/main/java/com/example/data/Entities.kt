package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val isVisible: Boolean,
    val isPreset: Boolean,
    val sortOrder: Int
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val trackName: String,
    val creatorRaw: String?,
    val sourceId: String,
    val recordedAtIso: String?,
    val distanceKm: Double,
    val rawPointCount: Int,
    val pointsJson: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val importedAtMillis: Long
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val visualizationMode: String,
    val tileStyle: String,
    val uiDarkMode: Boolean = false,
    val trackOpacity: Float,
    val trackWidthPx: Float,
    val rdpEnabled: Boolean,
    val rdpToleranceMeters: Float
)
