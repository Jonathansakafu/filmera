package com.filmera.app.feature.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filmera.app.container
import com.filmera.app.core.performance.DeviceInfo
import com.filmera.app.core.ui.components.GlassCard
import com.filmera.app.core.ui.components.ScreenHeader
import com.filmera.app.core.ui.components.SecondaryButton
import com.filmera.app.core.ui.components.SectionLabel
import com.filmera.app.core.ui.components.SupportBadge
import com.filmera.app.core.ui.theme.FilmeraColors
import com.filmera.app.data.Settings

@Composable
fun CompatibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val report = remember { DeviceInfo.collect(context) }
    val settings by context.container.settings.settings.collectAsState(initial = Settings())

    Column(Modifier.fillMaxSize().background(FilmeraColors.Black)) {
        ScreenHeader("Device compatibility", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(report.model, style = MaterialTheme.typography.titleLarge, color = FilmeraColors.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Recommended quality: ${report.recommended.label} (${report.recommended.shortLabel} fps)",
                        style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.Emerald,
                    )
                }
            }

            SectionLabel("Hardware")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Fact("Android", "${report.androidVersion} (API ${report.sdkInt})")
                    Fact("Processor", "${report.soc} · ${report.cpuCores} cores")
                    Fact("Memory", "${"%.1f".format(report.totalRamMb / 1024f)} GB RAM")
                    Fact("GPU", report.gpu + if (report.glesVersion > 0) " · GLES ${report.glesVersion}" else "")
                    Fact("Camera API", "Camera2 via CameraX · best level ${report.bestCameraLevel.label}", last = report.cameras.isEmpty())
                    report.cameras.forEachIndexed { i, c ->
                        Fact(
                            "${c.facing} camera",
                            "${c.level.label} · max ${c.maxPreview}${if (c.hasFlash) " · flash" else ""}\nFPS ranges ${c.fpsRanges}",
                            last = i == report.cameras.lastIndex,
                        )
                    }
                }
            }

            SectionLabel("What works on this phone")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.capabilities.forEach { cap ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cap.name, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                SupportBadge(cap.support)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(cap.detail, style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted)
                        }
                    }
                }
            }

            if (settings.developerMode) {
                Spacer(Modifier.height(20.dp))
                SecondaryButton(
                    "Export diagnostics",
                    { context.container.share.shareText("FILMERA diagnostics", report.asText()) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.IosShare,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.TextMuted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
    }
    if (!last) HorizontalDivider(color = FilmeraColors.Outline, modifier = Modifier.padding(horizontal = 16.dp))
}
