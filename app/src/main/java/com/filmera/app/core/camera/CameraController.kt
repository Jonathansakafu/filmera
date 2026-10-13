package com.filmera.app.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds CameraX use cases: a Preview feeding the GL pipeline and an ImageAnalysis feeding
 * face tracking. Both use 4:3 (the sensor's native shape) so face landmarks line up with the picture.
 */
class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    var camera: Camera? = null
        private set

    /**
     * Camera configurations from best to most compatible. Some cameras accept a
     * configuration and then never deliver frames, so the caller tries the next mode when
     * no picture arrives (see CameraViewModel's watchdog).
     */
    enum class Mode(val label: String) {
        /** 4:3 sensor-native size from the quality profile, fixed frame rate, face tracking. */
        BEST("Full quality"),
        /** 16:9 at 720p, camera-chosen frame rate, face tracking. */
        COMPATIBLE("Compatibility"),
        /** Preview only at the camera's default size. No face tracking. */
        SAFE("Safe mode"),
    }

    suspend fun bind(
        owner: LifecycleOwner,
        front: Boolean,
        profile: QualityProfile,
        surfaceProvider: Preview.SurfaceProvider,
        analyzer: ImageAnalysis.Analyzer,
        mode: Mode = Mode.BEST,
        /** Called with (sensorRotationDegrees, isFrontFacing) before frames start. */
        onCameraSelected: (Int, Boolean) -> Unit = { _, _ -> },
    ): Camera {
        val provider = provider ?: awaitProvider().also { provider = it }
        val selector = selectorFor(provider, front)
        selector.filter(provider.availableCameraInfos).firstOrNull()?.let { info ->
            onCameraSelected(info.sensorRotationDegrees, info.lensFacing == CameraSelector.LENS_FACING_FRONT)
        }

        val previewBuilder = Preview.Builder()
        when (mode) {
            Mode.BEST -> {
                previewBuilder.setResolutionSelector(resolution(profile.previewSize, fourByThree = true))
                applyFrameRate(previewBuilder, provider, selector, profile.fps)
            }
            Mode.COMPATIBLE -> previewBuilder.setResolutionSelector(
                resolution(android.util.Size(1280, 720), fourByThree = false),
            )
            Mode.SAFE -> Unit
        }
        val preview = previewBuilder.build().apply { setSurfaceProvider(surfaceProvider) }

        provider.unbindAll()
        if (mode == Mode.SAFE) {
            return provider.bindToLifecycle(owner, selector, preview).also { camera = it }
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                if (mode == Mode.BEST) resolution(profile.analysisSize, fourByThree = true)
                else resolution(android.util.Size(640, 360), fourByThree = false),
            )
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, analyzer) }
        return provider.bindToLifecycle(owner, selector, preview, analysis).also { camera = it }
    }

    fun hasCamera(front: Boolean): Boolean {
        val p = provider ?: return true
        return p.hasCamera(if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
    }

    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }

    private fun selectorFor(provider: ProcessCameraProvider, front: Boolean): CameraSelector {
        val wanted = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return if (provider.hasCamera(wanted)) wanted
        else if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    private fun resolution(size: android.util.Size, fourByThree: Boolean) = ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            if (fourByThree) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY,
        )
        .setResolutionStrategy(
            ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
        )
        .build()

    /** Requests a fixed frame rate when the camera supports one close to the target. */
    @SuppressLint("UnsafeOptInUsageError")
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun applyFrameRate(
        builder: Preview.Builder,
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        fps: Int,
    ) {
        try {
            val info = selector.filter(provider.availableCameraInfos).firstOrNull() ?: return
            val ranges = Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: return
            val best = pickFpsRange(ranges.toList(), fps) ?: return
            Camera2Interop.Extender(builder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, best)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set frame rate", e)
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        private const val TAG = "CameraController"

        /**
         * Prefers a fixed range [fps, fps] (steady motion), then the range with the
         * target as its upper bound and the highest lower bound.
         */
        fun pickFpsRange(ranges: List<Range<Int>>, fps: Int): Range<Int>? =
            ranges.firstOrNull { it.lower == fps && it.upper == fps }
                ?: ranges.filter { it.upper == fps }.maxByOrNull { it.lower }
                ?: ranges.filter { it.upper >= fps && it.lower <= fps }.minByOrNull { it.upper - it.lower }
    }
}
