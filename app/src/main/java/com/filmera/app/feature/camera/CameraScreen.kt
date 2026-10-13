package com.filmera.app.feature.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.filmera.app.core.face.FaceTracker
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterCategory
import com.filmera.app.core.ui.components.CameraControlButton
import com.filmera.app.core.ui.components.FilterChip
import com.filmera.app.core.ui.components.PermissionCard
import com.filmera.app.core.ui.theme.FilmeraColors
import kotlinx.coroutines.delay

private const val HOLD_MAX_SECONDS = 60
private const val VIDEO_MAX_SECONDS = 600

@Composable
fun CameraScreen(
    initialCategory: FilterCategory?,
    initialFilterId: String?,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenCapture: (String) -> Unit,
    vm: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()

    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        cameraGranted = result[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (initialCategory != null) vm.selectCategory(initialCategory)
        if (initialFilterId != null) vm.selectFilter(FilterCatalog.find(initialFilterId))
        if (!cameraGranted) launcher.launch(permissions)
    }
    LaunchedEffect(cameraGranted) {
        if (cameraGranted) vm.bind(lifecycleOwner)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.stopRecording()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!cameraGranted) {
            PermissionCard(
                icon = Icons.Rounded.CameraAlt,
                title = "Camera access needed",
                body = "Camera permission is required to use Live Filters. Your camera feed is processed on this phone and never uploaded.",
                action = if (askedOnce) "Open settings" else "Allow camera",
                onAction = {
                    if (askedOnce) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else launcher.launch(permissions)
                },
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            CameraControlButton(
                Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack,
                Modifier.statusBarsPadding().padding(16.dp),
            )
            // Re-check when returning from system settings.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            return@Box
        }

        CameraPreview(vm, state)
        StudioOverlay(vm, state, onBack, onOpenGallery, onOpenCapture)
    }
}

@Composable
private fun CameraPreview(vm: CameraViewModel, state: CameraUiState) {
    val zoom by rememberUpdatedState(state.zoom)
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, scale, _ ->
                    if (scale != 1f) vm.setZoom(zoom + (scale - 1f) * 0.6f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { vm.flipCamera() })
            },
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    private var attached = false
                    override fun surfaceCreated(holder: SurfaceHolder) {}
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (!attached) {
                            vm.pipeline.setDisplaySurface(holder.surface, width, height)
                            attached = true
                        } else {
                            vm.pipeline.resizeDisplay(width, height)
                        }
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        attached = false
                        vm.pipeline.clearDisplaySurface()
                    }
                })
            }
        },
    )
}

@Composable
private fun BoxScope.StudioOverlay(
    vm: CameraViewModel,
    state: CameraUiState,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenCapture: (String) -> Unit,
) {
    var showAdjust by remember { mutableStateOf(false) }

    // Top scrim + bar
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraControlButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, enabled = !state.recording)
            Spacer(Modifier.weight(1f))
            if (state.recording) {
                RecordingTimer(state.recordStartedAt)
            } else {
                QualityPill(
                    if (state.debug.shortSide > 0) "${state.debug.shortSide}p · ${state.profile.fps}"
                    else state.profile.shortLabel,
                )
            }
            Spacer(Modifier.weight(1f))
            CameraControlButton(
                if (state.torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                "Flash", vm::toggleTorch,
                active = state.torch,
                enabled = state.hasFlash,
            )
            Spacer(Modifier.width(10.dp))
            CameraControlButton(Icons.Rounded.Tune, "Adjust", { showAdjust = true }, active = state.adjust.any { it != 0f })
        }
        Spacer(Modifier.height(12.dp))
        FaceHint(state)
        if (state.settings.developerMode && state.settings.showPerformanceOverlay) {
            Spacer(Modifier.height(8.dp))
            DebugOverlay(state)
        }
        state.cameraError?.let { CameraErrorCard(it, vm) }
    }

    // Bottom controls
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .navigationBarsPadding()
            .padding(top = 40.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MessagePill(state, vm, onOpenCapture)

        AnimatedVisibility(
            visible = !state.recording,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.filter.id != FilterCatalog.ORIGINAL.id) {
                    IntensitySlider(state.filter.name, state.intensity, vm::setIntensity)
                }
                CategoryTabs(state.category, vm::selectCategory)
                Spacer(Modifier.height(10.dp))
                FilterCarousel(state, vm)
                Spacer(Modifier.height(14.dp))
                ModeSwitch(state.mode, vm::setMode)
                Spacer(Modifier.height(14.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GalleryThumb(state, onOpenGallery)
            ShutterButton(
                mode = state.mode,
                recording = state.recording,
                recordStartedAt = state.recordStartedAt,
                maxSeconds = if (state.mode == CaptureMode.VIDEO) VIDEO_MAX_SECONDS else HOLD_MAX_SECONDS,
                onTakePhoto = vm::takePhoto,
                onStartRecording = { vm.startRecording(if (state.mode == CaptureMode.VIDEO) VIDEO_MAX_SECONDS else HOLD_MAX_SECONDS) },
                onStopRecording = vm::stopRecording,
            )
            CameraControlButton(Icons.Rounded.Cameraswitch, "Switch camera", vm::flipCamera, size = 52.dp, enabled = !state.recording)
        }
    }

    if (showAdjust) AdjustSheet(state, vm) { showAdjust = false }
}

