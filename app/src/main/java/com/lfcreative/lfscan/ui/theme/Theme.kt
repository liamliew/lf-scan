package com.lfcreative.lfscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mode accent colours
val Blue = Color(0xFF1565C0)
val Green = Color(0xFF2E7D32)
val Purple = Color(0xFF6A1B9A)
val Grey = Color(0xFF616161)
val Amber = Color(0xFFB45309)

// Status badge colours (light theme)
val StatusAvailableBg = Color(0xFFDCFCE7)
val StatusAvailableText = Color(0xFF166534)
val StatusCheckedOutBg = Color(0xFFFEF3C7)
val StatusCheckedOutText = Color(0xFF92400E)
val StatusLostBg = Color(0xFFFEE2E2)
val StatusLostText = Color(0xFF991B1B)
val StatusUnderRepairBg = Color(0xFFF3F4F6)
val StatusUnderRepairText = Color(0xFF374151)

// Status badge colours (dark theme) — same hues, deepened background / lightened text so the
// badges keep their identity without turning into bright patches on a dark surface.
val StatusAvailableBgDark = Color(0xFF14532D)
val StatusAvailableTextDark = Color(0xFF86EFAC)
val StatusCheckedOutBgDark = Color(0xFF78350F)
val StatusCheckedOutTextDark = Color(0xFFFCD34D)
val StatusLostBgDark = Color(0xFF7F1D1D)
val StatusLostTextDark = Color(0xFFFCA5A5)
val StatusUnderRepairBgDark = Color(0xFF3F3F46)
val StatusUnderRepairTextDark = Color(0xFFD4D4D8)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2E),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF33333A)
)

@Composable
fun LFScanTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
