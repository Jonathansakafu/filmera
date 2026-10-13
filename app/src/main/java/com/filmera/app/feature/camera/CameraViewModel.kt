package com.filmera.app.feature.camera

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.filmera.app.container
import com.filmera.app.core.camera.CameraController
import com.filmera.app.core.camera.QualityProfile
import com.filmera.app.core.camera.QualitySetting
import com.filmera.app.core.face.FaceTracker
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterCategory
import com.filmera.app.core.filter.FilterDefinition
import com.filmera.app.core.media.Capture
import com.filmera.app.core.media.VideoRecorder
import com.filmera.app.core.performance.DeviceInfo
import com.filmera.app.core.render.CameraGlPipeline
import com.filmera.app.data.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

enum class CaptureMode { PHOTO, VIDEO }

data class DebugInfo(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val faceLatencyMs: Long = 0,
    val resolution: String = "-",
    /** Short side of the frames the camera actually delivers (e.g. 960 for 1280×960). */
    val shortSide: Int = 0,
    val rotation: Int = 0,
    val memoryMb: Long = 0,
    val thermal: String = "-",
    val gpu: String = "",
    val cameraTransform: Boolean = false,
    val sensorRotation: Int = 0,
    val failedFilters: Set<String> = emptySet(),
)

data class CameraUiState(
    val front: Boolean = true,
    val filter: FilterDefinition = FilterCatalog.ORIGINAL,
    val category: FilterCategory? = null,
    val intensity: Float = 1f,
    /** brightness, contrast, saturation, warmth */
    val adjust: List<Float> = listOf(0f, 0f, 0f, 0f),
    val torch: Boolean = false,
    val hasFlash: Boolean = false,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val recording: Boolean = false,
    val recordStartedAt: Long = 0,
    val profile: QualityProfile = QualityProfile.BALANCED,
    val faceStatus: FaceTracker.Status = FaceTracker.Status.LOADING,
    val faceVisible: Boolean = false,
    val lastCapture: Capture? = null,
    val message: String? = null,
    val cameraError: String? = null,
    val cameraMode: CameraController.Mode = CameraController.Mode.BEST,
    val zoom: Float = 0f,
    val debug: DebugInfo = DebugInfo(),
    val settings: Settings = Settings(),
)

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.container
    val pipeline = CameraGlPipeline()
    private val controller = CameraController(app)
    private val recorder = VideoRecorder(app)
    private val faceTracker = FaceTracker(app) { face -> pipeline.face = face }

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state

    private var owner: WeakReference<LifecycleOwner>? = null
    private var session: VideoRecorder.Session? = null
    private var recordLimitJob: Job? = null
    private val intensities = HashMap<String, Float>()
    private var slowWindows = 0
    private var boundAt = 0L
    private var autoProfile: QualityProfile? = null
    private var filterChosen = false
    private var watchdog: Job? = null

    init {
        viewModelScope.launch {
            val initial = container.settings.settings.first()
            _state.update { it.copy(settings = initial) }
            // A filter picked from the home screen wins over the last-used one.
            if (!filterChosen) selectFilter(FilterCatalog.find(initial.lastFilter), remember = false)
            var quality = initial.quality
            container.settings.settings.collect { s ->
                _state.update { it.copy(settings = s) }
                pipeline.mirror = _state.value.front && s.mirrorFront
                pipeline.rotationOffset = s.rotationFix
                faceTracker.rotationOffset = s.rotationFix
                if (s.quality != quality) {
                    quality = s.quality
                    onQualityChanged()
                }
            }
        }
        viewModelScope.launch { container.captures.refresh() }
        viewModelScope.launch {
            container.captures.captures.collect { list ->
                _state.update { it.copy(lastCapture = list.firstOrNull()) }
            }
        }
        viewModelScope.launch { monitorPerformance() }
    }

    // ------------------------------------------------------------ camera

    fun bind(lifecycleOwner: LifecycleOwner) {
        owner = WeakReference(lifecycleOwner)
        rebind()
    }

    private fun currentProfile(settings: Settings): QualityProfile =
        settings.quality.fixedProfile() ?: autoProfile ?: DeviceInfo.collect(getApplication()).recommended
            .also { autoProfile = it }

    private fun savedMode(settings: Settings, front: Boolean): CameraController.Mode =
        (if (front) settings.cameraModeFront else settings.cameraModeBack)
            ?.let { runCatching { CameraController.Mode.valueOf(it) }.getOrNull() }
            ?: CameraController.Mode.BEST

    private fun rebind(mode: CameraController.Mode? = null) {
        val lifecycleOwner = owner?.get() ?: return
        watchdog?.cancel()
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            val profile = currentProfile(settings)
            val front = _state.value.front
            val useMode = mode ?: savedMode(settings, front)
            faceTracker.frameStride = profile.faceStride
            try {
                val camera = controller.bind(
                    lifecycleOwner, front, profile, pipeline, faceTracker, useMode,
                ) { sensor, isFront ->
                    pipeline.sensorRotation = sensor
                    pipeline.frontFacing = isFront
                }
                Log.i(TAG, "Bound ${if (front) "front" else "back"} camera in $useMode, profile $profile")
                camera.cameraInfo.cameraState.observe(lifecycleOwner) { cs ->
                    cs.error?.let { Log.w(TAG, "Camera state ${cs.type} error ${it.code}", it.cause) }
                }
                pipeline.mirror = front && settings.mirrorFront
                boundAt = System.currentTimeMillis()
                slowWindows = 0
                _state.update {
                    it.copy(
                        profile = profile,
                        hasFlash = camera.cameraInfo.hasFlashUnit(),
                        torch = false,
                        zoom = 0f,
                        cameraError = null,
                        cameraMode = useMode,
                        faceStatus = faceTracker.status,
                    )
                }
                startWatchdog(front, useMode)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed in $useMode", e)
                val next = nextMode(useMode)
                if (next != null) rebind(next)
                else _state.update { it.copy(cameraError = CAMERA_FAILED) }
            }
        }
    }

    private fun nextMode(mode: CameraController.Mode) =
        CameraController.Mode.entries.getOrNull(mode.ordinal + 1)

    /**
     * Some cameras accept a configuration and then never send a frame. If nothing arrives
     * in time, fall back to the next, more compatible mode, and remember the one that works.
     */
    private fun startWatchdog(front: Boolean, mode: CameraController.Mode) {
        val startFrames = pipeline.framesReceived
        watchdog = viewModelScope.launch {
            delay(WATCHDOG_MS)
            if (pipeline.framesReceived > startFrames) {
                val saved = savedMode(container.settings.settings.first(), front)
                if (saved != mode) container.settings.setCameraMode(front, mode.name)
                return@launch
            }
            Log.w(TAG, "No camera frames in $mode after ${WATCHDOG_MS}ms")
            val next = nextMode(mode)
            if (next != null) {
                toast("Camera switched to ${next.label.lowercase()} for this device")
                rebind(next)
            } else {
                _state.update { it.copy(cameraError = CAMERA_FAILED) }
            }
        }
    }

    /** Clears the remembered mode and starts again from full quality. */
    fun retryCamera() {
        viewModelScope.launch {
            container.settings.setCameraMode(_state.value.front, CameraController.Mode.BEST.name)
            _state.update { it.copy(cameraError = null) }
            rebind(CameraController.Mode.BEST)
        }
    }

    /** Opens the share sheet with a problem report (device facts + this app's log). */
    fun sendProblemReport(note: String?) {
        viewModelScope.launch {
            val report = com.filmera.app.core.performance.ProblemReport.build(getApplication(), note)
            container.share.shareText("FILMERA problem report", report)
        }
    }

    fun flipCamera() {
        if (_state.value.recording) return
        _state.update { it.copy(front = !it.front) }
        rebind()
    }

    fun toggleTorch() {
        val cam = controller.camera ?: return
        val on = !_state.value.torch
        cam.cameraControl.enableTorch(on)
        _state.update { it.copy(torch = on) }
    }

    fun setZoom(linear: Float) {
        val z = linear.coerceIn(0f, 1f)
        controller.camera?.cameraControl?.setLinearZoom(z)
        _state.update { it.copy(zoom = z) }
    }

    // ----------------------------------------------------------- filters

    fun selectFilter(filter: FilterDefinition, remember: Boolean = true) {
        pipeline.filter = filter
        val intensity = intensities[filter.id] ?: filter.defaultIntensity
        pipeline.intensity = intensity
        faceTracker.enabled = filter.requiresFaceTracking
        _state.update { it.copy(filter = filter, intensity = intensity) }
        if (remember) {
            filterChosen = true
            viewModelScope.launch { container.settings.useFilter(filter.id) }
        }
    }

    fun selectCategory(category: FilterCategory?) {
        _state.update { it.copy(category = category) }
    }

    fun setIntensity(value: Float) {
        intensities[_state.value.filter.id] = value
        pipeline.intensity = value
        _state.update { it.copy(intensity = value) }
    }

    fun setAdjust(index: Int, value: Float) {
        val next = _state.value.adjust.toMutableList().also { it[index] = value }
        pipeline.adjust = next.toFloatArray()
        _state.update { it.copy(adjust = next) }
    }

    fun resetAdjust() {
        pipeline.adjust = FloatArray(4)
        _state.update { it.copy(adjust = listOf(0f, 0f, 0f, 0f)) }
    }

    /** Rotates the camera picture 90° clockwise (saved), for devices that report it wrong. */
    fun rotatePicture() {
        val next = (_state.value.settings.rotationFix + 90) % 360
        viewModelScope.launch { container.settings.setRotationFix(next) }
    }

    fun setMode(mode: CaptureMode) {
        if (!_state.value.recording) _state.update { it.copy(mode = mode) }
    }

    // ----------------------------------------------------------- capture

    fun takePhoto() {
        if (_state.value.recording) return
        pipeline.capturePhoto { bitmap ->
            viewModelScope.launch {
                if (bitmap == null) {
                    toast("Couldn't take the photo. Try again.")
                    return@launch
                }
                val capture = container.captures.savePhoto(bitmap)
                bitmap.recycle()
                afterCapture(capture, "Photo")
            }
        }
    }

    fun startRecording(maxSeconds: Int) {
        if (_state.value.recording) return
        val app = getApplication<Application>()
        val settings = _state.value.settings
        val audio = settings.recordAudio &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val profile = _state.value.profile
        val stats = pipeline.stats.value
        val input = stats.inputSize ?: profile.previewSize
        val quarter = stats.rotation % 180 != 0 || stats.inputSize == null
        val uprightW = if (quarter) input.height else input.width
        val uprightH = if (quarter) input.width else input.height

        viewModelScope.launch {
            try {
                val s = recorder.start(
                    container.captures.newVideoFile(), uprightW, uprightH,
                    profile.fps, profile.videoBitrate, audio,
                )
                pipeline.startRecording(s.surface, s.width, s.height, s.rotation)
                session = s
                _state.update { it.copy(recording = true, recordStartedAt = System.currentTimeMillis()) }
                recordLimitJob = launch {
                    delay(maxSeconds * 1000L)
                    stopRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed to start", e)
                toast("Recording isn't available right now.")
            }
        }
    }

    fun stopRecording() {
        val s = session ?: return
        session = null
        recordLimitJob?.cancel()
        _state.update { it.copy(recording = false) }
        viewModelScope.launch {
            pipeline.stopRecording()
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { recorder.stop(s) }
            if (!ok) {
                toast("Hold a little longer to record a video.")
                return@launch
            }
            container.captures.refresh()
            val capture = container.captures.find(s.file.name) ?: return@launch
            afterCapture(capture, "Video")
        }
    }

    private suspend fun afterCapture(capture: Capture, kind: String) {
        val settings = _state.value.settings
        if (settings.autoSaveToGallery) {
            val saved = container.captures.exportToGallery(capture)
            toast(if (saved) "$kind saved to your gallery" else "$kind saved in FILMERA")
        } else {
            toast("$kind saved in FILMERA")
        }
        _state.update { it.copy(lastCapture = capture) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun toast(text: String) = _state.update { it.copy(message = text) }

    // ------------------------------------------------------- performance

    private suspend fun monitorPerformance() {
        val app = getApplication<Application>()
        val power = app.getSystemService(PowerManager::class.java)
        while (true) {
            delay(1000)
            val stats = pipeline.stats.value
            val st = _state.value
            val runtime = Runtime.getRuntime()
            val memMb = (runtime.totalMemory() - runtime.freeMemory() + Debug.getNativeHeapAllocatedSize()) / (1024 * 1024)
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normal"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                    else -> "Critical"
                }
            } else "n/a"
            _state.update {
                it.copy(
                    faceStatus = faceTracker.status,
                    faceVisible = faceTracker.faceVisible,
                    debug = DebugInfo(
                        fps = stats.fps,
                        frameTimeMs = stats.frameTimeMs,
                        faceLatencyMs = if (st.filter.requiresFaceTracking) faceTracker.latencyMs else 0,
                        resolution = stats.inputSize?.let { s -> "${s.width}×${s.height}" } ?: "-",
                        shortSide = stats.inputSize?.let { s -> minOf(s.width, s.height) } ?: 0,
                        rotation = stats.rotation,
                        memoryMb = memMb,
                        thermal = thermal,
                        gpu = stats.glRenderer,
                        cameraTransform = stats.textureCorrected,
                        sensorRotation = stats.sensorRotation,
                        failedFilters = pipeline.failedFilters,
                    ),
                )
            }
            maybeStepDown(stats.fps, st)
        }
    }

    /** In AUTO, drops one quality level after ~4 s of clearly low frame rate. */
    private fun maybeStepDown(fps: Float, st: CameraUiState) {
        if (st.settings.quality != QualitySetting.AUTO || st.recording) return
        if (fps <= 0f || System.currentTimeMillis() - boundAt < 4000) return
        slowWindows = if (fps < st.profile.fps * 0.72f) slowWindows + 1 else 0
        if (slowWindows < 4) return
        val lower = when (st.profile) {
            QualityProfile.ULTRA -> QualityProfile.BALANCED
            QualityProfile.BALANCED -> QualityProfile.PERFORMANCE
            QualityProfile.PERFORMANCE -> return
        }
        autoProfile = lower
        toast("Performance mode enabled to keep video smooth")
        rebind(st.cameraMode)
    }

    fun onQualityChanged() {
        autoProfile = null
        rebind()
    }

    override fun onCleared() {
        session?.let { s ->
            kotlinx.coroutines.runBlocking { pipeline.stopRecording() }
            recorder.stop(s)
        }
        controller.shutdown()
        faceTracker.close()
        pipeline.release()
    }

    private companion object {
        const val TAG = "CameraViewModel"
        const val WATCHDOG_MS = 6000L
        const val CAMERA_FAILED =
            "The camera isn't sending a picture. Close other camera apps, then tap Try again. " +
                "If it keeps happening, tap Send report."
    }
}
