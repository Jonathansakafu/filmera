package com.filmera.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FilmeraColors {
    val Black = Color(0xFF0A0B0D)
    val Charcoal = Color(0xFF131518)
    val Surface = Color(0xFF181B1F)
    val SurfaceHigh = Color(0xFF20242A)
    val Outline = Color(0xFF2A2F36)
    val Emerald = Color(0xFF2BD99F)
    val EmeraldDeep = Color(0xFF12A577)
    val EmeraldGlow = Color(0x332BD99F)
    val Text = Color(0xFFF4F6F8)
    val TextMuted = Color(0xFF98A1AC)
    val TextFaint = Color(0xFF5E6772)
    val Danger = Color(0xFFFF5A5F)
    val Warning = Color(0xFFFFB547)
    val Info = Color(0xFF6CB4FF)
    val Glass = Color(0x99141619)
    val GlassBorder = Color(0x1FFFFFFF)
}

private val scheme = darkColorScheme(
    primary = FilmeraColors.Emerald,
    onPrimary = FilmeraColors.Black,
    secondary = FilmeraColors.EmeraldDeep,
    background = FilmeraColors.Black,
    onBackground = FilmeraColors.Text,
    surface = FilmeraColors.Surface,
    onSurface = FilmeraColors.Text,
    surfaceVariant = FilmeraColors.SurfaceHigh,
    onSurfaceVariant = FilmeraColors.TextMuted,
    outline = FilmeraColors.Outline,
    error = FilmeraColors.Danger,
)

private val base = FontFamily.Default

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.8).sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = base, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = base, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = base, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp),
)

@Composable
fun FilmeraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
