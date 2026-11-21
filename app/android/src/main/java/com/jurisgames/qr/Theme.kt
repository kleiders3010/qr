package com.jurisgames.qr

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The Tasklist look: the default theme of Tasks and Sobres (seed hue 265, tonal spot), 12 dp corners. */
private val Light = lightColorScheme(
    primary = Color(0xFF435E91), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD7E2FF), onPrimaryContainer = Color(0xFF001A40),
    secondary = Color(0xFF565E71), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2C),
    tertiary = Color(0xFF705574), onTertiary = Color(0xFFFFFFFF), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF1A1B20), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E), surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EE), outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0), surfaceTint = Color(0xFF435E91),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFACC7FF), onPrimary = Color(0xFF0E2F60), primaryContainer = Color(0xFF294677), onPrimaryContainer = Color(0xFFD7E2FF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283041), secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCDF), onTertiary = Color(0xFF3F2844), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9), surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0), surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF282A2F), outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E), surfaceTint = Color(0xFFACC7FF),
)

@Composable
fun QrTheme(content: @Composable () -> Unit) {
    val shapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp))
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, shapes = shapes, content = content)
}
