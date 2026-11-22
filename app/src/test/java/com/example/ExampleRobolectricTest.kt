package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.TrackAggregatorDatabase
import com.example.data.TrackRepository
import com.example.domain.GpxTrack
import com.example.domain.GpxXmlParser
import com.example.domain.SourcePresets
import com.example.domain.TrackPoint
import com.example.ui.TrackAggregatorViewModel
import com.example.ui.screens.MapWorkspaceScreen
import com.example.ui.theme.TrackForgeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TrackForge GPX", appName)
    }

    @Test
    fun `large 45000 point GPX track persists and reloads without CursorWindow crash`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackAggregatorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val pointsDir = File(context.cacheDir, "test_track_points_${System.nanoTime()}")
        try {
            val repo = TrackRepository(db.sourceDao(), db.trackDao(), db.settingsDao(), pointsDir)
            repo.ensureInitialized()

            val largePoints = List(45_000) { idx ->
                TrackPoint(
                    lat = 37.7000 + idx * 0.00001,
                    lon = -122.4000 + idx * 0.00001,
                    ele = 10.0 + (idx % 100),
                    timeIso = "2026-10-12T07:15:00Z"
                )
            }
            val track = GpxTrack(
                id = "large-track-1",
                fileName = "mega_tour.gpx",
                trackName = "Mega Tour",
                creatorRaw = "Garmin Connect",
                sourceId = SourcePresets.ID_GARMIN,
                recordedAtIso = "2026-10-12T07:15:00Z",
                distanceKm = 85.4,
                rawPointCount = largePoints.size,
                points = largePoints,
                minLat = 37.7,
                maxLat = 38.15,
                minLon = -122.4,
                maxLon = -121.95,
                importedAtMillis = 12345L
            )

            repo.saveTracks(listOf(track))
            val loaded = repo.tracks.first()
            assertEquals(1, loaded.size)
            assertEquals(45_000, loaded[0].points.size)
            assertEquals("Mega Tour", loaded[0].trackName)

            repo.deleteTrack("large-track-1")
            assertTrue(repo.tracks.first().isEmpty())
        } finally {
            db.close()
            pointsDir.deleteRecursively()
        }
    }

    @Test
    fun `importing a single GPX file through ViewModel and Staging Dialog succeeds without crashing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val gpxFile = File(app.cacheDir, "single_upload.gpx").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="StravaGPX">
                  <trk>
                    <name>Single Upload Track</name>
                    <trkseg>
                      <trkpt lat="37.7955" lon="-122.3937"><ele>10.5</ele></trkpt>
                      <trkpt lat="37.8012" lon="-122.3982"><ele>12.0</ele></trkpt>
                      <trkpt lat="37.8050" lon="-122.4010"><ele>15.0</ele></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """.trimIndent()
            )
        }
        val viewModel = TrackAggregatorViewModel(app)

        composeTestRule.setContent {
            TrackForgeTheme(darkTheme = false) {
                MapWorkspaceScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Simulate single GPX file upload via URI
        viewModel.importGpxUris(listOf(Uri.fromFile(gpxFile)))
        val deadline = System.currentTimeMillis() + 10_000
        while (viewModel.uiState.value.pendingImports.isEmpty() && System.currentTimeMillis() < deadline) {
            composeTestRule.waitForIdle()
            Thread.sleep(50)
        }
        assertTrue(
            "Expected pendingImports, got snackbar=${viewModel.uiState.value.snackbarMessage}",
            viewModel.uiState.value.pendingImports.isNotEmpty()
        )
        composeTestRule.waitForIdle()

        // Confirm import in staging dialog
        composeTestRule.onNodeWithTag("confirm_import_button").assertIsDisplayed().performClick()
        val importDeadline = System.currentTimeMillis() + 10_000
        while ((viewModel.uiState.value.tracks.isEmpty() || viewModel.uiState.value.isLoading) &&
            System.currentTimeMillis() < importDeadline
        ) {
            composeTestRule.waitForIdle()
            Thread.sleep(50)
        }
        assertTrue(
            "Expected tracks, got snackbar=${viewModel.uiState.value.snackbarMessage}",
            viewModel.uiState.value.tracks.isNotEmpty()
        )
        composeTestRule.waitForIdle()

        // Legend and Export button should be visible after importing 1 GPX file
        composeTestRule.onNodeWithTag("legend_toggle_all_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_export_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("merge_export_items_row").assertIsDisplayed().performClick()
        val settingsDeadline = System.currentTimeMillis() + 5_000
        while (!viewModel.uiState.value.settings.mergeExportItems && System.currentTimeMillis() < settingsDeadline) {
            composeTestRule.waitForIdle()
            Thread.sleep(50)
        }
        assertTrue(viewModel.uiState.value.settings.mergeExportItems)

        gpxFile.delete()
    }
}
