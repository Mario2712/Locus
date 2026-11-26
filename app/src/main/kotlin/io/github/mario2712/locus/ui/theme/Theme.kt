package io.github.mario2712.locus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LocusGreen = Color(0xFF1F6F5C)

private val LightFallback = lightColorScheme(primary = LocusGreen)
private val DarkFallback = darkColorScheme(primary = Color(0xFF7FD8BE))

/**
 * Material-3-Theme mit Material-You-Farben (Dynamic Color) vom Pixel.
 * minSdk 31 garantiert Dynamic Color; die Fallbacks dienen Previews und Tests.
 */
@Composable
fun LocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
