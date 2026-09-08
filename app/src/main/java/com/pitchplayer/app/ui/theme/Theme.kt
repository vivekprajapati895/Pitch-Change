package com.pitchplayer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// A mixing-desk palette: near-black panel, warm amber for the value the user
// is actually adjusting, cool indigo for everything secondary.
private val Amber = Color(0xFFF5B841)
private val AmberDeep = Color(0xFF2A1F05)
private val Indigo = Color(0xFF8C9BFF)
private val Panel = Color(0xFF15151C)
private val PanelHigh = Color(0xFF20212B)
private val Ink = Color(0xFF0A0A0E)
private val Danger = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Amber,
    secondary = Indigo,
    onSecondary = Color(0xFF10132E),
    secondaryContainer = Color(0xFF23264A),
    onSecondaryContainer = Indigo,
    background = Ink,
    onBackground = Color(0xFFECECF2),
    surface = Panel,
    onSurface = Color(0xFFECECF2),
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Color(0xFFA9AAB8),
    outline = Color(0xFF3A3B48),
    outlineVariant = Color(0xFF2A2B36),
    error = Danger,
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFB4B4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6100),
    onPrimary = Color.White,
    secondary = Color(0xFF4A55C4),
    background = Color(0xFFF8F7F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEBE7)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        letterSpacing = (-2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun PitchPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
