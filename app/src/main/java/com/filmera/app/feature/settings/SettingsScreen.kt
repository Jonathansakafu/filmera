package com.filmera.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filmera.app.BuildConfig
import com.filmera.app.container
import com.filmera.app.core.camera.QualitySetting
import com.filmera.app.core.performance.DeviceInfo
import com.filmera.app.core.ui.components.GlassCard
import com.filmera.app.core.ui.components.ScreenHeader
import com.filmera.app.core.ui.components.SectionLabel
import com.filmera.app.core.ui.components.SettingsRow
import com.filmera.app.core.ui.components.StatusPill
import com.filmera.app.core.ui.components.ToggleRow
import com.filmera.app.core.ui.theme.FilmeraColors
import com.filmera.app.data.Settings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCompatibility: () -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.settings
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    val recommended = remember { DeviceInfo.collect(context).recommended }
    var versionTaps by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(FilmeraColors.Black)) {
        ScreenHeader("Settings", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            SectionLabel("Quality")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    QualitySetting.entries.forEach { q ->
                        val desc = q.fixedProfile()?.description
                            ?: "Starts at ${recommended.label} for this phone and steps down if video gets choppy."
                        QualityOption(
                            title = q.label,
                            subtitle = desc,
                            selected = settings.quality == q,
                            recommended = q == QualitySetting.AUTO,
                        ) { scope.launch { repo.setQuality(q) } }
                    }
                }
            }

            SectionLabel("Camera")
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow("Mirror front camera", "Show and save selfies the way you see yourself", Icons.Rounded.Flip, settings.mirrorFront) {
                        scope.launch { repo.setMirrorFront(it) }
                    }
                    Divider()
                    ToggleRow("Save to phone gallery", "Also copy every capture to Pictures/FILMERA and Movies/FILMERA", Icons.Rounded.PhotoLibrary, settings.autoSaveToGallery) {
                        scope.launch { repo.setAutoSave(it) }
                    }
                    Divider()
                    ToggleRow("Record sound", "Uses the microphone only while recording a video", Icons.Rounded.Mic, settings.recordAudio) {
                        scope.launch { repo.setRecordAudio(it) }
                    }
                }
            }

            SectionLabel("About")
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow("Device compatibility", "What this phone supports", Icons.Rounded.PhoneAndroid, onOpenCompatibility)
                    Divider()
                    SettingsRow("Privacy", "How your camera data is handled", Icons.Rounded.Shield, onOpenPrivacy)
                    Divider()
                    SettingsRow(
                        "Version ${BuildConfig.VERSION_NAME}",
                        if (settings.developerMode) "Developer mode is on" else null,
                        Icons.Rounded.Info,
                        onClick = {
                            versionTaps++
                            if (versionTaps >= 7 && !settings.developerMode) {
                                scope.launch { repo.setDeveloperMode(true) }
                            }
                        },
                        trailing = null,
                    )
                }
            }

            if (settings.developerMode) {
                SectionLabel("Developer")
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow("Performance overlay", "FPS, frame time, face latency, memory and thermal on the camera", Icons.Rounded.Speed, settings.showPerformanceOverlay) {
                            scope.launch { repo.setPerformanceOverlay(it) }
                        }
                        Divider()
                        ToggleRow("Developer mode", "Turn off to hide these options", Icons.Rounded.Code, true) {
                            scope.launch { repo.setPerformanceOverlay(false); repo.setDeveloperMode(false) }
                            versionTaps = 0
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = FilmeraColors.Outline, modifier = Modifier.padding(start = 66.dp))

@Composable
private fun QualityOption(title: String, subtitle: String, selected: Boolean, recommended: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(2.dp, if (selected) FilmeraColors.Emerald else FilmeraColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(FilmeraColors.Emerald))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill("RECOMMENDED", FilmeraColors.Emerald)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted)
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FilmeraColors.Black)) {
        ScreenHeader("Privacy", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                "Your live camera processing happens on your device.",
                style = MaterialTheme.typography.headlineSmall, color = FilmeraColors.Text,
            )
            Spacer(Modifier.height(18.dp))
            listOf(
                "No uploads" to "Camera frames are filtered on your phone's GPU. FILMERA has no server and sends no camera data anywhere.",
                "Face tracking stays local" to "Face effects use an on-device model. Face positions are used for the current frame only. Nothing is stored or sent.",
                "Nothing is recorded unless you press the shutter" to "Photos and videos are saved only when you capture them, in FILMERA's private folder and (if enabled) your gallery.",
                "Sharing is your choice" to "Captures leave your phone only when you share them with an app you pick.",
                "Minimal permissions" to "Camera (required), microphone (only for video sound, optional) and, on Android 9 or older, storage to save to your gallery. No contacts, no location, no accounts.",
                "No tracking" to "There are no analytics or ads SDKs in this app.",
            ).forEach { (title, body) ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Emerald)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.TextMuted)
                    }
                }
            }
        }
    }
}
