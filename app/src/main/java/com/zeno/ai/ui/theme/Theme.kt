package com.zeno.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZBg = Color(0xFF05070D)
val ZBg2 = Color(0xFF0A0F24)
val ZCard = Color(0xFF111833)
val ZCyan = Color(0xFF22E4FF)
val ZViolet = Color(0xFF8B5CF6)
val ZMagenta = Color(0xFFFF3DCB)
val ZTextHi = Color(0xFFE8F1FF)
val ZTextLo = Color(0xFF8A97B8)
val ZError = Color(0xFFFF5C7A)

@Composable
fun ZenoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ZCyan,
            secondary = ZViolet,
            tertiary = ZMagenta,
            background = ZBg,
            surface = ZBg2,
            surfaceVariant = ZCard,
            onPrimary = ZBg,
            onBackground = ZTextHi,
            onSurface = ZTextHi,
            onSurfaceVariant = ZTextLo,
        ),
        content = content,
    )
}
