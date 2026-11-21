package com.amin.vbox.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** VBOX palette, copied from the screenshots. */
object VboxColors {
    val Black = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val CardDarkGrey = Color(0xFF121212)
    val Divider = Color(0xFF2C2C2E)
    val White = Color(0xFFFFFFFF)
    val GreyText = Color(0xFF8E8E93)
    val Accent = Color(0xFF7DD8E6)      // cyan links / active nav
    val AccentDeep = Color(0xFF5AC8E3)
    val Green = Color(0xFF1DB954)       // power button (screenshot green)
    val GreenBright = Color(0xFF22C55E)
    val ToggleOn = Color(0xFF1F9D4D)
    val ToggleTrackOff = Color(0xFF3A3A3C)
    val ThumbGrey = Color(0xFFAEAEB2)
    val Red = Color(0xFFFF3B30)
    val PillBg = Color(0xFF2C2C2E)
    val PillText = Color(0xFFAEAEB2)
    val ProgressTrack = Color(0xFF3A3A3C)
}

@Composable
fun VboxTheme(pureBlack: Boolean, content: @Composable () -> Unit) {
    val bg = if (pureBlack) VboxColors.Black else VboxColors.CardDarkGrey
    androidx.compose.material3.MaterialTheme(
        colorScheme = darkColorScheme(
            background = bg,
            surface = VboxColors.Card,
            onBackground = VboxColors.White,
            onSurface = VboxColors.White,
            primary = VboxColors.Accent,
            secondary = VboxColors.AccentDeep
        ),
        content = content
    )
}