@Composable
private fun CameraErrorCard(message: String, vm: CameraViewModel) {
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FilmeraColors.Surface)
            .border(1.dp, FilmeraColors.Danger.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text("No camera picture", style = MaterialTheme.typography.titleMedium, color = FilmeraColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.TextMuted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.filmera.app.core.ui.components.PrimaryButton("Try again", vm::retryCamera, Modifier.weight(1f))
            com.filmera.app.core.ui.components.SecondaryButton(
                "Send report", { vm.sendProblemReport("Camera shows no picture") }, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QualityPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(FilmeraColors.Glass)
            .border(1.dp, FilmeraColors.GlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun RecordingTimer(startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(FilmeraColors.Danger).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(8.dp))
        Text(
            "%d:%02d".format(secs / 60, secs % 60),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
        )
    }
}

@Composable
private fun FaceHint(state: CameraUiState) {
    val text = when {
        !state.filter.requiresFaceTracking -> null
        state.cameraMode == com.filmera.app.core.camera.CameraController.Mode.SAFE ->
            "Face effects are off in safe mode on this device"
        state.faceStatus == FaceTracker.Status.UNAVAILABLE -> "Face tracking isn't available on this device"
        state.faceStatus == FaceTracker.Status.LOADING -> "Starting face tracking…"
        !state.faceVisible -> "Looking for a face…"
        else -> null
    }
    AnimatedVisibility(text != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(FilmeraColors.Glass).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun DebugOverlay(state: CameraUiState) {
    val d = state.debug
    val lines = buildList {
        add("FPS ${"%.1f".format(d.fps)} / ${state.profile.fps} · ${state.cameraMode.label}")
        add("Frame ${"%.1f".format(d.frameTimeMs)} ms (GPU path)")
        add("Input ${d.resolution} · rot ${d.rotation}° · sensor ${d.sensorRotation}° · driver rotation ${if (d.cameraTransform) "undone" else "none"}")
        if (state.filter.requiresFaceTracking) add("Face ${d.faceLatencyMs} ms · ${if (state.faceVisible) "tracked" else "none"}")
        add("Memory ${d.memoryMb} MB · Thermal ${d.thermal}")
        add("Filter ${state.filter.id} · ${state.filter.performance}")
        if (d.gpu.isNotEmpty()) add(d.gpu)
        if (d.failedFilters.isNotEmpty()) add("Failed shaders: ${d.failedFilters.joinToString()}")
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(10.dp),
    ) {
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = FilmeraColors.Emerald)
        }
    }
}

@Composable
private fun MessagePill(state: CameraUiState, vm: CameraViewModel, onOpenCapture: (String) -> Unit) {
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            vm.consumeMessage()
        }
    }
    AnimatedVisibility(message != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
        Row(
            Modifier
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(FilmeraColors.Surface)
                .border(1.dp, FilmeraColors.GlassBorder, RoundedCornerShape(50))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Color.White)
            val last = state.lastCapture
            if (last != null && message?.contains("saved") == true) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "View & share",
                    style = MaterialTheme.typography.labelMedium,
                    color = FilmeraColors.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(FilmeraColors.Emerald)
                        .clickable { vm.consumeMessage(); onOpenCapture(last.name) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun IntensitySlider(name: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.width(78.dp))
        Slider(
            value = value, onValueChange = onChange,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = FilmeraColors.Emerald,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Text(
            "${(value * 100).toInt()}",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = FilmeraColors.TextMuted,
            modifier = Modifier.width(34.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun CategoryTabs(selected: FilterCategory?, onSelect: (FilterCategory?) -> Unit) {
    val tabs = listOf<FilterCategory?>(null) + FilterCategory.entries
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tabs) { cat ->
            val isSel = cat == selected
            Text(
                cat?.label ?: "All",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium),
                color = if (isSel) FilmeraColors.Black else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSel) Color.White else Color.Transparent)
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun FilterCarousel(state: CameraUiState, vm: CameraViewModel) {
    val filters = remember(state.category) {
        state.category?.let { listOf(FilterCatalog.ORIGINAL) + FilterCatalog.inCategory(it).filter { f -> f.id != "original" } }
            ?: FilterCatalog.all
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.category) { listState.scrollToItem(0) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(filters, key = { it.id }) { f ->
            FilterChip(f, selected = f.id == state.filter.id, onClick = { vm.selectFilter(f) })
        }
    }
}

@Composable
private fun ModeSwitch(mode: CaptureMode, onChange: (CaptureMode) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(FilmeraColors.Glass).padding(3.dp),
    ) {
        CaptureMode.entries.forEach { m ->
            val sel = m == mode
            Text(
                if (m == CaptureMode.PHOTO) "Photo" else "Video",
                style = MaterialTheme.typography.labelMedium,
                color = if (sel) FilmeraColors.Black else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) FilmeraColors.Emerald else Color.Transparent)
                    .clickable { onChange(m) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GalleryThumb(state: CameraUiState, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FilmeraColors.Glass)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable(enabled = !state.recording, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val last = state.lastCapture
        if (last != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(last.file)
                    .apply { if (last.isVideo) decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) } }
                    .crossfade(true).build(),
                contentDescription = "Last capture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.PhotoLibrary, "Gallery", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSheet(state: CameraUiState, vm: CameraViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = FilmeraColors.Charcoal,
        scrimColor = Color.Black.copy(alpha = 0.2f),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enhance", style = MaterialTheme.typography.titleLarge, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
                Text(
                    "Reset", style = MaterialTheme.typography.labelLarge, color = FilmeraColors.Emerald,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.resetAdjust() }.padding(8.dp),
                )
            }
            Text(
                "Applied on top of any filter, live.",
                style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FilmeraColors.SurfaceHigh)
                    .clickable { vm.rotatePicture() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Rotate90DegreesCw, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Picture sideways? Rotate it", style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text)
                    Text(
                        "Fix for phones that report the camera angle wrong. Saved for next time.",
                        style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted,
                    )
                }
                Text(
                    "${state.settings.rotationFix}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.settings.rotationFix == 0) FilmeraColors.TextMuted else FilmeraColors.Emerald,
                )
            }
            Spacer(Modifier.height(16.dp))
            listOf("Brightness", "Contrast", "Saturation", "Warmth").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
                    val v = state.adjust[i]
                    Text(
                        (if (v > 0) "+" else "") + (v * 100).toInt(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = FilmeraColors.TextMuted,
                    )
                }
                Slider(
                    value = state.adjust[i],
                    onValueChange = { vm.setAdjust(i, it) },
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = FilmeraColors.Emerald,
                        inactiveTrackColor = FilmeraColors.Outline,
                    ),
                )
            }
        }
    }
}
