package com.example.domain

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object GpxAndGeoJsonExporter {

    fun exportAggregatedGpx(
        tracks: List<GpxTrack>,
        sourcesById: Map<String, SourceCategory>,
        simplifyEnabled: Boolean,
        toleranceMeters: Float
    ): String {
        val nowIso = currentUtcIso()
        val sb = StringBuilder(tracks.size * 2048 + 512)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<gpx version=\"1.1\" creator=\"TrackForge GPX\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" " +
                "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 " +
                "http://www.topografix.com/GPX/1/1/gpx.xsd " +
                "http://www.garmin.com/xmlschemas/GpxExtensions/v3 " +
                "http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd\">\n"
        )
        sb.append("  <metadata>\n")
        sb.append("    <name>TrackForge GPX Aggregated Export</name>\n")
        sb.append("    <desc>Multi-source aggregated GPX containing ")
            .append(tracks.size)
            .append(" visible tracks</desc>\n")
        sb.append("    <time>").append(nowIso).append("</time>\n")
        sb.append("  </metadata>\n")

        for (track in tracks) {
            val source = sourcesById[track.sourceId]
            val sourceName = source?.name ?: "Custom"
            val colorHex = source?.colorHex ?: "#E879F9"
            val garminColor = GeoMath.mapHexToGarminDisplayColor(colorHex)
            val points = if (simplifyEnabled) {
                GeoMath.simplifyRdp(track.points, toleranceMeters.toDouble())
            } else {
                track.points
            }
            if (points.size < 2) continue

            val cleanTitle = track.trackName.trim().ifEmpty { track.fileName.substringBeforeLast('.') }
            val prefixedTitle = if (cleanTitle.startsWith("[$sourceName]")) {
                cleanTitle
            } else {
                "[$sourceName] $cleanTitle"
            }

            sb.append("  <trk>\n")
            sb.append("    <name>").append(escapeXml(prefixedTitle)).append("</name>\n")
            sb.append("    <src>").append(escapeXml(sourceName)).append("</src>\n")
            sb.append("    <type>").append(escapeXml(sourceName)).append("</type>\n")
            sb.append("    <extensions>\n")
            sb.append("      <gpxx:TrackExtension>\n")
            sb.append("        <gpxx:DisplayColor>").append(garminColor).append("</gpxx:DisplayColor>\n")
            sb.append("      </gpxx:TrackExtension>\n")
            sb.append("      <trackforge:color>").append(escapeXml(colorHex)).append("</trackforge:color>\n")
            sb.append("    </extensions>\n")
            sb.append("    <trkseg>\n")

            for (pt in points) {
                sb.append("      <trkpt lat=\"")
                    .append(formatCoord(pt.lat))
                    .append("\" lon=\"")
                    .append(formatCoord(pt.lon))
                    .append("\">")
                val hasSubTags = pt.ele != null || !pt.timeIso.isNullOrBlank()
                if (hasSubTags) {
                    sb.append("\n")
                    if (pt.ele != null) {
                        sb.append("        <ele>")
                            .append(String.format(Locale.US, "%.1f", pt.ele))
                            .append("</ele>\n")
                    }
                    if (!pt.timeIso.isNullOrBlank()) {
                        sb.append("        <time>").append(escapeXml(pt.timeIso)).append("</time>\n")
                    }
                    sb.append("      </trkpt>\n")
                } else {
                    sb.append("</trkpt>\n")
                }
            }

            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * Exports visible tracks into KML 2.2 optimized for Google My Maps.
     * Groups each source's tracks into a single <Placemark><MultiGeometry> with KML AABBGGRR
     * semi-transparent LineStyle so overlapping paths stack visually into a heatmap while
     * staying far below Google My Maps' 2,000-feature-per-layer limit.
     */
    fun exportGoogleMyMapsKml(
        tracks: List<GpxTrack>,
        sourcesById: Map<String, SourceCategory>,
        simplifyEnabled: Boolean,
        toleranceMeters: Float,
        trackOpacity: Float = 0.78f,
        trackWidthPx: Float = 3.5f
    ): String {
        val grouped = tracks.groupBy { it.sourceId }
        val sb = StringBuilder(tracks.size * 1024 + 1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>TrackForge GPX Heatmap</name>\n")
        sb.append("    <description>Aggregated multi-source heatmap exported for Google My Maps (")
            .append(tracks.size)
            .append(" tracks)</description>\n")

        grouped.forEach { (sourceId, _) ->
            val src = sourcesById[sourceId]
            val colorHex = src?.colorHex ?: "#E879F9"
            val kmlColor = hexToKmlColor(colorHex, trackOpacity)
            val widthStr = String.format(Locale.US, "%.1f", trackWidthPx.coerceIn(1f, 10f))
            sb.append("    <Style id=\"style_").append(escapeXml(sourceId)).append("\">\n")
            sb.append("      <LineStyle>\n")
            sb.append("        <color>").append(kmlColor).append("</color>\n")
            sb.append("        <width>").append(widthStr).append("</width>\n")
            sb.append("      </LineStyle>\n")
            sb.append("    </Style>\n")
        }

        grouped.forEach { (sourceId, sourceTracks) ->
            val src = sourcesById[sourceId]
            val sourceName = src?.name ?: "Custom"
            val totalKm = sourceTracks.sumOf { it.distanceKm }

            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(escapeXml(sourceName))
                .append(" (").append(sourceTracks.size).append(" tracks)</name>\n")
            sb.append("      <description>")
                .append(escapeXml(String.format(Locale.US, "%s • %d tracks • %.2f km", sourceName, sourceTracks.size, totalKm)))
                .append("</description>\n")
            sb.append("      <styleUrl>#style_").append(escapeXml(sourceId)).append("</styleUrl>\n")
            sb.append("      <MultiGeometry>\n")

            for (track in sourceTracks) {
                val points = if (simplifyEnabled) {
                    GeoMath.simplifyRdp(track.points, toleranceMeters.toDouble())
                } else {
                    track.points
                }
                if (points.size < 2) continue

                sb.append("        <LineString>\n")
                sb.append("          <tessellate>1</tessellate>\n")
                sb.append("          <coordinates>")
                points.forEachIndexed { idx, pt ->
                    if (idx > 0) sb.append(" ")
                    sb.append(formatCoord(pt.lon)).append(",").append(formatCoord(pt.lat))
                    if (pt.ele != null) {
                        sb.append(",").append(String.format(Locale.US, "%.1f", pt.ele))
                    }
                }
                sb.append("</coordinates>\n")
                sb.append("        </LineString>\n")
            }

            sb.append("      </MultiGeometry>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    /**
     * Compresses the Google My Maps KML into a standard KMZ archive (doc.kml inside ZIP)
     * to minimize file size under Google My Maps' 5 MB upload ceiling.
     */
    fun exportGoogleMyMapsKmz(
        tracks: List<GpxTrack>,
        sourcesById: Map<String, SourceCategory>,
        simplifyEnabled: Boolean,
        toleranceMeters: Float,
        trackOpacity: Float = 0.78f,
        trackWidthPx: Float = 3.5f
    ): ByteArray {
        val kmlBytes = exportGoogleMyMapsKml(
            tracks = tracks,
            sourcesById = sourcesById,
            simplifyEnabled = simplifyEnabled,
            toleranceMeters = toleranceMeters,
            trackOpacity = trackOpacity,
            trackWidthPx = trackWidthPx
        ).toByteArray(Charsets.UTF_8)

        val baos = ByteArrayOutputStream(kmlBytes.size / 4 + 128)
        ZipOutputStream(baos).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kmlBytes)
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    fun exportGeoJson(
        tracks: List<GpxTrack>,
        sourcesById: Map<String, SourceCategory>,
        simplifyEnabled: Boolean,
        toleranceMeters: Float
    ): String {
        val sb = StringBuilder(tracks.size * 1024 + 256)
        sb.append("{\n")
        sb.append("  \"type\": \"FeatureCollection\",\n")
        sb.append("  \"features\": [\n")

        var wroteFeature = false
        for (track in tracks) {
            val source = sourcesById[track.sourceId]
            val sourceName = source?.name ?: "Custom"
            val colorHex = source?.colorHex ?: "#E879F9"
            val points = if (simplifyEnabled) {
                GeoMath.simplifyRdp(track.points, toleranceMeters.toDouble())
            } else {
                track.points
            }
            if (points.size < 2) continue

            if (wroteFeature) sb.append(",\n")
            wroteFeature = true

            sb.append("    {\n")
            sb.append("      \"type\": \"Feature\",\n")
            sb.append("      \"properties\": {\n")
            sb.append("        \"name\": \"").append(escapeJson(track.trackName)).append("\",\n")
            sb.append("        \"source\": \"").append(escapeJson(sourceName)).append("\",\n")
            sb.append("        \"color\": \"").append(escapeJson(colorHex)).append("\",\n")
            sb.append("        \"stroke\": \"").append(escapeJson(colorHex)).append("\",\n")
            sb.append("        \"distance_km\": ").append(String.format(Locale.US, "%.3f", track.distanceKm)).append(",\n")
            sb.append("        \"point_count\": ").append(points.size)
            if (!track.recordedAtIso.isNullOrBlank()) {
                sb.append(",\n        \"time\": \"").append(escapeJson(track.recordedAtIso)).append("\"")
            }
            sb.append("\n      },\n")
            sb.append("      \"geometry\": {\n")
            sb.append("        \"type\": \"LineString\",\n")
            sb.append("        \"coordinates\": [")
            points.forEachIndexed { idx, pt ->
                if (idx > 0) sb.append(",")
                if (pt.ele != null) {
                    sb.append("[")
                        .append(formatCoord(pt.lon))
                        .append(",")
                        .append(formatCoord(pt.lat))
                        .append(",")
                        .append(String.format(Locale.US, "%.1f", pt.ele))
                        .append("]")
                } else {
                    sb.append("[")
                        .append(formatCoord(pt.lon))
                        .append(",")
                        .append(formatCoord(pt.lat))
                        .append("]")
                }
            }
            sb.append("]\n")
            sb.append("      }\n")
            sb.append("    }")
        }

        sb.append("\n  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Converts #RRGGBB + opacity (0..1) to KML's AABBGGRR hex format.
     */
    fun hexToKmlColor(hex: String, opacity: Float): String {
        val clean = hex.trim().removePrefix("#")
        val rr = clean.take(2).takeIf { it.length == 2 } ?: "FF"
        val gg = clean.drop(2).take(2).takeIf { it.length == 2 } ?: "57"
        val bb = clean.drop(4).take(2).takeIf { it.length == 2 } ?: "22"
        val alpha = (opacity.coerceIn(0.1f, 1.0f) * 255f).roundToInt().coerceIn(0, 255)
        return String.format(Locale.US, "%02x%s%s%s", alpha, bb.lowercase(Locale.US), gg.lowercase(Locale.US), rr.lowercase(Locale.US))
    }

    private fun formatCoord(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeJson(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun currentUtcIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
