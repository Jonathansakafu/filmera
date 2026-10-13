package com.filmera.app.core.render

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.filmera.app.core.face.FaceData
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterDefinition
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

data class PipelineStats(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val inputSize: Size? = null,
    val rotation: Int = 0,
    val glRenderer: String = "",
    val glesVersion: Int = 0,
    val cameraTransform: Boolean = false,
    val sensorRotation: Int = 0,
    /** True when the driver's texture matrix contained a rotation/mirror that we undo. */
    val textureCorrected: Boolean = false,
)

/**
 * The live filter pipeline:
 *
 *   CameraX Preview -> SurfaceTexture (OES texture) -> FilterRenderer ->
 *       screen surface + (while recording) encoder surface + (on demand) photo FBO
 *
 * Every frame is drawn once per output on a dedicated GL thread. No CPU copies happen
 * except for photo capture.
 */
class CameraGlPipeline : Preview.SurfaceProvider {

    private val thread = HandlerThread("FilmeraGL").apply { start() }
    private val handler = Handler(thread.looper)
    private val glExecutor = Executor { handler.post(it) }
    private val glDispatcher = handler.asCoroutineDispatcher()

    private lateinit var egl: EglCore
    private lateinit var idleSurface: EGLSurface
    private lateinit var renderer: FilterRenderer

    // Input (GL thread only)
    private var inputTexture: SurfaceTexture? = null
    private var oesTexture = 0
    private var inputSize: Size? = null
    private var inputRotation = 0
    private val stMatrix = FloatArray(16)

    // Outputs (GL thread only)
    private var displaySurface: EGLSurface? = null
    private var displaySize = Size(1, 1)
    private var recordSurface: EGLSurface? = null
    private var recordSize = Size(1, 1)
    private var recordRotation = 0
    private var pendingPhoto: ((Bitmap?) -> Unit)? = null

    // Inputs from other threads
    @Volatile var filter: FilterDefinition = FilterCatalog.ORIGINAL
    @Volatile var intensity: Float = 1f
    @Volatile var adjust: FloatArray = FloatArray(4)
    @Volatile var face: FaceData? = null
    /** Mirror the picture (front camera selfie view). Applies to every output. */
    @Volatile var mirror: Boolean = false
    /** Sensor orientation and facing of the bound camera (diagnostics only). */
    @Volatile var sensorRotation: Int = 0
    @Volatile var frontFacing: Boolean = false
    /** Manual fix from Settings for devices that still report the wrong rotation. */
    @Volatile var rotationOffset: Int = 0
    private var textureCorrection = Affine2D.IDENTITY
    private var loggedMatrix = false

    private val startNanos = System.nanoTime()

    private val _stats = MutableStateFlow(PipelineStats())
    val stats: StateFlow<PipelineStats> = _stats
    private var frameCount = 0
    private var frameTimeTotal = 0f
    private var windowStart = SystemClock.elapsedRealtime()

    /** Filter ids whose shaders failed on this GPU (reported in diagnostics). */
    @Volatile var failedFilters: Set<String> = emptySet()
        private set

    init {
        handler.post {
            egl = EglCore()
            idleSurface = egl.createPbufferSurface(1, 1)
            egl.makeCurrent(idleSurface)
            renderer = FilterRenderer()
            val gpu = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            GpuInfo.renderer = gpu
            GpuInfo.glesVersion = egl.glesVersion
            _stats.value = _stats.value.copy(glRenderer = gpu, glesVersion = egl.glesVersion)
        }
    }

    // ------------------------------------------------------------------ input

