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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TrackRepository(
    private val sourceDao: SourceDao,
    private val trackDao: TrackDao,
    private val settingsDao: SettingsDao,
    private val pointsDir: File? = null
) {
    val sources: Flow<List<SourceCategory>> = sourceDao.observeSources().map { list ->
        list.map { it.toDomain() }
    }

    val tracks: Flow<List<GpxTrack>> = trackDao.observeTracks().map { list ->
        withContext(Dispatchers.IO) {
            list.map { it.toDomain() }
        }
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
        deletePointFile(trackId)
    }

    suspend fun deleteTracksBySource(sourceId: String) = withContext(Dispatchers.IO) {
        val ids = trackDao.getTrackIdsBySource(sourceId)
        trackDao.deleteTracksBySource(sourceId)
        ids.forEach { deletePointFile(it) }
    }

    suspend fun clearAllTracks() = withContext(Dispatchers.IO) {
        trackDao.clearAllTracks()
        pointsDir?.listFiles()?.forEach { runCatching { it.delete() } }
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
        val dir = pointsDir
        val encodedPoints = if (dir != null && writePointFile(dir, id, points)) {
            "$FILE_POINTS_PREFIX$id"
        } else {
            val arr = JSONArray()
            for (p in points) {
                if (!p.lat.isFinite() || !p.lon.isFinite()) continue
                val ptArr = JSONArray()
                ptArr.put(p.lat)
                ptArr.put(p.lon)
                val finiteEle = p.ele?.takeIf { it.isFinite() }
                if (finiteEle != null || p.timeIso != null) {
                    ptArr.put(finiteEle ?: JSONObjectNull)
                    if (p.timeIso != null) {
                        ptArr.put(p.timeIso)
                    }
                }
                arr.put(ptArr)
            }
            arr.toString()
        }
        return TrackEntity(
            id = id,
            fileName = fileName,
            trackName = trackName,
            creatorRaw = creatorRaw,
            sourceId = sourceId,
            recordedAtIso = recordedAtIso,
            distanceKm = distanceKm.takeIf { it.isFinite() } ?: 0.0,
            rawPointCount = rawPointCount,
            pointsJson = encodedPoints,
            minLat = minLat.takeIf { it.isFinite() } ?: 0.0,
            maxLat = maxLat.takeIf { it.isFinite() } ?: 0.0,
            minLon = minLon.takeIf { it.isFinite() } ?: 0.0,
            maxLon = maxLon.takeIf { it.isFinite() } ?: 0.0,
            importedAtMillis = importedAtMillis,
            isVisible = isVisible
        )
    }

    private fun TrackEntity.toDomain(): GpxTrack {
        val pts: List<TrackPoint> = if (pointsJson.startsWith(FILE_POINTS_PREFIX)) {
            val fileId = pointsJson.removePrefix(FILE_POINTS_PREFIX)
            val dir = pointsDir
            if (dir != null) readPointFile(dir, fileId) else emptyList()
        } else {
            val parsed = mutableListOf<TrackPoint>()
            try {
                val arr = JSONArray(pointsJson)
                for (i in 0 until arr.length()) {
                    val ptArr = arr.getJSONArray(i)
                    val lat = ptArr.getDouble(0)
                    val lon = ptArr.getDouble(1)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val ele = if (ptArr.length() > 2 && !ptArr.isNull(2)) {
                        ptArr.getDouble(2).takeIf { it.isFinite() }
                    } else null
                    val time = if (ptArr.length() > 3 && !ptArr.isNull(3)) ptArr.getString(3) else null
                    parsed.add(TrackPoint(lat = lat, lon = lon, ele = ele, timeIso = time))
                }
            } catch (_: Exception) {
            }
            parsed
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

    private fun writePointFile(dir: File, trackId: String, points: List<TrackPoint>): Boolean {
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$trackId.bin")
            DataOutputStream(
                GZIPOutputStream(BufferedOutputStream(FileOutputStream(file), 16 * 1024))
            ).use { out ->
                out.writeInt(points.size)
                for (p in points) {
                    out.writeDouble(if (p.lat.isFinite()) p.lat else 0.0)
                    out.writeDouble(if (p.lon.isFinite()) p.lon else 0.0)
                    val finiteEle = p.ele?.takeIf { it.isFinite() }
                    if (finiteEle != null) {
                        out.writeBoolean(true)
                        out.writeDouble(finiteEle)
                    } else {
                        out.writeBoolean(false)
                    }
                    val timeBytes = p.timeIso?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
                    if (timeBytes != null) {
                        out.writeInt(timeBytes.size)
                        out.write(timeBytes)
                    } else {
                        out.writeInt(0)
                    }
                }
            }
        }.isSuccess
    }

    private fun readPointFile(dir: File, trackId: String): List<TrackPoint> {
        val file = File(dir, "$trackId.bin")
        if (!file.exists()) return emptyList()
        return runCatching {
            DataInputStream(
                GZIPInputStream(BufferedInputStream(FileInputStream(file), 16 * 1024))
            ).use { input ->
                val count = input.readInt().coerceAtLeast(0)
                val list = ArrayList<TrackPoint>(count.coerceAtMost(100_000))
                for (i in 0 until count) {
                    val lat = input.readDouble()
                    val lon = input.readDouble()
                    val hasEle = input.readBoolean()
                    val ele = if (hasEle) input.readDouble().takeIf { it.isFinite() } else null
                    val timeLen = input.readInt()
                    val time = if (timeLen > 0 && timeLen < 4096) {
                        val bytes = ByteArray(timeLen)
                        input.readFully(bytes)
                        String(bytes, Charsets.UTF_8)
                    } else {
                        if (timeLen > 0) input.skipBytes(timeLen)
                        null
                    }
                    if (lat.isFinite() && lon.isFinite()) {
                        list.add(TrackPoint(lat = lat, lon = lon, ele = ele, timeIso = time))
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    private fun deletePointFile(trackId: String) {
        pointsDir?.let { dir ->
            runCatching { File(dir, "$trackId.bin").delete() }
        }
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
        private const val FILE_POINTS_PREFIX = "@file:"
    }
}
