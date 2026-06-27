package com.lfcreative.lfscan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mode accent colours
val Blue = Color(0xFF1565C0)
val Green = Color(0xFF2E7D32)
val Purple = Color(0xFF6A1B9A)
val Grey = Color(0xFF616161)

// Status badge colours
val StatusAvailableBg = Color(0xFFDCFCE7)
val StatusAvailableText = Color(0xFF166534)
val StatusCheckedOutBg = Color(0xFFFEF3C7)
val StatusCheckedOutText = Color(0xFF92400E)
val StatusLostBg = Color(0xFFFEE2E2)
val StatusLostText = Color(0xFF991B1B)
val StatusUnderRepairBg = Color(0xFFF3F4F6)
val StatusUnderRepairText = Color(0xFF374151)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
)

@Composable
fun LFScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
