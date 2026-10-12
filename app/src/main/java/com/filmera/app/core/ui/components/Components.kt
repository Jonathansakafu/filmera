package com.filmera.app.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filmera.app.core.performance.Support
import com.filmera.app.core.ui.theme.FilmeraColors

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(FilmeraColors.Emerald, FilmeraColors.EmeraldDeep))
                else Brush.horizontalGradient(listOf(FilmeraColors.SurfaceHigh, FilmeraColors.SurfaceHigh)),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = FilmeraColors.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) FilmeraColors.Black else FilmeraColors.TextFaint)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FilmeraColors.SurfaceHigh)
            .border(1.dp, FilmeraColors.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = FilmeraColors.Text, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = FilmeraColors.Text)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(FilmeraColors.Surface)
            .border(1.dp, FilmeraColors.GlassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** Round translucent button used over the camera preview. */
@Composable
fun CameraControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) FilmeraColors.Emerald else FilmeraColors.Glass)
            .border(1.dp, if (active) Color.Transparent else FilmeraColors.GlassBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = if (active) FilmeraColors.Black else Color.White,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
fun SupportBadge(support: Support, modifier: Modifier = Modifier) {
    val color = when (support) {
        Support.SUPPORTED -> FilmeraColors.Emerald
        Support.LIMITED -> FilmeraColors.Warning
        Support.EXPERIMENTAL -> FilmeraColors.Info
        Support.NOT_AVAILABLE -> FilmeraColors.Danger
        Support.PLANNED -> FilmeraColors.TextMuted
    }
    StatusPill(support.label.uppercase(), color, modifier, pulse = support == Support.EXPERIMENTAL)
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "pill")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f, targetValue = if (pulse) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).alpha(dotAlpha).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = FilmeraColors.Text)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = FilmeraColors.Text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FilmeraColors.TextFaint,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 20.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = {
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = FilmeraColors.TextFaint)
    },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(FilmeraColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = FilmeraColors.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FilmeraColors.TextMuted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(title, subtitle, icon, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FilmeraColors.Black,
                checkedTrackColor = FilmeraColors.Emerald,
                uncheckedThumbColor = FilmeraColors.TextMuted,
                uncheckedTrackColor = FilmeraColors.SurfaceHigh,
                uncheckedBorderColor = FilmeraColors.Outline,
            ),
        )
    }
}

/** Shown in place of the camera when permission is missing. */
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(FilmeraColors.EmeraldGlow),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = FilmeraColors.Emerald, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = FilmeraColors.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium, color = FilmeraColors.TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            PrimaryButton(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier, sizeSp: Int = 20) {
    Text(
        "FILMERA",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = androidx.compose.ui.unit.TextUnit(sizeSp.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = FontWeight.Black,
            letterSpacing = androidx.compose.ui.unit.TextUnit(sizeSp * 0.18f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = FilmeraColors.Text,
    )
}
