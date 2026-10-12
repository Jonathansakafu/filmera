package com.filmera.app.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.filmera.app.core.ui.components.GlassCard
import com.filmera.app.core.ui.components.PrimaryButton
import com.filmera.app.core.ui.components.StatusPill
import com.filmera.app.core.ui.theme.FilmeraColors

private data class AppPermission(
    val permission: String,
    val icon: ImageVector,
    val title: String,
    val reason: String,
    val required: Boolean,
)

/** Every runtime permission FILMERA uses on this Android version. */
private fun appPermissions(): List<AppPermission> = buildList {
    add(
        AppPermission(
            Manifest.permission.CAMERA, Icons.Rounded.CameraAlt, "Camera",
            "Shows the live preview and applies filters. Frames are processed on your phone and never uploaded.",
            required = true,
        ),
    )
    add(
        AppPermission(
            Manifest.permission.RECORD_AUDIO, Icons.Rounded.Mic, "Microphone",
            "Adds sound to the videos you record. Only used while recording.",
            required = false,
        ),
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(
            AppPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Icons.Rounded.Folder, "Storage",
                "Saves your photos and videos to the phone's gallery.",
                required = false,
            ),
        )
    }
}

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Explains and requests each permission. Shown after onboarding and whenever camera access is missing. */
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val perms = remember { appPermissions() }
    var refresh by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf(false) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        refresh++
    }

    // Re-check after returning from system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reading `refresh` makes these recompute after each grant.
    val states = remember(refresh) { perms.associate { it.permission to granted(it.permission) } }
    val allGranted = states.values.all { it }
    val cameraGranted = states[Manifest.permission.CAMERA] == true
    val missing = perms.filter { states[it.permission] != true }.map { it.permission }

    Column(
        Modifier.fillMaxSize().background(FilmeraColors.Black).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Allow access", style = MaterialTheme.typography.displaySmall, color = FilmeraColors.Text)
            Spacer(Modifier.height(10.dp))
            Text(
                "FILMERA needs a few permissions to work. You can change them anytime in your phone's settings.",
                style = MaterialTheme.typography.bodyLarge, color = FilmeraColors.TextMuted,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                perms.forEach { p ->
                    PermissionRow(p, states[p.permission] == true)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "FILMERA has no internet access. Nothing you capture leaves your phone unless you share it.",
                style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextFaint,
            )
        }

        Column(Modifier.padding(24.dp)) {
            when {
                allGranted -> PrimaryButton("CONTINUE", onContinue, Modifier.fillMaxWidth())
                // Android stops showing the dialog after repeated denials; send the user to settings.
                asked && !cameraGranted -> PrimaryButton(
                    "OPEN SETTINGS",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    Modifier.fillMaxWidth(),
                )
                else -> PrimaryButton(
                    if (missing.size == perms.size) "ALLOW ALL" else "ALLOW REMAINING",
                    { launcher.launch(missing.toTypedArray()) },
                    Modifier.fillMaxWidth(),
                )
            }
            if (!allGranted) {
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (cameraGranted) "Continue without optional permissions" else "Not now",
                        color = FilmeraColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(p: AppPermission, granted: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(FilmeraColors.EmeraldGlow),
                contentAlignment = Alignment.Center,
            ) { Icon(p.icon, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
                    when {
                        granted -> StatusPill("ALLOWED", FilmeraColors.Emerald)
                        p.required -> StatusPill("REQUIRED", FilmeraColors.Warning)
                        else -> StatusPill("OPTIONAL", FilmeraColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(p.reason, style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted)
            }
        }
    }
}
