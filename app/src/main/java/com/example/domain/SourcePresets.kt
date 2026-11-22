package com.example.domain

object SourcePresets {
    const val ID_STRAVA = "strava"
    const val ID_SPORTS_TRACKER = "sports_tracker"
    const val ID_GOOGLE_MAPS = "google_maps"
    const val ID_GARMIN = "garmin"
    const val ID_CUSTOM = "custom"

    val defaultSources: List<SourceCategory> = listOf(
        SourceCategory(
            id = ID_STRAVA,
            name = "Strava",
            colorHex = "#FF5722",
            isVisible = true,
            isPreset = true,
            sortOrder = 0
        ),
        SourceCategory(
            id = ID_SPORTS_TRACKER,
            name = "Sports Tracker",
            colorHex = "#00E5FF",
            isVisible = true,
            isPreset = true,
            sortOrder = 1
        ),
        SourceCategory(
            id = ID_GOOGLE_MAPS,
            name = "Google Maps",
            colorHex = "#22C55E",
            isVisible = true,
            isPreset = true,
            sortOrder = 2
        ),
        SourceCategory(
            id = ID_GARMIN,
            name = "Garmin",
            colorHex = "#3B82F6",
            isVisible = true,
            isPreset = true,
            sortOrder = 3
        ),
        SourceCategory(
            id = ID_CUSTOM,
            name = "Custom",
            colorHex = "#E879F9",
            isVisible = true,
            isPreset = true,
            sortOrder = 4
        )
    )

    fun detectSourceId(
        creator: String? = null,
        trackType: String? = null,
        trackSrc: String? = null,
        metadataName: String? = null,
        trackName: String? = null,
        fileName: String? = null,
        availableSources: List<SourceCategory> = defaultSources
    ): String {
        val combined = listOfNotNull(
            creator,
            trackType,
            trackSrc,
            metadataName,
            trackName,
            fileName
        ).joinToString(" ").lowercase()

        if (combined.contains("strava")) return ID_STRAVA
        if (combined.contains("sports tracker") || combined.contains("sports-tracker") || combined.contains("sportstracker")) {
            return ID_SPORTS_TRACKER
        }
        if (combined.contains("google") || combined.contains("takeout") || combined.contains("my maps") || combined.contains("timeline")) {
            return ID_GOOGLE_MAPS
        }
        if (combined.contains("garmin") || combined.contains("basecamp") || combined.contains("forerunner") || combined.contains("fenix") || combined.contains("edge")) {
            return ID_GARMIN
        }

        // Check custom user-created source names as fallback
        for (source in availableSources) {
            if (!source.isPreset && source.name.isNotBlank() && combined.contains(source.name.lowercase())) {
                return source.id
            }
        }

        return ID_CUSTOM
    }
}
