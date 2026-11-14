package com.safeguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SgPalette {
    val Deep = Color(0xFF05070C)
    val DeepElevated = Color(0xFF0B1220)
    val Card = Color(0xF20E1626)
    val CardStroke = Color(0x3325E5C4)
    val Teal = Color(0xFF25E5C4)
    val TealDim = Color(0xFF118A78)
    val Danger = Color(0xFFFF3B5C)
    val DangerDim = Color(0xFF8E1A2C)
    val Amber = Color(0xFFFFC857)
    val TextPrimary = Color(0xFFEAF2FF)
    val TextSecondary = Color(0xFF8FA3BF)
    val Grid = Color(0x1A25E5C4)
}

private val Scheme = darkColorScheme(
    primary = SgPalette.Teal,
    onPrimary = SgPalette.Deep,
    secondary = SgPalette.TealDim,
    background = SgPalette.Deep,
    onBackground = SgPalette.TextPrimary,
    surface = SgPalette.Card,
    onSurface = SgPalette.TextPrimary,
    error = SgPalette.Danger
)

private val SgTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp
    )
)

val Mono = FontFamily.Monospace

@Composable
fun SafeGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = SgTypography, content = content)
}
