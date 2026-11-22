package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.TileStyle
import com.example.ui.ExportFormat
import com.example.ui.TrackAggregatorViewModel
import com.example.ui.components.BottomRenderDeck
import com.example.ui.components.CreateCustomSourceDialog
import com.example.ui.components.EmptyMapOverlay
import com.example.ui.components.ExportOptionsDialog
import com.example.ui.components.FloatingLegendCard
import com.example.ui.components.LeafletMapView
import com.example.ui.components.PendingImportStagingDialog
import com.example.ui.components.SourceColorPickerDialog
import com.example.ui.components.TopTacticalHud
import com.example.ui.components.TrackManagerSidebar
import kotlinx.coroutines.launch

@Composable
fun MapWorkspaceScreen(
    isDarkTheme: Boolean = false,
    onToggleUiTheme: () -> Unit = {},
    viewModel: TrackAggregatorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var mobileDrawerOpen by remember { mutableStateOf(false) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importGpxUris(uris)
        }
    }

    val saveKmzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kmz")
    ) { uri ->
        uri?.let { viewModel.exportKmzToUri(it) }
    }

    val saveKmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { uri ->
        uri?.let { viewModel.exportKmlToUri(it) }
    }

    val saveGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        uri?.let { viewModel.exportAggregatedGpxToUri(it) }
    }

    val saveGeoJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        uri?.let { viewModel.exportGeoJsonToUri(it) }
    }

    fun launchImportPicker() {
        gpxPickerLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
    }

    fun shareExportedFile(format: ExportFormat) {
        scope.launch {
            val file = viewModel.createShareExportFile(format) ?: return@launch
            viewModel.closeExportDialog()
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share TrackForge Export"))
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                TrackManagerSidebar(
                    summaries = uiState.sourceSummaries,
                    tracks = uiState.tracks,
                    allSources = uiState.sources,
                    visibleTrackCount = uiState.visibleTrackCount,
                    isDarkTheme = isDarkTheme,
                    onToggleUiTheme = onToggleUiTheme,
                    showCloseButton = false,
                    onClose = {},
                    onImportClick = ::launchImportPicker,
                    onLoadDemoClick = viewModel::loadDemoTracks,
                    onCreateSourceClick = viewModel::openCreateSourceDialog,
                    onToggleAllVisibility = viewModel::setAllVisibility,
                    onToggleSourceVisibility = viewModel::toggleSourceVisibility,
                    onToggleTrackVisibility = viewModel::toggleTrackVisibility,
                    onOpenColorPicker = viewModel::openColorPicker,
                    onReassignTrackSource = viewModel::reassignTrackSource,
                    onDeleteTrack = viewModel::deleteTrack,
                    onDeleteSourceTracks = viewModel::deleteSourceTracks,
                    onClearAllTracks = viewModel::clearAllTracks,
                    onOpenExportDialog = viewModel::openExportDialog,
                    modifier = Modifier
                        .width(356.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            // Map & Floating HUD Workspace
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LeafletMapView(
                    payloadJson = uiState.mapPayloadJson,
                    isDarkTiles = uiState.settings.tileStyle == TileStyle.DARK_MATTER,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Tactical HUD
                TopTacticalHud(
                    visualizationMode = uiState.settings.visualizationMode,
                    isDarkMode = uiState.settings.uiDarkMode,
                    totalTracks = uiState.visibleTrackCount,
                    visibleDistanceKm = uiState.visibleDistanceKm,
                    showSidebarToggle = !isWideScreen,
                    onToggleSidebar = { mobileDrawerOpen = !mobileDrawerOpen },
                    onSelectMode = viewModel::setVisualizationMode,
                    onToggleDarkMode = viewModel::toggleDarkMode,
                    onRecenter = viewModel::recenterMap,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .widthIn(max = 720.dp)
                )

                // Floating Legend HUD (when tracks exist)
                if (uiState.tracks.isNotEmpty()) {
                    FloatingLegendCard(
                        summaries = uiState.sourceSummaries,
                        allItemsVisible = uiState.visibleTrackCount == uiState.tracks.size,
                        anyItemVisible = uiState.visibleTrackCount > 0,
                        onToggleAllVisibility = viewModel::setAllVisibility,
                        onToggleSource = viewModel::toggleSourceVisibility,
                        onPickColor = viewModel::openColorPicker,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 84.dp, start = 12.dp)
                    )
                }

                // Empty State Onboarding Card
                if (uiState.tracks.isEmpty() && !uiState.isLoading) {
                    EmptyMapOverlay(
                        onImportClick = ::launchImportPicker,
                        onLoadDemoClick = viewModel::loadDemoTracks,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Bottom Render & Action Dock
                BottomRenderDeck(
                    settings = uiState.settings,
                    rawPointCount = uiState.totalRawPoints,
                    renderedPointCount = uiState.totalRenderedPoints,
                    hasVisibleTracks = uiState.visibleTrackCount > 0,
                    onOpacityChange = viewModel::setTrackOpacity,
                    onWidthChange = viewModel::setTrackWidth,
                    onRdpToggle = viewModel::setRdpEnabled,
                    onRdpToleranceChange = viewModel::setRdpTolerance,
                    onImportClick = ::launchImportPicker,
                    onExportClick = viewModel::openExportDialog,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .widthIn(max = 680.dp)
                )

                // Loading Overlay
                if (uiState.isLoading) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                // Snackbar Host
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Mobile Slide-Over Track Manager Overlay (avoids swipe conflict with Leaflet map panning)
        if (!isWideScreen) {
            BackHandler(enabled = mobileDrawerOpen) {
                mobileDrawerOpen = false
            }

            AnimatedVisibility(
                visible = mobileDrawerOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { mobileDrawerOpen = false }
                )
            }

            AnimatedVisibility(
                visible = mobileDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                TrackManagerSidebar(
                    summaries = uiState.sourceSummaries,
                    tracks = uiState.tracks,
                    allSources = uiState.sources,
                    visibleTrackCount = uiState.visibleTrackCount,
                    isDarkTheme = isDarkTheme,
                    onToggleUiTheme = onToggleUiTheme,
                    showCloseButton = true,
                    onClose = { mobileDrawerOpen = false },
                    onImportClick = {
                        mobileDrawerOpen = false
                        launchImportPicker()
                    },
                    onLoadDemoClick = {
                        mobileDrawerOpen = false
                        viewModel.loadDemoTracks()
                    },
                    onCreateSourceClick = viewModel::openCreateSourceDialog,
                    onToggleAllVisibility = viewModel::setAllVisibility,
                    onToggleSourceVisibility = viewModel::toggleSourceVisibility,
                    onToggleTrackVisibility = viewModel::toggleTrackVisibility,
                    onOpenColorPicker = viewModel::openColorPicker,
                    onReassignTrackSource = viewModel::reassignTrackSource,
                    onDeleteTrack = viewModel::deleteTrack,
                    onDeleteSourceTracks = viewModel::deleteSourceTracks,
                    onClearAllTracks = viewModel::clearAllTracks,
                    onOpenExportDialog = {
                        mobileDrawerOpen = false
                        viewModel.openExportDialog()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 360.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
        }
    }

    // Dialogs
    if (uiState.pendingImports.isNotEmpty()) {
        PendingImportStagingDialog(
            pendingTracks = uiState.pendingImports,
            sources = uiState.sources,
            onUpdateItemSource = viewModel::updatePendingImportSource,
            onSetAllSources = viewModel::setAllPendingImportsSource,
            onConfirmImport = viewModel::confirmPendingImports,
            onCancel = viewModel::cancelPendingImports
        )
    }

    uiState.editingColorSource?.let { source ->
        SourceColorPickerDialog(
            source = source,
            onSelectColor = { hex -> viewModel.updateSourceColor(source.id, hex) },
            onDismiss = viewModel::closeColorPicker
        )
    }

    if (uiState.showCreateSourceDialog) {
        CreateCustomSourceDialog(
            onCreate = viewModel::createCustomSource,
            onDismiss = viewModel::closeCreateSourceDialog
        )
    }

    if (uiState.showExportDialog) {
        ExportOptionsDialog(
            allTracks = uiState.tracks,
            sources = uiState.sources,
            visibleTrackCount = uiState.visibleTrackCount,
            visibleDistanceKm = uiState.visibleDistanceKm,
            renderedPoints = uiState.totalRenderedPoints,
            rdpEnabled = uiState.settings.rdpEnabled,
            mergeExportItems = uiState.settings.mergeExportItems,
            onToggleMergeExportItems = viewModel::setMergeExportItems,
            onToggleTrackVisibility = viewModel::toggleTrackVisibility,
            onToggleAllVisibility = viewModel::setAllVisibility,
            onSaveKmz = { saveKmzLauncher.launch("trackforge_heatmap.kmz") },
            onSaveKml = { saveKmlLauncher.launch("trackforge_heatmap.kml") },
            onSaveGpx = { saveGpxLauncher.launch("trackforge_aggregated.gpx") },
            onSaveGeoJson = { saveGeoJsonLauncher.launch("trackforge_aggregated.geojson") },
            onShareKmz = { shareExportedFile(ExportFormat.KMZ) },
            onShareGpx = { shareExportedFile(ExportFormat.GPX) },
            onDismiss = viewModel::closeExportDialog
        )
    }
}
