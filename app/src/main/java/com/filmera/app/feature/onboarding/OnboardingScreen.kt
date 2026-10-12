package com.filmera.app.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.filmera.app.core.ui.components.PrimaryButton
import com.filmera.app.core.ui.components.Wordmark
import com.filmera.app.core.ui.theme.FilmeraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Short branded intro shown after the system splash. */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val lift = remember { Animatable(14f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        lift.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        delay(550)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(FilmeraColors.Black), contentAlignment = Alignment.Center) {
        LensGlow(Modifier.size(320.dp).alpha(alpha.value * 0.9f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha.value).padding(top = lift.value.dp),
        ) {
            Wordmark(sizeSp = 34)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your camera. Enhanced in real time.",
                style = MaterialTheme.typography.bodyMedium,
                color = FilmeraColors.TextMuted,
            )
        }
    }
}

@Composable
private fun LensGlow(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glow")
    val pulse by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.scale(pulse)) {
        drawCircle(
            Brush.radialGradient(listOf(FilmeraColors.Emerald.copy(alpha = 0.22f), Color.Transparent)),
            radius = size.minDimension / 2,
        )
        drawCircle(FilmeraColors.Emerald.copy(alpha = 0.18f), radius = size.minDimension * 0.3f, style = Stroke(1.5.dp.toPx()))
        drawCircle(FilmeraColors.Emerald.copy(alpha = 0.08f), radius = size.minDimension * 0.42f, style = Stroke(1.dp.toPx()))
    }
}

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    Page(
        Icons.Rounded.AutoAwesome,
        "Look better on camera.",
        "FILMERA enhances your camera in real time — lighting, color, skin and detail, tuned on the live picture.",
    ),
    Page(
        Icons.Rounded.Face,
        "Live filters. Zero waiting.",
        "Effects run on the live camera feed, including face effects that follow you. What you see is exactly what you capture.",
    ),
    Page(
        Icons.AutoMirrored.Rounded.Send,
        "Snap, record, share anywhere.",
        "Save filtered photos and videos to your gallery, or send them straight to WhatsApp, Instagram, Telegram and more.",
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(
        Modifier.fillMaxSize().background(FilmeraColors.Black).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(sizeSp = 16)
            Spacer(Modifier.weight(1f))
            if (!last) {
                TextButton(onClick = onDone) { Text("Skip", color = FilmeraColors.TextMuted) }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    LensGlow(Modifier.size(260.dp))
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(30.dp))
                            .background(Brush.linearGradient(listOf(FilmeraColors.SurfaceHigh, FilmeraColors.Charcoal))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(page.icon, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(page.title, style = MaterialTheme.typography.displaySmall, color = FilmeraColors.Text)
                Spacer(Modifier.height(14.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = FilmeraColors.TextMuted)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.Start) {
            pages.indices.forEach { i ->
                val selected = i == pager.currentPage
                val w by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (selected) FilmeraColors.Emerald else FilmeraColors.Outline, label = "dotc")
                Box(Modifier.padding(end = 6.dp).height(8.dp).width(w).clip(CircleShape).background(c))
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (last) "GET STARTED" else "Next",
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Processing happens on your phone. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = FilmeraColors.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}
