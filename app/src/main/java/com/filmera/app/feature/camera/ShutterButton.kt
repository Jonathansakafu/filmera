package com.filmera.app.feature.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filmera.app.core.ui.theme.FilmeraColors
import kotlinx.coroutines.delay

/**
 * Photo mode: tap takes a photo, press-and-hold records until release.
 * Video mode: tap starts recording, tap again stops.
 */
@Composable
fun ShutterButton(
    mode: CaptureMode,
    recording: Boolean,
    recordStartedAt: Long,
    maxSeconds: Int,
    onTakePhoto: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var holdRecording by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val currentMode by rememberUpdatedState(mode)
    val isRecording by rememberUpdatedState(recording)

    LaunchedEffect(recording, recordStartedAt) {
        progress = 0f
        if (!recording) {
            holdRecording = false
            return@LaunchedEffect
        }
        while (true) {
            progress = ((System.currentTimeMillis() - recordStartedAt) / (maxSeconds * 1000f)).coerceIn(0f, 1f)
            delay(50)
        }
    }

    val scale by animateFloatAsState(if (pressed || recording) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "scale")
    val innerSize by animateDpAsState(if (recording) 30.dp else 62.dp, label = "inner")
    val innerCorner by animateDpAsState(if (recording) 8.dp else 31.dp, label = "corner")
    val innerColor by animateColorAsState(
        when {
            recording -> FilmeraColors.Danger
            mode == CaptureMode.VIDEO -> FilmeraColors.Danger
            else -> Color.White
        },
        label = "color",
    )

    Box(
        Modifier
            .size(84.dp)
            .scale(scale)
            .semantics {
                contentDescription = when {
                    recording -> "Stop recording"
                    mode == CaptureMode.VIDEO -> "Start recording"
                    else -> "Take photo, hold to record"
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        if (holdRecording) {
                            holdRecording = false
                            onStopRecording()
                        }
                    },
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when {
                            isRecording -> onStopRecording()
                            currentMode == CaptureMode.VIDEO -> onStartRecording()
                            else -> onTakePhoto()
                        }
                    },
                    onLongPress = {
                        if (currentMode == CaptureMode.PHOTO && !isRecording) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            holdRecording = true
                            onStartRecording()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = Color.White.copy(alpha = if (recording) 0.3f else 0.95f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke),
            )
            if (recording) {
                drawArc(
                    color = FilmeraColors.Danger,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(innerColor),
        )
    }
}
