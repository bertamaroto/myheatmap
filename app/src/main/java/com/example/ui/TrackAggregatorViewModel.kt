package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TrackAggregatorDatabase
import com.example.data.TrackRepository
import com.example.domain.DemoTrackGenerator
import com.example.domain.GeoMath
import com.example.domain.GpxAndGeoJsonExporter
import com.example.domain.GpxTrack
import com.example.domain.GpxXmlParser
import com.example.domain.MapSettings
import com.example.domain.PendingImportTrack
import com.example.domain.SourceCategory
import com.example.domain.SourcePresets
import com.example.domain.SourceSummary
import com.example.domain.TileStyle
import com.example.domain.VisualizationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

enum class ExportFormat(val extension: String, val mimeType: String) {
    KMZ("kmz", "application/vnd.google-earth.kmz"),
    KML("kml", "application/vnd.google-earth.kml+xml"),
    GPX("gpx", "application/gpx+xml"),
    GEOJSON("geojson", "application/geo+json")
}

data class MainUiState(
    val sources: List<SourceCategory> = SourcePresets.defaultSources,
    val tracks: List<GpxTrack> = emptyList(),
    val settings: MapSettings = MapSettings(),
    val sourceSummaries: List<SourceSummary> = emptyList(),
    val visibleTrackCount: Int = 0,
    val visibleDistanceKm: Double = 0.0,
    val totalRawPoints: Int = 0,
    val totalRenderedPoints: Int = 0,
    val mapPayloadJson: String = "{}",
    val pendingImports: List<PendingImportTrack> = emptyList(),
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    val editingColorSource: SourceCategory? = null,
    val showExportDialog: Boolean = false,
    val showCreateSourceDialog: Boolean = false
)

private data class TransientUiState(
    val pendingImports: List<PendingImportTrack> = emptyList(),
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    val fitBoundsNonce: Int = 0,
    val editingColorSourceId: String? = null,
    val showExportDialog: Boolean = false,
    val showCreateSourceDialog: Boolean = false
)

class TrackAggregatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TrackRepository
    private val transientState = MutableStateFlow(TransientUiState())

    init {
        val db = TrackAggregatorDatabase.getInstance(application)
        val pointsDir = File(application.filesDir, "track_points")
        repository = TrackRepository(db.sourceDao(), db.trackDao(), db.settingsDao(), pointsDir)
        viewModelScope.launch {
            runCatching { repository.ensureInitialized() }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        repository.sources,
        repository.tracks,
        repository.settings,
        transientState
    ) { sources, tracks, settings, transient ->
        val sourcesById = sources.associateBy { it.id }
        val groupedBySource = tracks.groupBy { it.sourceId }

        val summaries = sources.map { src ->
            val srcTracks = groupedBySource[src.id].orEmpty()
            val visibleSrcTracks = srcTracks.filter { it.isVisible && src.isVisible }
            val rawPts = srcTracks.sumOf { it.rawPointCount }
            val renderedPts = if (settings.rdpEnabled) {
                visibleSrcTracks.sumOf { GeoMath.simplifyRdp(it.points, settings.rdpToleranceMeters.toDouble()).size }
            } else {
                visibleSrcTracks.sumOf { it.rawPointCount }
            }
            SourceSummary(
                source = src,
                trackCount = srcTracks.size,
                totalDistanceKm = srcTracks.sumOf { it.distanceKm },
                rawPointCount = rawPts,
                renderedPointCount = renderedPts
            )
        }

        val visibleTracks = tracks.filter { track ->
            track.isVisible && (sourcesById[track.sourceId]?.isVisible ?: true)
        }

        var visibleRawPts = 0
        var visibleRenderedPts = 0
        val tracksJson = JSONArray()

        for (track in visibleTracks) {
            val src = sourcesById[track.sourceId]
            val colorHex = src?.colorHex ?: "#E879F9"
            val sourceName = src?.name ?: "Custom"
            val pts = if (settings.rdpEnabled) {
                GeoMath.simplifyRdp(track.points, settings.rdpToleranceMeters.toDouble())
            } else {
                track.points
            }
            visibleRawPts += track.rawPointCount
            visibleRenderedPts += pts.size

            val displayPts = GeoMath.decimateForDisplay(pts, MAX_DISPLAY_POINTS_PER_TRACK)
            val ptsArray = JSONArray()
            for (pt in displayPts) {
                if (!pt.lat.isFinite() || !pt.lon.isFinite()) continue
                ptsArray.put(JSONArray().apply {
                    put(pt.lat)
                    put(pt.lon)
                })
            }

            tracksJson.put(JSONObject().apply {
                put("id", track.id)
                put("trackName", track.trackName)
                put("sourceName", sourceName)
                put("color", colorHex)
                put("distanceLabel", String.format(Locale.US, "%.2f km", track.distanceKm))
                put("pointCountLabel", "${pts.size} pts")
                put("dateLabel", track.recordedAtIso?.take(10) ?: "")
                put("pts", ptsArray)
            })
        }

        val payload = JSONObject().apply {
            put("mode", settings.visualizationMode.name)
            put("tileStyle", settings.tileStyle.name)
            put("opacity", settings.trackOpacity.toDouble())
            put("weight", settings.trackWidthPx.toDouble())
            put("fitBoundsNonce", transient.fitBoundsNonce)
            put("tracks", tracksJson)
        }
        val safePayloadJson = payload.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        MainUiState(
            sources = sources,
            tracks = tracks,
            settings = settings,
            sourceSummaries = summaries,
            visibleTrackCount = visibleTracks.size,
            visibleDistanceKm = visibleTracks.sumOf { it.distanceKm },
            totalRawPoints = visibleRawPts,
            totalRenderedPoints = visibleRenderedPts,
            mapPayloadJson = safePayloadJson,
            pendingImports = transient.pendingImports,
            isLoading = transient.isLoading,
            snackbarMessage = transient.snackbarMessage,
            editingColorSource = transient.editingColorSourceId?.let { sourcesById[it] },
            showExportDialog = transient.showExportDialog,
            showCreateSourceDialog = transient.showCreateSourceDialog
        )
    }
        .flowOn(Dispatchers.Default)
        .catch { err ->
            emit(MainUiState(snackbarMessage = err.localizedMessage ?: "Failed to update map state."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState()
        )

    fun importGpxUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentSources = uiState.value.sources
        viewModelScope.launch {
            transientState.update { it.copy(isLoading = true) }
            val parsed = mutableListOf<PendingImportTrack>()
            var failedCount = 0
            var errorDetail: String? = null

            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                for (uri in uris) {
                    try {
                        val fileName = resolveFileName(uri) ?: "track.gpx"
                        val stream = resolver.openInputStream(uri)
                        if (stream == null) {
                            failedCount++
                            continue
                        }
                        stream.use { input ->
                            val tracksInFile = GpxXmlParser.parseStream(input, fileName, currentSources)
                            if (tracksInFile.isEmpty()) {
                                failedCount++
                            } else {
                                parsed.addAll(tracksInFile)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedCount++
                        if (errorDetail == null) errorDetail = e.localizedMessage
                    } catch (oom: OutOfMemoryError) {
                        failedCount++
                        errorDetail = "File is too large for available memory."
                    }
                }
            }

            if (parsed.isEmpty()) {
                transientState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = when {
                            errorDetail != null -> "Could not read GPX file: $errorDetail"
                            failedCount > 0 -> "No valid GPX track points found in selected files."
                            else -> "No GPX files selected."
                        }
                    )
                }
            } else {
                transientState.update {
                    it.copy(
                        isLoading = false,
                        pendingImports = parsed,
                        snackbarMessage = if (failedCount > 0) {
                            "Parsed ${parsed.size} track(s); $failedCount file(s) had no track points."
                        } else null
                    )
                }
            }
        }
    }

    fun updatePendingImportSource(tempId: String, newSourceId: String) {
        transientState.update { state ->
            state.copy(
                pendingImports = state.pendingImports.map { item ->
                    if (item.tempId == tempId) item.copy(selectedSourceId = newSourceId) else item
                }
            )
        }
    }

    fun setAllPendingImportsSource(newSourceId: String) {
        transientState.update { state ->
            state.copy(
                pendingImports = state.pendingImports.map { item ->
                    item.copy(selectedSourceId = newSourceId)
                }
            )
        }
    }

    fun cancelPendingImports() {
        transientState.update { it.copy(pendingImports = emptyList()) }
    }

    fun confirmPendingImports() {
        val pending = transientState.value.pendingImports
        if (pending.isEmpty()) return
        viewModelScope.launch {
            transientState.update { it.copy(isLoading = true, pendingImports = emptyList()) }
            val result = runCatching {
                val now = System.currentTimeMillis()
                val newTracks = pending.mapIndexed { idx, item ->
                    GpxTrack(
                        id = UUID.randomUUID().toString(),
                        fileName = item.fileName,
                        trackName = item.trackName,
                        creatorRaw = item.creatorRaw,
                        sourceId = item.selectedSourceId,
                        recordedAtIso = item.recordedAtIso,
                        distanceKm = item.distanceKm,
                        rawPointCount = item.points.size,
                        points = item.points,
                        minLat = item.minLat,
                        maxLat = item.maxLat,
                        minLon = item.minLon,
                        maxLon = item.maxLon,
                        importedAtMillis = now + idx
                    )
                }
                // Ensure selected sources are visible so newly imported tracks show immediately
                val selectedSourceIds = newTracks.map { it.sourceId }.toSet()
                selectedSourceIds.forEach { srcId ->
                    repository.updateSourceVisibility(srcId, true)
                }
                repository.saveTracks(newTracks)
                newTracks.size
            }
            result.onSuccess { count ->
                transientState.update {
                    it.copy(
                        isLoading = false,
                        fitBoundsNonce = it.fitBoundsNonce + 1,
                        snackbarMessage = "Imported $count GPX track(s) to map."
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                transientState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "Failed to save imported tracks: ${err.localizedMessage ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun loadDemoTracks() {
        viewModelScope.launch {
            transientState.update { it.copy(isLoading = true) }
            val demoTracks = withContext(Dispatchers.Default) {
                DemoTrackGenerator.generateDemoTracks()
            }
            SourcePresets.defaultSources.forEach { src ->
                repository.updateSourceVisibility(src.id, true)
            }
            repository.saveTracks(demoTracks)
            transientState.update {
                it.copy(
                    isLoading = false,
                    fitBoundsNonce = it.fitBoundsNonce + 1,
                    snackbarMessage = "Loaded ${demoTracks.size} multi-source demo tracks."
                )
            }
        }
    }

    fun toggleSourceVisibility(sourceId: String) {
        val current = uiState.value.sources.find { it.id == sourceId } ?: return
        viewModelScope.launch {
            repository.updateSourceVisibility(sourceId, !current.isVisible)
        }
    }

    fun toggleTrackVisibility(trackId: String) {
        val track = uiState.value.tracks.find { it.id == trackId } ?: return
        viewModelScope.launch {
            repository.updateTrackVisibility(trackId, !track.isVisible, track.sourceId)
        }
    }

    fun setAllVisibility(visible: Boolean) {
        viewModelScope.launch {
            repository.setAllVisibility(visible)
        }
    }

    fun setMergeExportItems(merge: Boolean) {
        updateSettings { it.copy(mergeExportItems = merge) }
    }

    fun openColorPicker(sourceId: String) {
        transientState.update { it.copy(editingColorSourceId = sourceId) }
    }

    fun closeColorPicker() {
        transientState.update { it.copy(editingColorSourceId = null) }
    }

    fun updateSourceColor(sourceId: String, colorHex: String) {
        val normalized = if (colorHex.startsWith("#")) colorHex.uppercase(Locale.US) else "#${colorHex.uppercase(Locale.US)}"
        if (!GeoMath.isValidHexColor(normalized)) return
        viewModelScope.launch {
            repository.updateSourceColor(sourceId, normalized)
            transientState.update { it.copy(editingColorSourceId = null) }
        }
    }

    fun openCreateSourceDialog() {
        transientState.update { it.copy(showCreateSourceDialog = true) }
    }

    fun closeCreateSourceDialog() {
        transientState.update { it.copy(showCreateSourceDialog = false) }
    }

    fun createCustomSource(name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val normalizedColor = if (colorHex.startsWith("#")) colorHex.uppercase(Locale.US) else "#${colorHex.uppercase(Locale.US)}"
        val validColor = if (GeoMath.isValidHexColor(normalizedColor)) normalizedColor else "#FACC15"
        val nextOrder = (uiState.value.sources.maxOfOrNull { it.sortOrder } ?: 4) + 1
        val customId = "custom_${UUID.randomUUID().toString().take(8)}"

        viewModelScope.launch {
            repository.addCustomSource(
                SourceCategory(
                    id = customId,
                    name = cleanName,
                    colorHex = validColor,
                    isVisible = true,
                    isPreset = false,
                    sortOrder = nextOrder
                )
            )
            transientState.update {
                it.copy(
                    showCreateSourceDialog = false,
                    snackbarMessage = "Added source \"$cleanName\""
                )
            }
        }
    }

    fun reassignTrackSource(trackId: String, newSourceId: String) {
        viewModelScope.launch {
            repository.reassignTrackSource(trackId, newSourceId)
            repository.updateSourceVisibility(newSourceId, true)
        }
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            repository.deleteTrack(trackId)
            transientState.update { it.copy(snackbarMessage = "Track removed.") }
        }
    }

    fun deleteSourceTracks(sourceId: String) {
        val src = uiState.value.sources.find { it.id == sourceId }
        viewModelScope.launch {
            repository.deleteTracksBySource(sourceId)
            if (src != null && !src.isPreset) {
                repository.deleteCustomSource(sourceId)
            }
            transientState.update {
                it.copy(snackbarMessage = "Deleted all ${src?.name ?: "source"} tracks.")
            }
        }
    }

    fun clearAllTracks() {
        viewModelScope.launch {
            repository.clearAllTracks()
            transientState.update { it.copy(snackbarMessage = "Cleared all tracks.") }
        }
    }

    fun setVisualizationMode(mode: VisualizationMode) {
        updateSettings { it.copy(visualizationMode = mode) }
    }

    fun toggleDarkMode() {
        updateSettings { current ->
            val nextDark = !current.uiDarkMode
            current.copy(
                uiDarkMode = nextDark,
                tileStyle = if (nextDark) TileStyle.DARK_MATTER else TileStyle.LIGHT_STREET
            )
        }
    }

    fun toggleTileStyle() {
        updateSettings { current ->
            val next = if (current.tileStyle == TileStyle.DARK_MATTER) {
                TileStyle.LIGHT_STREET
            } else {
                TileStyle.DARK_MATTER
            }
            current.copy(tileStyle = next)
        }
    }

    fun setTrackOpacity(opacity: Float) {
        updateSettings { it.copy(trackOpacity = opacity.coerceIn(0.15f, 1.0f)) }
    }

    fun setTrackWidth(width: Float) {
        updateSettings { it.copy(trackWidthPx = width.coerceIn(1.5f, 12.0f)) }
    }

    fun setRdpEnabled(enabled: Boolean) {
        updateSettings { it.copy(rdpEnabled = enabled) }
    }

    fun setRdpTolerance(meters: Float) {
        updateSettings { it.copy(rdpToleranceMeters = meters.coerceIn(2f, 50f)) }
    }

    fun recenterMap() {
        transientState.update { it.copy(fitBoundsNonce = it.fitBoundsNonce + 1) }
    }

    fun openExportDialog() {
        if (uiState.value.visibleTrackCount == 0) {
            transientState.update {
                it.copy(snackbarMessage = "Enable or import at least one visible track before exporting.")
            }
            return
        }
        transientState.update { it.copy(showExportDialog = true) }
    }

    fun closeExportDialog() {
        transientState.update { it.copy(showExportDialog = false) }
    }

    fun exportAggregatedGpxToUri(uri: Uri) {
        exportToUri(uri, ExportFormat.GPX, "track(s) to GPX v1.1")
    }

    fun exportGeoJsonToUri(uri: Uri) {
        exportToUri(uri, ExportFormat.GEOJSON, "feature(s) to GeoJSON")
    }

    fun exportKmzToUri(uri: Uri) {
        exportToUri(uri, ExportFormat.KMZ, "track(s) to Google My Maps KMZ")
    }

    fun exportKmlToUri(uri: Uri) {
        exportToUri(uri, ExportFormat.KML, "track(s) to Google My Maps KML")
    }

    private fun exportToUri(uri: Uri, format: ExportFormat, label: String) {
        val snapshot = uiState.value
        val visibleTracks = getVisibleTracks(snapshot)
        if (visibleTracks.isEmpty()) return

        viewModelScope.launch {
            transientState.update { it.copy(isLoading = true, showExportDialog = false) }
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = generateExportBytes(visibleTracks, snapshot, format)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } ?: error("Failed to open output stream")
                }.isSuccess
            }
            transientState.update {
                it.copy(
                    isLoading = false,
                    snackbarMessage = if (success) {
                        "Exported ${visibleTracks.size} $label."
                    } else {
                        "Failed to save ${format.extension.uppercase()} file."
                    }
                )
            }
        }
    }

    suspend fun createShareExportFile(format: ExportFormat): File? {
        val snapshot = uiState.value
        val visibleTracks = getVisibleTracks(snapshot)
        if (visibleTracks.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "trackforge_heatmap.${format.extension}")
                val bytes = generateExportBytes(visibleTracks, snapshot, format)
                file.writeBytes(bytes)
                file
            }.getOrNull()
        }
    }

    private fun generateExportBytes(
        visibleTracks: List<GpxTrack>,
        snapshot: MainUiState,
        format: ExportFormat
    ): ByteArray {
        val sourcesById = snapshot.sources.associateBy { it.id }
        val rdp = snapshot.settings.rdpEnabled
        val tol = snapshot.settings.rdpToleranceMeters
        val merge = snapshot.settings.mergeExportItems
        return when (format) {
            ExportFormat.KMZ -> GpxAndGeoJsonExporter.exportGoogleMyMapsKmz(
                tracks = visibleTracks,
                sourcesById = sourcesById,
                simplifyEnabled = rdp,
                toleranceMeters = tol,
                trackOpacity = snapshot.settings.trackOpacity,
                trackWidthPx = snapshot.settings.trackWidthPx,
                mergeAll = merge
            )
            ExportFormat.KML -> GpxAndGeoJsonExporter.exportGoogleMyMapsKml(
                tracks = visibleTracks,
                sourcesById = sourcesById,
                simplifyEnabled = rdp,
                toleranceMeters = tol,
                trackOpacity = snapshot.settings.trackOpacity,
                trackWidthPx = snapshot.settings.trackWidthPx,
                mergeAll = merge
            ).toByteArray(Charsets.UTF_8)
            ExportFormat.GPX -> GpxAndGeoJsonExporter.exportAggregatedGpx(
                tracks = visibleTracks,
                sourcesById = sourcesById,
                simplifyEnabled = rdp,
                toleranceMeters = tol,
                mergeAll = merge
            ).toByteArray(Charsets.UTF_8)
            ExportFormat.GEOJSON -> GpxAndGeoJsonExporter.exportGeoJson(
                tracks = visibleTracks,
                sourcesById = sourcesById,
                simplifyEnabled = rdp,
                toleranceMeters = tol,
                mergeAll = merge
            ).toByteArray(Charsets.UTF_8)
        }
    }

    fun clearSnackbar() {
        transientState.update { it.copy(snackbarMessage = null) }
    }

    private fun getVisibleTracks(state: MainUiState): List<GpxTrack> {
        val sourcesById = state.sources.associateBy { it.id }
        return state.tracks.filter { track ->
            track.isVisible && (sourcesById[track.sourceId]?.isVisible ?: true)
        }
    }

    private fun updateSettings(transform: (MapSettings) -> MapSettings) {
        val next = transform(uiState.value.settings)
        viewModelScope.launch {
            repository.saveSettings(next)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            val resolver = getApplication<Application>().contentResolver
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return@runCatching name
                    }
                }
            }
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private const val MAX_DISPLAY_POINTS_PER_TRACK = 10_000
    }
}
