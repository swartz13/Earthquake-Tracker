package com.berk.deprem.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    secondary = Color(0xFFE7BDB4),
    background = Color(0xFF12100F),
    surface = Color(0xFF1B1918),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    secondary = Color(0xFF775651),
    background = Color(0xFFFFFBF9),
)

/** Buyukluge gore renk: gozle hizli tarama icin. */
object MagColors {
    fun of(mag: Double): Color = when {
        mag < 2.5 -> Color(0xFF6E7B8B)
        mag < 3.5 -> Color(0xFF2E7D32)
        mag < 4.5 -> Color(0xFFE9A100)
        mag < 5.5 -> Color(0xFFE05E00)
        else -> Color(0xFFC62828)
    }
}

@Composable
fun DepremTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
