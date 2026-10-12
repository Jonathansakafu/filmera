package com.filmera.app.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.filmera.app.container
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterCategory
import com.filmera.app.core.media.Capture
import com.filmera.app.core.ui.components.CameraControlButton
import com.filmera.app.core.ui.components.FilterChip
import com.filmera.app.core.ui.components.GlassCard
import com.filmera.app.core.ui.components.PrimaryButton
import com.filmera.app.core.ui.components.SectionLabel
import com.filmera.app.core.ui.components.Wordmark
import com.filmera.app.core.ui.theme.FilmeraColors
import com.filmera.app.data.Settings
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenCamera: (category: FilterCategory?, filterId: String?) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenCapture: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCompatibility: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsState(initial = Settings())
    val captures by container.captures.captures.collectAsState()
    val shareApps = remember { container.share.installedTargets() }
    LaunchedEffect(Unit) { container.captures.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(FilmeraColors.Black)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(sizeSp = 17)
            Spacer(Modifier.weight(1f))
            CameraControlButton(Icons.Rounded.PhoneAndroid, "Device compatibility", onOpenCompatibility, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            CameraControlButton(Icons.Rounded.Settings, "Settings", onOpenSettings, size = 40.dp)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.TextMuted)
            Text("Ready to look better?", style = MaterialTheme.typography.headlineMedium, color = FilmeraColors.Text)
            Spacer(Modifier.height(18.dp))
            LiveCameraCard { onOpenCamera(null, null) }
        }

        val recent = settings.recentFilters.map { FilterCatalog.find(it) }.filter { it.id != "original" }
        if (recent.isNotEmpty()) {
            SectionLabel("Recent filters", Modifier.padding(start = 20.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recent, key = { it.id }) { f ->
                    FilterChip(f, selected = false, onClick = { onOpenCamera(f.category, f.id) })
                }
            }
        }

        SectionLabel("Categories", Modifier.padding(start = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val cats = FilterCategory.entries
            cats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        CategoryTile(cat, Modifier.weight(1f)) { onOpenCamera(cat, null) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (captures.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                SectionLabel("Your captures")
                Spacer(Modifier.weight(1f))
                Text(
                    "See all", style = MaterialTheme.typography.labelMedium, color = FilmeraColors.Emerald,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenGallery).padding(6.dp),
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(captures.take(10), key = { it.name }) { c -> CaptureThumb(c) { onOpenCapture(c.name) } }
            }
        }

        SectionLabel("Share anywhere", Modifier.padding(start = 20.dp))
        GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Send filtered photos and videos to your apps",
                    style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text,
                )
                Spacer(Modifier.height(10.dp))
                val names = shareApps.map { it.name }.ifEmpty { listOf("Any app with sharing") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.take(3).forEach { AppTag(it) }
                    if (names.size > 3) AppTag("+${names.size - 3}")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Live filters inside other apps' video calls aren't possible on Android for regular apps. See Device compatibility for details.",
                    style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun LiveCameraCard(onOpen: () -> Unit) {
    val t = rememberInfiniteTransition(label = "card")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000)), label = "sweep")
    val breathe by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breathe")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F2A22), FilmeraColors.Charcoal, Color(0xFF0B1714))))
            .border(1.dp, FilmeraColors.GlassBorder, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.36f)
            val r = size.minDimension * 0.34f
            drawCircle(Brush.radialGradient(listOf(FilmeraColors.Emerald.copy(alpha = 0.25f), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
            drawCircle(FilmeraColors.Emerald.copy(alpha = 0.35f), r, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(FilmeraColors.Emerald.copy(alpha = 0.15f), r * 0.7f, c, style = Stroke(1.dp.toPx()))
            drawArc(
                FilmeraColors.Emerald, sweep, 70f, false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(FilmeraColors.Emerald.copy(alpha = breathe)))
                Spacer(Modifier.width(8.dp))
                Text("LIVE CAMERA", style = MaterialTheme.typography.labelSmall, color = FilmeraColors.Emerald)
            }
            Spacer(Modifier.height(6.dp))
            Text("${FilterCatalog.all.size - 1} filters, applied live", style = MaterialTheme.typography.headlineSmall, color = FilmeraColors.Text)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("OPEN CAMERA", onOpen, icon = Icons.Rounded.CameraAlt)
        }
    }
}

private fun FilterCategory.icon(): ImageVector = when (this) {
    FilterCategory.COLOR -> Icons.Rounded.Palette
    FilterCategory.LIGHTING -> Icons.Rounded.WbSunny
    FilterCategory.BEAUTY -> Icons.Rounded.AutoAwesome
    FilterCategory.FACE -> Icons.Rounded.Face
    FilterCategory.FUN -> Icons.Rounded.TheaterComedy
}

@Composable
private fun CategoryTile(cat: FilterCategory, modifier: Modifier, onClick: () -> Unit) {
    val count = FilterCatalog.inCategory(cat).count { it.id != "original" }
    val swatch = FilterCatalog.inCategory(cat).firstOrNull { it.id != "original" }?.swatch
    GlassCard(modifier, corner = 20.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(
                    if (swatch != null) Brush.linearGradient(listOf(Color(swatch.first), Color(swatch.second)))
                    else Brush.linearGradient(listOf(FilmeraColors.SurfaceHigh, FilmeraColors.SurfaceHigh)),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(cat.icon(), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(cat.label, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text)
                Text("$count filters", style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted)
            }
        }
    }
}

@Composable
fun CaptureThumb(capture: Capture, modifier: Modifier = Modifier.size(width = 96.dp, height = 128.dp), onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(FilmeraColors.Surface).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(capture.file)
                .apply { if (capture.isVideo) decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) } }
                .crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (capture.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                val s = capture.durationMs / 1000
                Text("%d:%02d".format(s / 60, s % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun AppTag(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        color = FilmeraColors.Text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(FilmeraColors.SurfaceHigh).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
