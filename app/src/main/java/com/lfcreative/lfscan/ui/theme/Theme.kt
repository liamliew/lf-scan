package com.lfcreative.lfscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lfcreative.lfscan.R

// Mode accent colours — fixed regardless of theme (a "Check Out" mode bar looks the same in
// light/dark), and all pass >=4.5:1 with white text/icons on top, which is how they're used
// (TopAppBar backgrounds, solid status banners). Don't use LocalExtendedColors' theme-aware
// tokens for a solid-fill-plus-white-text banner — those are tuned the other way (colored
// text/icon ON a surface) and go too bright to host white text once dark theme swaps them in.
val Blue = Color(0xFF1565C0)
val Green = Color(0xFF2E7D32)
val Purple = Color(0xFF6A1B9A)
val PurpleLight = Color(0xFFC084FC) // Brighter shade of purple (light purple) for dark mode
val Grey = Color(0xFF616161)
val Amber = Color(0xFFB45309)
val Red = Color(0xFFB91C1C)

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

// outline bumped from the original 0xFFD1D5DB/0xFF52525B (1.47:1 / 2.16:1 against their
// background — well under WCAG 1.4.11's 3:1 non-text contrast minimum for a UI component
// boundary like a text field or card border) to values that clear 3:1 while staying as light
// as possible.
private val LightColorScheme = lightColorScheme(
    primary = Blue,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFF8B95A1),
    outlineVariant = Color(0xFFE5E7EB)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF33333A)
)

// Semantic status colors for icons/text (>=4.5:1 against their theme's surface — WCAG AA normal
// text) plus a soft "container" wash for badges/chips/left-bars, theme-aware via
// LocalExtendedColors. These replace ad-hoc literals like Color(0xFF4ade80) that were previously
// hardcoded the same in both themes and failed AA contrast in one or both (as low as 1.7:1) —
// always read these through LocalExtendedColors.current rather than reintroducing a raw hex.
data class ExtendedColors(
    val green: Color,
    val greenContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val red: Color,
    val redContainer: Color,
    val grey: Color,
    val greyContainer: Color,
    val blue: Color,
    val blueContainer: Color,
    val purple: Color,
    val purpleContainer: Color
)

private val LightExtendedColors = ExtendedColors(
    green = StatusAvailableText, greenContainer = StatusAvailableBg,
    amber = StatusCheckedOutText, amberContainer = StatusCheckedOutBg,
    red = StatusLostText, redContainer = StatusLostBg,
    grey = StatusUnderRepairText, greyContainer = StatusUnderRepairBg,
    blue = Color(0xFF1D4ED8), blueContainer = Color(0xFFDBEAFE),
    purple = Color(0xFF7E22CE), purpleContainer = Color(0xFFF3E8FF)
)

private val DarkExtendedColors = ExtendedColors(
    green = StatusAvailableTextDark, greenContainer = StatusAvailableBgDark,
    amber = StatusCheckedOutTextDark, amberContainer = StatusCheckedOutBgDark,
    red = StatusLostTextDark, redContainer = StatusLostBgDark,
    grey = StatusUnderRepairTextDark, greyContainer = StatusUnderRepairBgDark,
    blue = Color(0xFF60A5FA), blueContainer = Color(0xFF1E3A5F),
    purple = Color(0xFFC084FC), purpleContainer = Color(0xFF4C1D6F)
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val AppFontFamily = FontFamily(
    Font(R.font.roboto, weight = FontWeight.Normal),
    Font(R.font.roboto, weight = FontWeight.Medium),
    Font(R.font.roboto, weight = FontWeight.SemiBold),
    Font(R.font.roboto, weight = FontWeight.Bold)
)

val AppMonospaceFontFamily = FontFamily(
    Font(R.font.roboto_mono, weight = FontWeight.Normal),
    Font(R.font.roboto_mono, weight = FontWeight.Medium),
    Font(R.font.roboto_mono, weight = FontWeight.SemiBold),
    Font(R.font.roboto_mono, weight = FontWeight.Bold)
)

// Industrial/handheld-scanner visual direction: flat rectangular geometry everywhere, no rounded
// corners. Covers every Material3 component that relies on the theme's shape defaults (Button,
// Card, OutlinedTextField, Dialog, DropdownMenu, AlertDialog, ModalBottomSheet, etc.) — anything
// with an explicit `shape =`/`.clip(...)` argument bypasses this and must be flattened at the call
// site instead (see ScannerScreen/ModeSelectScreen/etc.). The ONE exception app-wide is PinScreen's
// passcode dots/cells, which stay rounded on purpose and are never touched by this.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

private val defaultTypography = Typography()

val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = AppFontFamily)
)

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LFScanTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = resolveDarkTheme(themeMode)
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
