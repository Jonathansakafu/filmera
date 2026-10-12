package com.filmera.app.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs MediaPipe Face Landmarker on camera frames (on-device; frames never leave the
 * phone) and publishes smoothed [FaceData] through [onFace].
 *
 * Only runs while [enabled] is true, i.e. while a face filter is selected.
 */
class FaceTracker(
    context: Context,
    private val onFace: (FaceData?) -> Unit,
) : ImageAnalysis.Analyzer {

    enum class Status { LOADING, READY, UNAVAILABLE }

    @Volatile var enabled = false
        set(value) {
            field = value
            if (!value) {
                smoother.reset()
                onFace(null)
            }
        }

    /** Process every Nth frame. Set from the quality profile. */
    @Volatile var frameStride = 1

    @Volatile var status = Status.LOADING
        private set

    /** Time from submitting a frame to receiving landmarks, in ms. */
    @Volatile var latencyMs = 0L
        private set

    @Volatile var faceVisible = false
        private set

    private val smoother = LandmarkSmoother()
    private val busy = AtomicBoolean(false)
    private var frameCounter = 0L
    private var lastTimestamp = 0L
    private var lastSeenAt = 0L
    private val submitTimes = HashMap<Long, Long>()

    private val landmarker: FaceLandmarker? = try {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onResult)
            .setErrorListener { e -> Log.w(TAG, "Face landmarker error", e); busy.set(false) }
            .build()
        FaceLandmarker.createFromOptions(context, options).also { status = Status.READY }
    } catch (e: Throwable) {
        // Includes UnsatisfiedLinkError: MediaPipe ships no native library for some
        // CPU types (e.g. x86_64). The rest of the app keeps working without face effects.
        Log.e(TAG, "Face tracking unavailable", e)
        status = Status.UNAVAILABLE
        null
    }

    override fun analyze(image: ImageProxy) {
        val lm = landmarker
        if (lm == null || !enabled || frameCounter++ % frameStride != 0L || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val upright: Bitmap
        try {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            upright = if (rotation == 0) raw else {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame conversion failed", e)
            busy.set(false)
            return
        } finally {
            image.close()
        }

        // MediaPipe requires strictly increasing timestamps.
        val ts = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1)
        lastTimestamp = ts
        synchronized(submitTimes) { submitTimes[ts] = SystemClock.uptimeMillis() }
        try {
            lm.detectAsync(BitmapImageBuilder(upright).build(), ts)
        } catch (e: Exception) {
            Log.w(TAG, "detectAsync failed", e)
            busy.set(false)
        }
    }

    private fun onResult(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        busy.set(false)
        val now = SystemClock.uptimeMillis()
        synchronized(submitTimes) {
            submitTimes.remove(result.timestampMs())?.let { latencyMs = now - it }
            if (submitTimes.size > 16) submitTimes.clear()
        }
        if (!enabled) return
        val face = result.faceLandmarks().firstOrNull()
        if (face == null || face.size < FaceData.REQUIRED_POINTS) {
            // Keep the last position briefly so effects don't flicker on a missed frame.
            if (now - lastSeenAt > LOST_AFTER_MS) {
                faceVisible = false
                smoother.reset()
                onFace(null)
            }
            return
        }
        lastSeenAt = now
        faceVisible = true
        val data = FaceData.fromLandmarks { i -> floatArrayOf(face[i].x(), face[i].y()) }
        onFace(smoother.smooth(data))
    }

    fun close() {
        enabled = false
        landmarker?.close()
    }

    private companion object {
        const val TAG = "FaceTracker"
        const val MODEL = "face_landmarker.task"
        const val LOST_AFTER_MS = 300L
    }
}