    override fun onSurfaceRequested(request: SurfaceRequest) {
        handler.post {
            val size = request.resolution
            // Each camera session gets its own texture; the old one is deleted when
            // CameraX releases its surface.
            val textureId = GlUtil.createOesTexture()
            val texture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(size.width, size.height)
            }
            val surface = Surface(texture)
            val previous = inputTexture
            inputTexture = texture
            oesTexture = textureId
            loggedMatrix = false
            inputSize = size
            texture.setOnFrameAvailableListener({ if (inputTexture === it) drawFrame() }, handler)
            request.setTransformationInfoListener(glExecutor) { info ->
                inputRotation = info.rotationDegrees
                Log.i(TAG, "Camera frames ${size.width}x${size.height}, rotation ${info.rotationDegrees}, " +
                    "HAL transform ${info.hasCameraTransform()}, sensor $sensorRotation, front $frontFacing")
                _stats.value = _stats.value.copy(cameraTransform = info.hasCameraTransform())
            }
            request.provideSurface(surface, glExecutor) {
                surface.release()
                texture.release()
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                if (inputTexture === texture) inputTexture = null
            }
            // The previous texture is released by its own provideSurface callback once
            // CameraX stops using it; detach the listener so it stops drawing.
            previous?.setOnFrameAvailableListener(null)
        }
    }

    // ---------------------------------------------------------------- outputs

    fun setDisplaySurface(surface: Surface, width: Int, height: Int) {
        handler.post {
            displaySurface?.let { egl.makeCurrent(idleSurface); egl.releaseSurface(it) }
            displaySurface = egl.createWindowSurface(surface)
            displaySize = Size(width, height)
        }
    }

    fun resizeDisplay(width: Int, height: Int) {
        handler.post { displaySize = Size(width, height) }
    }

    /** Blocks until the EGL surface is released so the caller can destroy the Surface. */
    fun clearDisplaySurface() {
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post {
            displaySurface?.let { egl.makeCurrent(idleSurface); egl.releaseSurface(it) }
            displaySurface = null
            done.countDown()
        }
        done.await(1, java.util.concurrent.TimeUnit.SECONDS)
    }

    /**
     * Starts sending frames to an encoder input surface of [width]x[height] whose file is
     * tagged to rotate [rotation] degrees on playback.
     */
    suspend fun startRecording(surface: Surface, width: Int, height: Int, rotation: Int) =
        withContext(glDispatcher) {
            recordSurface = egl.createWindowSurface(surface)
            recordSize = Size(width, height)
            recordRotation = rotation
        }

    suspend fun stopRecording() = withContext(glDispatcher) {
        recordSurface?.let { egl.makeCurrent(idleSurface); egl.releaseSurface(it) }
        recordSurface = null
    }

    /** Captures the next filtered frame at full camera resolution. */
    fun capturePhoto(callback: (Bitmap?) -> Unit) {
        handler.post { pendingPhoto = callback }
    }

    // ---------------------------------------------------------------- drawing

    private fun drawFrame() {
        val texture = inputTexture ?: return
        val size = inputSize ?: return
        val began = SystemClock.elapsedRealtimeNanos()
        try {
            egl.makeCurrent(displaySurface ?: idleSurface)
            texture.updateTexImage()
            texture.getTransformMatrix(stMatrix)
            val correction = FrameGeometry.orientationCorrection(stMatrix)
            if (correction != textureCorrection || !loggedMatrix) {
                loggedMatrix = true
                Log.i(TAG, "Texture matrix 2x2 [%.2f %.2f; %.2f %.2f] -> correction %s".format(
                    stMatrix[0], stMatrix[4], stMatrix[1], stMatrix[5],
                    if (correction == Affine2D.IDENTITY) "none" else correction.toString()))
            }
            textureCorrection = correction
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateTexImage failed", e)
            return
        }

        val params = FrameParams(
            filter = filter,
            intensity = intensity,
            adjust = adjust,
            timeSeconds = ((System.nanoTime() - startNanos) / 1e9 % 3600.0).toFloat(),
            face = face,
        )
        val mirrored = mirror

        displaySurface?.let { s ->
            val g = geometry(size, mirrored, displaySize.width, displaySize.height)
            renderer.draw(oesTexture, stMatrix, g, params)
            egl.swapBuffers(s)
        }

        recordSurface?.let { s ->
            egl.makeCurrent(s)
            val g = geometry(size, mirrored, recordSize.width, recordSize.height, outputRotation = recordRotation)
            renderer.draw(oesTexture, stMatrix, g, params)
            // Encoder timestamps share System.nanoTime's clock with the microphone, so
            // audio and video stay in sync.
            egl.setPresentationTime(s, System.nanoTime())
            egl.swapBuffers(s)
        }

        pendingPhoto?.let { callback ->
            pendingPhoto = null
            callback(renderPhoto(size, mirrored, params))
        }

        failedFilters = renderer.failedFilters.toSet()
        recordStats((SystemClock.elapsedRealtimeNanos() - began) / 1e6f, size)
    }

    private fun geometry(
        size: Size, mirrored: Boolean, outW: Int, outH: Int,
        outputRotation: Int = 0, bottomUp: Boolean = false,
    ) = FrameGeometry(
        size.width, size.height, (inputRotation + rotationOffset) % 360, mirrored, outW, outH,
        outputRotation = outputRotation, outputBottomUp = bottomUp,
        textureCorrection = textureCorrection,
    )

    private fun renderPhoto(size: Size, mirrored: Boolean, params: FrameParams): Bitmap? = try {
        egl.makeCurrent(idleSurface)
        val probe = geometry(size, mirrored, 1, 1)
        val w = probe.imageWidth
        val h = probe.imageHeight
        val fb = GlUtil.Framebuffer(w, h)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb.fbo)
        val g = geometry(size, mirrored, w, h, bottomUp = true)
        renderer.draw(oesTexture, stMatrix, g, params)
        val pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        fb.release()
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            pixels.rewind()
            copyPixelsFromBuffer(pixels)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Photo capture failed", e)
        null
    }

    private fun recordStats(frameMs: Float, size: Size) {
        frameCount++
        frameTimeTotal += frameMs
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStart
        if (elapsed >= 1000) {
            _stats.value = _stats.value.copy(
                fps = frameCount * 1000f / elapsed,
                frameTimeMs = frameTimeTotal / frameCount,
                inputSize = size,
                rotation = inputRotation,
                sensorRotation = sensorRotation,
                textureCorrected = textureCorrection != Affine2D.IDENTITY,
            )
            frameCount = 0
            frameTimeTotal = 0f
            windowStart = now
        }
    }

    fun release() {
        handler.post {
            inputTexture?.setOnFrameAvailableListener(null)
            displaySurface?.let { egl.releaseSurface(it) }
            recordSurface?.let { egl.releaseSurface(it) }
            renderer.release()
            egl.releaseSurface(idleSurface)
            egl.release()
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "CameraGlPipeline"
    }
}

/** GPU details captured the first time the pipeline starts (shown in diagnostics). */
object GpuInfo {
    @Volatile var renderer: String? = null
    @Volatile var glesVersion: Int = 0
}
