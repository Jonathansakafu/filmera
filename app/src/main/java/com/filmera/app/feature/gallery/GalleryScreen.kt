package com.filmera.app.feature.gallery

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.filmera.app.container
import com.filmera.app.core.media.Capture
import com.filmera.app.core.media.ShareTarget
import com.filmera.app.core.ui.components.PermissionCard
import com.filmera.app.core.ui.components.ScreenHeader
import com.filmera.app.core.ui.theme.FilmeraColors
import com.filmera.app.feature.home.CaptureThumb
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(onBack: () -> Unit, onOpenCamera: () -> Unit, onOpenCapture: (String) -> Unit) {
    val container = LocalContext.current.container
    val captures by container.captures.captures.collectAsState()
    LaunchedEffect(Unit) { container.captures.refresh() }

    Column(Modifier.fillMaxSize().background(FilmeraColors.Black)) {
        ScreenHeader("Captures", onBack)
        if (captures.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PermissionCard(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Nothing here yet",
                    body = "Tap the shutter for a photo, or hold it to record a video. Your captures show up here.",
                    action = "Open camera",
                    onAction = onOpenCamera,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(captures, key = { it.name }) { c ->
                    CaptureThumb(c, Modifier.fillMaxWidth().aspectRatio(0.75f)) { onOpenCapture(c.name) }
                }
            }
        }
    }
}

@Composable
fun ViewerScreen(name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val capture = remember(name) { container.captures.find(name) }
    val targets = remember { container.share.installedTargets() }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    if (capture == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (capture.isVideo) {
            AndroidView(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(capture.file.absolutePath)
                        setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                        setOnPreparedListener { mp -> mp.isLooping = true; start() }
                    }
                },
                onRelease = { it.stopPlayback() },
            )
        } else {
            AsyncImage(
                model = capture.file,
                contentDescription = "Capture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.35f))) {
            ScreenHeader(if (capture.isVideo) "Video" else "Photo", onBack) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = FilmeraColors.Text) }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(FilmeraColors.Charcoal)
                .navigationBarsPadding()
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Share to", style = MaterialTheme.typography.titleMedium, color = FilmeraColors.Text,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(targets) { t -> ShareTile(t.name, t.name.take(1)) { share(context, capture, t) } }
                item { ShareTile("More", null) { share(context, capture, null) } }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(FilmeraColors.SurfaceHigh)
                        .clickable {
                            scope.launch {
                                val ok = container.captures.exportToGallery(capture)
                                note = if (ok) "Saved to your gallery" else "Couldn't save to the gallery"
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to gallery", style = MaterialTheme.typography.labelLarge, color = FilmeraColors.Text)
                }
                Spacer(Modifier.width(12.dp))
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = FilmeraColors.Surface,
            title = { Text("Delete this ${if (capture.isVideo) "video" else "photo"}?", color = FilmeraColors.Text) },
            text = { Text("It will be removed from FILMERA. Copies already saved to your gallery stay there.", color = FilmeraColors.TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { container.captures.delete(capture); onBack() }
                }) { Text("Delete", color = FilmeraColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = FilmeraColors.Text) } },
        )
    }
}

private fun share(context: android.content.Context, capture: Capture, target: ShareTarget?) {
    context.container.share.share(capture, target)
}

@Composable
private fun ShareTile(label: String, initial: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(FilmeraColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(initial, style = MaterialTheme.typography.titleLarge, color = FilmeraColors.Emerald)
            } else {
                Icon(Icons.Rounded.IosShare, null, tint = FilmeraColors.Emerald)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = FilmeraColors.Text, maxLines = 1)
    }
}
