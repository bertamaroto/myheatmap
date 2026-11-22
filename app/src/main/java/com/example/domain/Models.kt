package com.example.domain

enum class VisualizationMode {
    SOURCE_TRACKS,
    DENSITY_HEATMAP
}

enum class TileStyle {
    DARK_MATTER,
    LIGHT_STREET
}

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val timeIso: String? = null
)

data class SourceCategory(
    val id: String,
    val name: String,
    val colorHex: String,
    val isVisible: Boolean = true,
    val isPreset: Boolean = true,
    val sortOrder: Int = 0
)

data class GpxTrack(
    val id: String,
    val fileName: String,
    val trackName: String,
    val creatorRaw: String?,
    val sourceId: String,
    val recordedAtIso: String?,
    val distanceKm: Double,
    val rawPointCount: Int,
    val points: List<TrackPoint>,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val importedAtMillis: Long
)

data class MapSettings(
    val visualizationMode: VisualizationMode = VisualizationMode.SOURCE_TRACKS,
    val tileStyle: TileStyle = TileStyle.LIGHT_STREET,
    val uiDarkMode: Boolean = false,
    val trackOpacity: Float = 0.80f,
    val trackWidthPx: Float = 3.5f,
    val rdpEnabled: Boolean = false,
    val rdpToleranceMeters: Float = 12f
)

data class SourceSummary(
    val source: SourceCategory,
    val trackCount: Int,
    val totalDistanceKm: Double,
    val rawPointCount: Int,
    val renderedPointCount: Int
)

data class PendingImportTrack(
    val tempId: String,
    val fileName: String,
    val trackName: String,
    val creatorRaw: String?,
    val detectedSourceId: String,
    val selectedSourceId: String,
    val recordedAtIso: String?,
    val distanceKm: Double,
    val points: List<TrackPoint>,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)
