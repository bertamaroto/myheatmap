package com.example.data

import com.example.domain.GpxTrack
import com.example.domain.MapSettings
import com.example.domain.SourceCategory
import com.example.domain.SourcePresets
import com.example.domain.TileStyle
import com.example.domain.TrackPoint
import com.example.domain.VisualizationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

class TrackRepository(
    private val sourceDao: SourceDao,
    private val trackDao: TrackDao,
    private val settingsDao: SettingsDao
) {
    val sources: Flow<List<SourceCategory>> = sourceDao.observeSources().map { list ->
        list.map { it.toDomain() }
    }

    val tracks: Flow<List<GpxTrack>> = trackDao.observeTracks().map { list ->
        list.map { it.toDomain() }
    }

    val settings: Flow<MapSettings> = settingsDao.observeSettings().map { entity ->
        entity?.toDomain() ?: MapSettings()
    }

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (sourceDao.countSources() == 0) {
            sourceDao.insertIfAbsent(SourcePresets.defaultSources.map { it.toEntity() })
        }
    }

    suspend fun saveTracks(tracks: List<GpxTrack>) = withContext(Dispatchers.IO) {
        trackDao.insertTracks(tracks.map { it.toEntity() })
    }

    suspend fun updateSourceVisibility(id: String, isVisible: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.updateVisibility(id, isVisible)
        trackDao.updateVisibilityBySource(id, isVisible)
    }

    suspend fun updateTrackVisibility(trackId: String, isVisible: Boolean, sourceId: String) = withContext(Dispatchers.IO) {
        trackDao.updateTrackVisibility(trackId, isVisible)
        if (isVisible) {
            sourceDao.updateVisibility(sourceId, true)
        }
    }

    suspend fun setAllVisibility(isVisible: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setAllVisibility(isVisible)
        trackDao.setAllVisibility(isVisible)
    }

    suspend fun updateSourceColor(id: String, colorHex: String) = withContext(Dispatchers.IO) {
        sourceDao.updateColor(id, colorHex)
    }

    suspend fun addCustomSource(source: SourceCategory) = withContext(Dispatchers.IO) {
        sourceDao.upsertSource(source.toEntity())
    }

    suspend fun deleteCustomSource(id: String) = withContext(Dispatchers.IO) {
        trackDao.reassignSourceTracks(id, SourcePresets.ID_CUSTOM)
        sourceDao.deleteCustomSource(id)
    }

    suspend fun reassignTrackSource(trackId: String, newSourceId: String) = withContext(Dispatchers.IO) {
        trackDao.updateTrackSource(trackId, newSourceId)
    }

    suspend fun deleteTrack(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTrack(trackId)
    }

    suspend fun deleteTracksBySource(sourceId: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTracksBySource(sourceId)
    }

    suspend fun clearAllTracks() = withContext(Dispatchers.IO) {
        trackDao.clearAllTracks()
    }

    suspend fun saveSettings(settings: MapSettings) = withContext(Dispatchers.IO) {
        settingsDao.upsertSettings(settings.toEntity())
    }

    private fun SourceEntity.toDomain() = SourceCategory(
        id = id,
        name = name,
        colorHex = colorHex,
        isVisible = isVisible,
        isPreset = isPreset,
        sortOrder = sortOrder
    )

    private fun SourceCategory.toEntity() = SourceEntity(
        id = id,
        name = name,
        colorHex = colorHex,
        isVisible = isVisible,
        isPreset = isPreset,
        sortOrder = sortOrder
    )

    private fun GpxTrack.toEntity(): TrackEntity {
        val arr = JSONArray()
        for (p in points) {
            val ptArr = JSONArray()
            ptArr.put(p.lat)
            ptArr.put(p.lon)
            if (p.ele != null || p.timeIso != null) {
                ptArr.put(p.ele ?: JSONObjectNull)
                if (p.timeIso != null) {
                    ptArr.put(p.timeIso)
                }
            }
            arr.put(ptArr)
        }
        return TrackEntity(
            id = id,
            fileName = fileName,
            trackName = trackName,
            creatorRaw = creatorRaw,
            sourceId = sourceId,
            recordedAtIso = recordedAtIso,
            distanceKm = distanceKm,
            rawPointCount = rawPointCount,
            pointsJson = arr.toString(),
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            importedAtMillis = importedAtMillis,
            isVisible = isVisible
        )
    }

    private fun TrackEntity.toDomain(): GpxTrack {
        val pts = mutableListOf<TrackPoint>()
        try {
            val arr = JSONArray(pointsJson)
            for (i in 0 until arr.length()) {
                val ptArr = arr.getJSONArray(i)
                val lat = ptArr.getDouble(0)
                val lon = ptArr.getDouble(1)
                val ele = if (ptArr.length() > 2 && !ptArr.isNull(2)) ptArr.getDouble(2) else null
                val time = if (ptArr.length() > 3 && !ptArr.isNull(3)) ptArr.getString(3) else null
                pts.add(TrackPoint(lat = lat, lon = lon, ele = ele, timeIso = time))
            }
        } catch (_: Exception) {
        }
        return GpxTrack(
            id = id,
            fileName = fileName,
            trackName = trackName,
            creatorRaw = creatorRaw,
            sourceId = sourceId,
            recordedAtIso = recordedAtIso,
            distanceKm = distanceKm,
            rawPointCount = rawPointCount,
            points = pts,
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            importedAtMillis = importedAtMillis,
            isVisible = isVisible
        )
    }

    private fun SettingsEntity.toDomain(): MapSettings {
        return MapSettings(
            visualizationMode = runCatching { VisualizationMode.valueOf(visualizationMode) }
                .getOrDefault(VisualizationMode.SOURCE_TRACKS),
            tileStyle = runCatching { TileStyle.valueOf(tileStyle) }
                .getOrDefault(TileStyle.LIGHT_STREET),
            uiDarkMode = uiDarkMode,
            trackOpacity = trackOpacity,
            trackWidthPx = trackWidthPx,
            rdpEnabled = rdpEnabled,
            rdpToleranceMeters = rdpToleranceMeters,
            mergeExportItems = mergeExportItems
        )
    }

    private fun MapSettings.toEntity() = SettingsEntity(
        id = 1,
        visualizationMode = visualizationMode.name,
        tileStyle = tileStyle.name,
        uiDarkMode = uiDarkMode,
        trackOpacity = trackOpacity,
        trackWidthPx = trackWidthPx,
        rdpEnabled = rdpEnabled,
        rdpToleranceMeters = rdpToleranceMeters,
        mergeExportItems = mergeExportItems
    )

    companion object {
        private val JSONObjectNull = org.json.JSONObject.NULL
    }
}
