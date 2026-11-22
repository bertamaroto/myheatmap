package com.example.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(a: TrackPoint, b: TrackPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val h = (sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon).coerceIn(0.0, 1.0)
        return 2.0 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1.0 - h))
    }

    fun computeTotalDistanceKm(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var totalMeters = 0.0
        for (i in 1 until points.size) {
            totalMeters += haversineMeters(points[i - 1], points[i])
        }
        return totalMeters / 1000.0
    }

    /**
     * Iterative Ramer-Douglas-Peucker simplification using equirectangular meter projection.
     * Stack-safe for tracks with tens of thousands of points.
     */
    fun simplifyRdp(points: List<TrackPoint>, toleranceMeters: Double): List<TrackPoint> {
        if (points.size <= 2 || toleranceMeters <= 0.0) return points

        val n = points.size
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to (n - 1))

        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end - start <= 1) continue

            val pStart = points[start]
            val pEnd = points[end]
            val refLatRad = Math.toRadians((pStart.lat + pEnd.lat) * 0.5)
            val cosRef = cos(refLatRad).coerceAtLeast(0.01)

            var maxDist = 0.0
            var maxIdx = -1

            for (i in (start + 1) until end) {
                val dist = perpendicularDistanceMeters(points[i], pStart, pEnd, cosRef)
                if (dist > maxDist) {
                    maxDist = dist
                    maxIdx = i
                }
            }

            if (maxDist > toleranceMeters && maxIdx != -1) {
                keep[maxIdx] = true
                stack.addLast(start to maxIdx)
                stack.addLast(maxIdx to end)
            }
        }

        val result = ArrayList<TrackPoint>(n / 2 + 2)
        for (i in 0 until n) {
            if (keep[i]) result.add(points[i])
        }
        return result
    }

    /**
     * Uniformly decimates a point list to at most [maxPoints] for lightweight WebView rendering,
     * preserving the exact first and last points.
     */
    fun decimateForDisplay(points: List<TrackPoint>, maxPoints: Int): List<TrackPoint> {
        if (maxPoints < 2 || points.size <= maxPoints) return points
        val result = ArrayList<TrackPoint>(maxPoints)
        val lastIdx = points.size - 1
        val step = lastIdx.toDouble() / (maxPoints - 1).toDouble()
        for (i in 0 until maxPoints - 1) {
            result.add(points[(i * step).toInt()])
        }
        result.add(points[lastIdx])
        return result
    }

    private fun perpendicularDistanceMeters(
        p: TrackPoint,
        a: TrackPoint,
        b: TrackPoint,
        cosRef: Double
    ): Double {
        val degToM = (PI / 180.0) * EARTH_RADIUS_M
        val ax = a.lon * degToM * cosRef
        val ay = a.lat * degToM
        val bx = b.lon * degToM * cosRef
        val by = b.lat * degToM
        val px = p.lon * degToM * cosRef
        val py = p.lat * degToM

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) {
            return sqrt((px - ax).pow(2) + (py - ay).pow(2))
        }
        val num = abs(dy * px - dx * py + bx * ay - by * ax)
        return num / sqrt(lenSq)
    }

    private data class GarminColorEntry(val name: String, val r: Int, val g: Int, val b: Int)

    private val garminPalette = listOf(
        GarminColorEntry("Black", 0, 0, 0),
        GarminColorEntry("DarkRed", 139, 0, 0),
        GarminColorEntry("DarkGreen", 0, 100, 0),
        GarminColorEntry("DarkYellow", 184, 134, 11),
        GarminColorEntry("DarkBlue", 0, 0, 139),
        GarminColorEntry("DarkMagenta", 139, 0, 139),
        GarminColorEntry("DarkCyan", 0, 139, 139),
        GarminColorEntry("LightGray", 211, 211, 211),
        GarminColorEntry("DarkGray", 169, 169, 169),
        GarminColorEntry("Red", 255, 69, 0),
        GarminColorEntry("Green", 34, 197, 94),
        GarminColorEntry("Yellow", 250, 204, 21),
        GarminColorEntry("Blue", 59, 130, 246),
        GarminColorEntry("Magenta", 232, 121, 249),
        GarminColorEntry("Cyan", 0, 229, 255),
        GarminColorEntry("White", 255, 255, 255)
    )

    fun mapHexToGarminDisplayColor(hex: String): String {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return "Cyan"
        val rgb = clean.toIntOrNull(16) ?: return "Cyan"
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        var bestName = "Cyan"
        var bestDist = Int.MAX_VALUE
        for (entry in garminPalette) {
            val dr = r - entry.r
            val dg = g - entry.g
            val db = b - entry.b
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestName = entry.name
            }
        }
        return bestName
    }

    fun isValidHexColor(hex: String): Boolean {
        return Regex("^#[0-9A-Fa-f]{6}$").matches(hex.trim())
    }
}
