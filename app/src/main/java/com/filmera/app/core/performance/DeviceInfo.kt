package com.filmera.app.core.performance

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.filmera.app.core.camera.CameraLevel
import com.filmera.app.core.camera.QualityProfile
import com.filmera.app.core.render.GpuInfo

data class CameraFacts(
    val id: String,
    val facing: String,
    val level: CameraLevel,
    val maxPreview: String,
    val fpsRanges: String,
    val hasFlash: Boolean,
)

/** Support levels shown on badges. */
enum class Support(val label: String) { SUPPORTED("Supported"), LIMITED("Limited"), EXPERIMENTAL("Experimental"), NOT_AVAILABLE("Not available"), PLANNED("Planned") }

data class Capability(val name: String, val support: Support, val detail: String)

data class DeviceReport(
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val soc: String,
    val cpuCores: Int,
    val totalRamMb: Long,
    val gpu: String,
    val glesVersion: Int,
    val cameras: List<CameraFacts>,
    val bestCameraLevel: CameraLevel,
    val recommended: QualityProfile,
    val capabilities: List<Capability>,
) {
    fun asText(): String = buildString {
        appendLine("FILMERA diagnostics")
        appendLine("Device: $model")
        appendLine("Android: $androidVersion (API $sdkInt)")
        appendLine("SoC: $soc · $cpuCores cores · ${totalRamMb} MB RAM")
        appendLine("GPU: $gpu (GLES $glesVersion)")
        appendLine("Recommended profile: ${recommended.label}")
        cameras.forEach {
            appendLine("Camera ${it.id} (${it.facing}): ${it.level.label}, max ${it.maxPreview}, fps ${it.fpsRanges}, flash=${it.hasFlash}")
        }
        capabilities.forEach { appendLine("${it.name}: ${it.support.label} — ${it.detail}") }
    }
}

object DeviceInfo {

    fun collect(context: Context): DeviceReport {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramMb = mem.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val cameras = cameraFacts(context)
        val best = cameras.maxOfOrNull { it.level } ?: CameraLevel.LEGACY
        val front = cameras.firstOrNull { it.facing == "Front" }?.level ?: best

        return DeviceReport(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE,
            cpuCores = cores,
            totalRamMb = ramMb,
            gpu = GpuInfo.renderer ?: "Open the camera once to detect",
            glesVersion = GpuInfo.glesVersion,
            cameras = cameras,
            bestCameraLevel = best,
            recommended = QualityProfile.recommend(ramMb, cores, front),
            capabilities = capabilities(context, cameras),
        )
    }

    private fun capabilities(context: Context, cameras: List<CameraFacts>): List<Capability> {
        val pm = context.packageManager
        val external = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL)
        return listOf(
            Capability(
                "Live filters", Support.SUPPORTED,
                "GPU shaders run on the live camera feed on this device.",
            ),
            Capability(
                "Face effects",
                if (cameras.any { it.facing == "Front" }) Support.SUPPORTED else Support.LIMITED,
                "On-device face tracking (MediaPipe). Works best with the front camera.",
            ),
            Capability(
                "Photo & video capture", Support.SUPPORTED,
                "Filtered photos and clips are saved on your phone.",
            ),
            Capability(
                "Share to WhatsApp, Instagram, Telegram", Support.SUPPORTED,
                "Uses Android's standard share sheet.",
            ),
            Capability(
                "Live camera inside other apps' calls", Support.NOT_AVAILABLE,
                "Android only lets system apps create virtual cameras. WhatsApp and Instagram " +
                    "read the phone's real cameras directly, so no Play Store app can replace " +
                    "their video during a call without root.",
            ),
            Capability(
                "External USB camera (UVC)",
                if (external) Support.EXPERIMENTAL else Support.NOT_AVAILABLE,
                if (external) "Phone accepts USB cameras, but calling apps may not list them."
                else "This phone does not report USB camera support.",
            ),
            Capability(
                "FILMERA Call (filtered live calls)", Support.PLANNED,
                "Built-in calls over WebRTC so the other person sees your filters. Not built yet.",
            ),
        )
    }

    private fun cameraFacts(context: Context): List<CameraFacts> = try {
        val cm = context.getSystemService(CameraManager::class.java)
        cm.cameraIdList.map { id ->
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraMetadata.LENS_FACING_FRONT -> "Front"
                CameraMetadata.LENS_FACING_BACK -> "Back"
                else -> "External"
            }
            val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> CameraLevel.LEGACY
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> CameraLevel.EXTERNAL
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> CameraLevel.LIMITED
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> CameraLevel.FULL
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> CameraLevel.LEVEL_3
                else -> CameraLevel.LIMITED
            }
            val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val max = sizes.maxByOrNull { it.width.toLong() * it.height }
            val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
                .joinToString(" ") { "${it.lower}-${it.upper}" }
            CameraFacts(
                id = id,
                facing = facing,
                level = level,
                maxPreview = max?.let { "${it.width}×${it.height}" } ?: "Unknown",
                fpsRanges = fps.ifEmpty { "Unknown" },
                hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
