package app.later

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val bg: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val onAccent: Color, val warn: Color, val soft: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFEDF0EC), surface = Color(0xFFF8F9F6), ink = Color(0xFF1E2622), muted = Color(0xFF6B746F),
    line = Color(0xFFD8DDD7), accent = Color(0xFF2E5E4E), onAccent = Color(0xFFF4F7F3),
    warn = Color(0xFFB8572F), soft = Color(0xFFE1E7E1),
)
val DarkPalette = Palette(
    bg = Color(0xFF151A17), surface = Color(0xFF1D2420), ink = Color(0xFFE6EBE7), muted = Color(0xFF8E9792),
    line = Color(0xFF2C3530), accent = Color(0xFF8FC4AE), onAccent = Color(0xFF10201A),
    warn = Color(0xFFE59B76), soft = Color(0xFF232B27),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun LaterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, onSurfaceVariant = p.muted, outline = p.line,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, onSurfaceVariant = p.muted, outline = p.line,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
