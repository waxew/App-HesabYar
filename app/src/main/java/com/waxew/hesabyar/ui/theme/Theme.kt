package com.waxew.hesabyar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.waxew.hesabyar.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1D4ED8),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFDBEAFE),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF172554),
    secondary = androidx.compose.ui.graphics.Color(0xFF0F766E),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFCCFBF1),
    background = androidx.compose.ui.graphics.Color(0xFFF7F9FC),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFF3F8)
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF93C5FD),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF172554),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1E3A8A),
    secondary = androidx.compose.ui.graphics.Color(0xFF5EEAD4),
    background = androidx.compose.ui.graphics.Color(0xFF0B1220),
    surface = androidx.compose.ui.graphics.Color(0xFF111827),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2937)
)

@Composable
fun HesabYarTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
