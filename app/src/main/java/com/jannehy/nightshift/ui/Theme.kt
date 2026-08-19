package com.jannehy.nightshift.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jannehy.nightshift.core.Accents

fun colorFromHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor("#" + hex.removePrefix("#")))
}.getOrElse { Color(0xFFFFB03A) }

/**
 * Material 3 with the chosen accent as primary. The brand orange ships in two
 * tones, exactly like the web UI: a colour bright enough for a dark background
 * is too pale on a light one.
 */
@Composable
fun NightshiftTheme(accentHex: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val effective = if (!dark && accentHex.equals(Accents.DEFAULT_HEX, ignoreCase = true)) {
        Accents.DEFAULT_LIGHT_HEX
    } else {
        accentHex
    }
    val accent = colorFromHex(effective)
    val scheme = if (dark) {
        darkColorScheme(primary = accent, secondary = accent, tertiary = accent)
    } else {
        lightColorScheme(primary = accent, secondary = accent, tertiary = accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
