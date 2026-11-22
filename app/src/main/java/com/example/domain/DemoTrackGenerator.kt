package com.example.domain

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

object DemoTrackGenerator {

    private data class DemoSpec(
        val fileName: String,
        val trackName: String,
        val creator: String,
        val sourceId: String,
        val timeIso: String,
        val waypoints: List<Pair<Double, Double>>,
        val jitterMeters: Double,
        val seedOffset: Double
    )

    fun generateDemoTracks(): List<GpxTrack> {
        // Overlapping waterfront, Presidio, and Golden Gate Park corridors in San Francisco
        // Deliberately overlap along Marina/Embarcadero & Park corridors to showcase glowing intensity & heatmap hotspots
        val embarcaderoToCrissy = listOf(
            37.7955 to -122.3937,
            37.8012 to -122.3982,
            37.8065 to -122.4055,
            37.8087 to -122.4160,
            37.8068 to -122.4265,
            37.8045 to -122.4368,
            37.8058 to -122.4485,
            37.8064 to -122.4630,
            37.8088 to -122.4751
        )

        val presidioToGgpLoop = listOf(
            37.8088 to -122.4751,
            37.8005 to -122.4712,
            37.7920 to -122.4675,
            37.7825 to -122.4688,
            37.7714 to -122.4686,
            37.7690 to -122.4820,
            37.7684 to -122.4970,
            37.7745 to -122.5095,
            37.7810 to -122.5015,
            37.7915 to -122.4880,
            37.8020 to -122.4795,
            37.8088 to -122.4751
        )

        val urbanMissionCommute = listOf(
            37.7525 to -122.4184,
            37.7608 to -122.4192,
            37.7695 to -122.4165,
            37.7780 to -122.4125,
            37.7865 to -122.4045,
            37.7955 to -122.3937,
            37.8012 to -122.3982,
            37.8065 to -122.4055
        )

        val twinPeaksTrail = listOf(
            37.7690 to -122.4535,
            37.7635 to -122.4480,
            37.7568 to -122.4465,
            37.7518 to -122.4478,
            37.7545 to -122.4530,
            37.7615 to -122.4585,
            37.7688 to -122.4610,
            37.7714 to -122.4686
        )

        val specs = listOf(
            DemoSpec(
                fileName = "strava_bay_tempo.gpx",
                trackName = "Bayfront Tempo Run",
                creator = "StravaGPX",
                sourceId = SourcePresets.ID_STRAVA,
                timeIso = "2026-10-12T07:15:00Z",
                waypoints = embarcaderoToCrissy,
                jitterMeters = 8.0,
                seedOffset = 0.3
            ),
            DemoSpec(
                fileName = "strava_crissy_repeats.gpx",
                trackName = "Crissy Field Intervals",
                creator = "StravaGPX",
                sourceId = SourcePresets.ID_STRAVA,
                timeIso = "2026-10-18T06:48:00Z",
                waypoints = embarcaderoToCrissy.reversed(),
                jitterMeters = 10.0,
                seedOffset = 1.1
            ),
            DemoSpec(
                fileName = "garmin_presidio_century.gpx",
                trackName = "Presidio & GGP Ride",
                creator = "Garmin Edge 840",
                sourceId = SourcePresets.ID_GARMIN,
                timeIso = "2026-10-24T08:30:00Z",
                waypoints = embarcaderoToCrissy.dropLast(1) + presidioToGgpLoop,
                jitterMeters = 12.0,
                seedOffset = 2.4
            ),
            DemoSpec(
                fileName = "sports_tracker_coastal_hike.gpx",
                trackName = "Coastal Bluff Hike",
                creator = "Sports Tracker",
                sourceId = SourcePresets.ID_SPORTS_TRACKER,
                timeIso = "2026-11-02T10:05:00Z",
                waypoints = presidioToGgpLoop,
                jitterMeters = 15.0,
                seedOffset = 3.7
            ),
            DemoSpec(
                fileName = "google_maps_bike_commute.gpx",
                trackName = "Mission to Embarcadero",
                creator = "Google Maps Timeline",
                sourceId = SourcePresets.ID_GOOGLE_MAPS,
                timeIso = "2026-11-08T08:42:00Z",
                waypoints = urbanMissionCommute,
                jitterMeters = 7.0,
                seedOffset = 4.9
            ),
            DemoSpec(
                fileName = "custom_twin_peaks_ridge.gpx",
                trackName = "Twin Peaks Ridge Scramble",
                creator = "Custom Field Logger",
                sourceId = SourcePresets.ID_CUSTOM,
                timeIso = "2026-11-15T16:20:00Z",
                waypoints = twinPeaksTrail,
                jitterMeters = 11.0,
                seedOffset = 6.2
            )
        )

        val now = System.currentTimeMillis()
        return specs.mapIndexed { idx, spec ->
            val densePoints = interpolateWaypoints(
                waypoints = spec.waypoints,
                stepsPerSegment = 24,
                jitterMeters = spec.jitterMeters,
                seedOffset = spec.seedOffset,
                baseTimeIso = spec.timeIso
            )
            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0
            for (p in densePoints) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            GpxTrack(
                id = UUID.randomUUID().toString(),
                fileName = spec.fileName,
                trackName = spec.trackName,
                creatorRaw = spec.creator,
                sourceId = spec.sourceId,
                recordedAtIso = spec.timeIso,
                distanceKm = GeoMath.computeTotalDistanceKm(densePoints),
                rawPointCount = densePoints.size,
                points = densePoints,
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon,
                importedAtMillis = now + idx
            )
        }
    }

    private fun interpolateWaypoints(
        waypoints: List<Pair<Double, Double>>,
        stepsPerSegment: Int,
        jitterMeters: Double,
        seedOffset: Double,
        baseTimeIso: String
    ): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        var stepCounter = 0
        for (i in 0 until waypoints.size - 1) {
            val (lat1, lon1) = waypoints[i]
            val (lat2, lon2) = waypoints[i + 1]
            for (s in 0 until stepsPerSegment) {
                val t = s.toDouble() / stepsPerSegment
                val baseLat = lat1 + (lat2 - lat1) * t
                val baseLon = lon1 + (lon2 - lon1) * t

                // Smooth sinusoidal road curve perturbation
                val phase = stepCounter * 0.35 + seedOffset
                val latJitterDeg = (sin(phase) * jitterMeters) / 111_320.0
                val lonJitterDeg = (cos(phase * 0.8) * jitterMeters) / 88_000.0
                val ele = 12.0 + 38.0 * (0.5 + 0.5 * sin(phase * 0.25))

                result.add(
                    TrackPoint(
                        lat = baseLat + latJitterDeg,
                        lon = baseLon + lonJitterDeg,
                        ele = ele,
                        timeIso = if (stepCounter == 0) baseTimeIso else null
                    )
                )
                stepCounter++
            }
        }
        val last = waypoints.last()
        result.add(TrackPoint(lat = last.first, lon = last.second, ele = 14.0))
        return result
    }
}
