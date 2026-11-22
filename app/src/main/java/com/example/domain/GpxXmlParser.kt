package com.example.domain

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.UUID
import javax.xml.parsers.SAXParserFactory

object GpxXmlParser {

    /**
     * Streaming SAX parser for GPX files.
     * Uses O(1) XML parser memory (no DOM tree allocation) so large multi-megabyte GPX batches
     * can be parsed on-device with minimal RAM.
     */
    fun parseStream(
        inputStream: InputStream,
        fileName: String,
        availableSources: List<SourceCategory> = SourcePresets.defaultSources
    ): List<PendingImportTrack> {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newSAXParser()
        val handler = StreamingGpxHandler(fileName, availableSources)
        parser.parse(inputStream, handler)
        return handler.results
    }

    private class StreamingGpxHandler(
        private val fileName: String,
        private val availableSources: List<SourceCategory>
    ) : DefaultHandler() {

        val results = mutableListOf<PendingImportTrack>()
        private val rteFallback = mutableListOf<PendingImportTrack>()

        private var creatorRaw: String? = null
        private var metadataName: String? = null
        private var metadataAuthor: String? = null
        private var metadataTime: String? = null

        private var inMetadata = false
        private var inMetadataAuthor = false
        private var inTrk = false
        private var inRte = false
        private var inPoint = false

        private var currentTrackName = StringBuilder()
        private var currentTrackType = StringBuilder()
        private var currentTrackSrc = StringBuilder()
        private val currentPoints = mutableListOf<TrackPoint>()

        private var ptLat: Double? = null
        private var ptLon: Double? = null
        private var ptEle: Double? = null
        private var ptTime: String? = null

        private var currentTag: String? = null
        private val textBuf = StringBuilder()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String,
            attributes: Attributes
        ) {
            val tag = qName.substringAfter(':').lowercase()
            currentTag = tag
            textBuf.setLength(0)

            when (tag) {
                "gpx" -> {
                    creatorRaw = attributes.getValue("creator")?.trim()?.takeIf { it.isNotEmpty() }
                }
                "metadata" -> inMetadata = true
                "author" -> if (inMetadata) inMetadataAuthor = true
                "trk" -> {
                    inTrk = true
                    currentTrackName.setLength(0)
                    currentTrackType.setLength(0)
                    currentTrackSrc.setLength(0)
                    currentPoints.clear()
                }
                "rte" -> {
                    inRte = true
                    currentTrackName.setLength(0)
                    currentTrackType.setLength(0)
                    currentTrackSrc.setLength(0)
                    currentPoints.clear()
                }
                "trkpt", "rtept" -> {
                    inPoint = true
                    ptLat = attributes.getValue("lat")?.toDoubleOrNull()
                    ptLon = attributes.getValue("lon")?.toDoubleOrNull()
                    ptEle = null
                    ptTime = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (currentTag != null) {
                textBuf.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val tag = qName.substringAfter(':').lowercase()
            val text = textBuf.toString().trim()

            when (tag) {
                "metadata" -> inMetadata = false
                "author" -> inMetadataAuthor = false
                "name" -> {
                    if (inMetadata && !inMetadataAuthor && metadataName == null && text.isNotEmpty()) {
                        metadataName = text
                    } else if (inMetadataAuthor && metadataAuthor == null && text.isNotEmpty()) {
                        metadataAuthor = text
                    } else if ((inTrk || inRte) && !inPoint && currentTrackName.isEmpty() && text.isNotEmpty()) {
                        currentTrackName.append(text)
                    }
                }
                "type" -> {
                    if ((inTrk || inRte) && !inPoint && currentTrackType.isEmpty() && text.isNotEmpty()) {
                        currentTrackType.append(text)
                    }
                }
                "src" -> {
                    if ((inTrk || inRte) && !inPoint && currentTrackSrc.isEmpty() && text.isNotEmpty()) {
                        currentTrackSrc.append(text)
                    }
                }
                "ele" -> {
                    if (inPoint) ptEle = text.toDoubleOrNull()
                }
                "time" -> {
                    if (inPoint) {
                        ptTime = text.takeIf { it.isNotEmpty() }
                    } else if (inMetadata && metadataTime == null && text.isNotEmpty()) {
                        metadataTime = text
                    }
                }
                "trkpt", "rtept" -> {
                    val lat = ptLat
                    val lon = ptLon
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        currentPoints.add(TrackPoint(lat = lat, lon = lon, ele = ptEle, timeIso = ptTime))
                    }
                    inPoint = false
                }
                "trk" -> {
                    finalizeTrack(targetList = results, index = results.size + 1)
                    inTrk = false
                }
                "rte" -> {
                    finalizeTrack(targetList = rteFallback, index = rteFallback.size + 1)
                    inRte = false
                }
                "gpx" -> {
                    if (results.isEmpty() && rteFallback.isNotEmpty()) {
                        results.addAll(rteFallback)
                    }
                }
            }
            currentTag = null
            textBuf.setLength(0)
        }

        private fun finalizeTrack(targetList: MutableList<PendingImportTrack>, index: Int) {
            if (currentPoints.size < 2) return
            val rawName = currentTrackName.toString().trim()
            val trkType = currentTrackType.toString().trim().takeIf { it.isNotEmpty() }
            val trkSrc = currentTrackSrc.toString().trim().takeIf { it.isNotEmpty() }

            val baseTitle = stripSourcePrefix(rawName).ifBlank {
                val stem = fileName.substringBeforeLast('.')
                if (index > 1) "$stem #$index" else stem
            }
            val firstPointTime = currentPoints.firstOrNull { !it.timeIso.isNullOrBlank() }?.timeIso ?: metadataTime
            val detectedSource = SourcePresets.detectSourceId(
                creator = creatorRaw ?: metadataAuthor,
                trackType = trkType,
                trackSrc = trkSrc,
                metadataName = metadataName,
                trackName = rawName,
                fileName = fileName,
                availableSources = availableSources
            )

            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0
            for (p in currentPoints) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }

            val copiedPoints = ArrayList<TrackPoint>(currentPoints)
            targetList.add(
                PendingImportTrack(
                    tempId = UUID.randomUUID().toString(),
                    fileName = fileName,
                    trackName = baseTitle,
                    creatorRaw = creatorRaw ?: trkSrc ?: trkType,
                    detectedSourceId = detectedSource,
                    selectedSourceId = detectedSource,
                    recordedAtIso = firstPointTime,
                    distanceKm = GeoMath.computeTotalDistanceKm(copiedPoints),
                    points = copiedPoints,
                    minLat = minLat,
                    maxLat = maxLat,
                    minLon = minLon,
                    maxLon = maxLon
                )
            )
        }

        private fun stripSourcePrefix(name: String): String {
            val match = Regex("^\\[[^\\]]+]\\s*(.+)$").find(name.trim())
            return match?.groupValues?.getOrNull(1)?.trim() ?: name.trim()
        }
    }
}
