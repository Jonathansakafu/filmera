package com.filmera.app.core.camera

import android.util.Size

enum class QualityProfile(
    val label: String,
    val description: String,
    /** Camera preview size in landscape (sensor) orientation. */
    val previewSize: Size,
    val fps: Int,
    /** Face analysis frame size (landscape) and how often to run it. */
    val analysisSize: Size,
    val faceStride: Int,
    val videoBitrate: Int,
) {
    PERFORMANCE(
        "Performance", "720p · 24 fps · lighter face tracking. Best for older phones.",
        Size(1280, 720), 24, Size(480, 270), 3, 4_000_000,
    ),
    BALANCED(
        "Balanced", "720p · 30 fps. Smooth on most phones.",
        Size(1280, 720), 30, Size(640, 360), 2, 6_000_000,
    ),
    ULTRA(
        "Ultra", "1080p · 30 fps · full-rate face tracking. For recent high-end phones.",
        Size(1920, 1080), 30, Size(640, 360), 1, 12_000_000,
    ),
    ;

    val shortLabel get() = "${previewSize.height}p · $fps"

    companion object {
        /**
         * Picks a starting profile from hardware facts. Running FPS is still monitored
         * and AUTO steps down if the phone can't keep up.
         */
        fun recommend(totalRamMb: Long, cpuCores: Int, cameraHardwareLevel: CameraLevel): QualityProfile =
            when {
                totalRamMb < 3_000 || cameraHardwareLevel == CameraLevel.LEGACY -> PERFORMANCE
                totalRamMb >= 6_000 && cpuCores >= 8 &&
                    cameraHardwareLevel >= CameraLevel.FULL -> ULTRA
                else -> BALANCED
            }
    }
}

/** User-facing setting: a fixed profile, or AUTO (recommended + adaptive). */
enum class QualitySetting(val label: String) {
    AUTO("Auto"), PERFORMANCE("Performance"), BALANCED("Balanced"), ULTRA("Ultra");

    fun fixedProfile(): QualityProfile? = when (this) {
        AUTO -> null
        PERFORMANCE -> QualityProfile.PERFORMANCE
        BALANCED -> QualityProfile.BALANCED
        ULTRA -> QualityProfile.ULTRA
    }
}

/** Camera2 hardware levels, ordered by capability. */
enum class CameraLevel(val label: String) {
    LEGACY("Legacy"), EXTERNAL("External"), LIMITED("Limited"), FULL("Full"), LEVEL_3("Level 3");
}
