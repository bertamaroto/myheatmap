package com.example

import com.example.domain.DemoTrackGenerator
import com.example.domain.GeoMath
import com.example.domain.GpxAndGeoJsonExporter
import com.example.domain.GpxTrack
import com.example.domain.GpxXmlParser
import com.example.domain.SourcePresets
import com.example.domain.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxEngineUnitTest {

    @Test
    fun `parses creator and detects Strava, Garmin, Google Maps, and Sports Tracker sources`() {
        val stravaGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="StravaGPX">
              <metadata><time>2026-10-12T07:15:00Z</time></metadata>
              <trk>
                <name>Morning Loop</name>
                <trkseg>
                  <trkpt lat="37.7955" lon="-122.3937"><ele>10.5</ele></trkpt>
                  <trkpt lat="37.8012" lon="-122.3982"><ele>12.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val parsed = GpxXmlParser.parseStream(stravaGpx.byteInputStream(), "run.gpx")
        assertEquals(1, parsed.size)
        assertEquals(SourcePresets.ID_STRAVA, parsed[0].detectedSourceId)
        assertEquals("Morning Loop", parsed[0].trackName)
        assertEquals(2, parsed[0].points.size)
        assertTrue(parsed[0].distanceKm > 0.5)

        assertEquals(
            SourcePresets.ID_GOOGLE_MAPS,
            SourcePresets.detectSourceId(creator = "Google Takeout Location History")
        )
        assertEquals(
            SourcePresets.ID_SPORTS_TRACKER,
            SourcePresets.detectSourceId(creator = "Sports Tracker 4.1")
        )
        assertEquals(
            SourcePresets.ID_GARMIN,
            SourcePresets.detectSourceId(creator = "Garmin Connect")
        )
    }

    @Test
    fun `rdp simplification reduces collinear points while preserving endpoints`() {
        val points = listOf(
            TrackPoint(37.7700, -122.4200),
            TrackPoint(37.7710, -122.4200),
            TrackPoint(37.7720, -122.4200),
            TrackPoint(37.7730, -122.4200),
            TrackPoint(37.7740, -122.4200)
        )
        val simplified = GeoMath.simplifyRdp(points, toleranceMeters = 5.0)
        assertEquals(2, simplified.size)
        assertEquals(37.7700, simplified.first().lat, 1e-6)
        assertEquals(37.7740, simplified.last().lat, 1e-6)
    }

    @Test
    fun `exports valid GPX v1_1 with source prefix, type, and gpxx DisplayColor`() {
        val sources = SourcePresets.defaultSources.associateBy { it.id }
        val track = GpxTrack(
            id = "t1",
            fileName = "morning.gpx",
            trackName = "Morning Run",
            creatorRaw = "StravaGPX",
            sourceId = SourcePresets.ID_STRAVA,
            recordedAtIso = "2026-10-12T07:15:00Z",
            distanceKm = 2.4,
            rawPointCount = 2,
            points = listOf(
                TrackPoint(37.7955, -122.3937, 10.0, "2026-10-12T07:15:00Z"),
                TrackPoint(37.8012, -122.3982, 12.0, "2026-10-12T07:18:00Z")
            ),
            minLat = 37.7955,
            maxLat = 37.8012,
            minLon = -122.3982,
            maxLon = -122.3937,
            importedAtMillis = 1L
        )

        val gpxOut = GpxAndGeoJsonExporter.exportAggregatedGpx(
            tracks = listOf(track),
            sourcesById = sources,
            simplifyEnabled = false,
            toleranceMeters = 10f
        )

        assertTrue(gpxOut.contains("<gpx version=\"1.1\""))
        assertTrue(gpxOut.contains("<name>[Strava] Morning Run</name>"))
        assertTrue(gpxOut.contains("<type>Strava</type>"))
        assertTrue(gpxOut.contains("<gpxx:TrackExtension>"))
        assertTrue(gpxOut.contains("<gpxx:DisplayColor>Red</gpxx:DisplayColor>"))

        // Re-importing exported GPX should round-trip cleanly
        val reimported = GpxXmlParser.parseStream(gpxOut.byteInputStream(), "aggregated.gpx")
        assertEquals(1, reimported.size)
        assertEquals(SourcePresets.ID_STRAVA, reimported[0].detectedSourceId)
        assertEquals("Morning Run", reimported[0].trackName)
    }

    @Test
    fun `exports valid GeoJSON with source and color properties and generates demo tracks`() {
        val sources = SourcePresets.defaultSources.associateBy { it.id }
        val demos = DemoTrackGenerator.generateDemoTracks()
        assertEquals(6, demos.size)

        val geoJson = GpxAndGeoJsonExporter.exportGeoJson(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f
        )

        assertTrue(geoJson.contains("\"type\": \"FeatureCollection\""))
        assertTrue(geoJson.contains("\"source\": \"Strava\""))
        assertTrue(geoJson.contains("\"color\": \"#FF5722\""))
        assertTrue(geoJson.contains("\"source\": \"Google Maps\""))
    }

    @Test
    fun `exports Google My Maps KML and compressed KMZ with MultiGeometry and AABBGGRR color`() {
        val sources = SourcePresets.defaultSources.associateBy { it.id }
        val demos = DemoTrackGenerator.generateDemoTracks()

        val kml = GpxAndGeoJsonExporter.exportGoogleMyMapsKml(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f,
            trackOpacity = 0.8f,
            trackWidthPx = 4.0f
        )

        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
        assertTrue(kml.contains("<MultiGeometry>"))
        assertTrue(kml.contains("<color>cc2257ff</color>")) // Strava #FF5722 @ 80% -> cc2257ff

        val kmzBytes = GpxAndGeoJsonExporter.exportGoogleMyMapsKmz(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f,
            trackOpacity = 0.8f,
            trackWidthPx = 4.0f
        )
        assertTrue(kmzBytes.isNotEmpty())
        assertTrue(kmzBytes.size < kml.toByteArray(Charsets.UTF_8).size)

        val zipStream = java.util.zip.ZipInputStream(kmzBytes.inputStream())
        val entry = zipStream.nextEntry
        assertEquals("doc.kml", entry?.name)
    }

    @Test
    fun `mergeAll combines all exported tracks into single GPX track, single KML Placemark, and single GeoJSON MultiLineString`() {
        val sources = SourcePresets.defaultSources.associateBy { it.id }
        val demos = DemoTrackGenerator.generateDemoTracks()

        val mergedGpx = GpxAndGeoJsonExporter.exportAggregatedGpx(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f,
            mergeAll = true
        )
        assertEquals(1, "<trk>".toRegex().findAll(mergedGpx).count())
        assertEquals(demos.size, "<trkseg>".toRegex().findAll(mergedGpx).count())

        val mergedKml = GpxAndGeoJsonExporter.exportGoogleMyMapsKml(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f,
            mergeAll = true
        )
        assertEquals(1, "<Placemark>".toRegex().findAll(mergedKml).count())
        assertEquals(1, "<MultiGeometry>".toRegex().findAll(mergedKml).count())
        assertEquals(demos.size, "<LineString>".toRegex().findAll(mergedKml).count())

        val mergedGeoJson = GpxAndGeoJsonExporter.exportGeoJson(
            tracks = demos,
            sourcesById = sources,
            simplifyEnabled = true,
            toleranceMeters = 15f,
            mergeAll = true
        )
        assertEquals(1, "\"type\": \"Feature\"".toRegex().findAll(mergedGeoJson).count())
        assertTrue(mergedGeoJson.contains("\"type\": \"MultiLineString\""))
    }

    @Test
    fun `parses GPX with UTF-8 BOM and filters NaN elevation safely`() {
        val gpxWithBom = "\uFEFF" + """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Garmin Connect">
              <trk>
                <name>BOM Ride</name>
                <trkseg>
                  <trkpt lat=" 40.416775 " lon=" -3.703790 "><ele>NaN</ele></trkpt>
                  <trkpt lat="40.420000" lon="-3.700000"><ele>Infinity</ele></trkpt>
                  <trkpt lat="40.425000" lon="-3.695000"><ele>655.2</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val parsed = GpxXmlParser.parseStream(gpxWithBom.toByteArray(Charsets.UTF_8).inputStream(), "bom_ride.gpx")
        assertEquals(1, parsed.size)
        assertEquals(3, parsed[0].points.size)
        assertEquals(null, parsed[0].points[0].ele)
        assertEquals(null, parsed[0].points[1].ele)
        assertEquals(655.2, parsed[0].points[2].ele!!, 1e-6)
        assertTrue(parsed[0].distanceKm.isFinite())

        val largeList = List(25_000) { idx ->
            TrackPoint(37.7 + idx * 0.00001, -122.4 + idx * 0.00001)
        }
        val decimated = GeoMath.decimateForDisplay(largeList, 10_000)
        assertEquals(10_000, decimated.size)
        assertEquals(largeList.first(), decimated.first())
        assertEquals(largeList.last(), decimated.last())
    }
}
