package com.yumao.mdreader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.yumao.mdreader.data.AppSettings
import com.yumao.mdreader.data.ThemeMode
import com.yumao.mdreader.data.ThemePalette
import com.yumao.mdreader.data.ThemeSource

// Neutral (not blue-tinted) so every palette seed sits on it equally well.
private val LightBg = Color(0xFFF7F8FA)
private val DarkBg = Color(0xFF111218)

private fun darken(c: Color, fraction: Float): Color = lerp(c, Color.Black, fraction)
private fun lighten(c: Color, fraction: Float): Color = lerp(c, Color.White, fraction)

/** Seed with its hue rotated, for a related-but-distinct tertiary. */
private fun hueRotate(c: Color, degrees: Float): Color {
    val argb = (0xFF shl 24) or
        ((c.red * 255).toInt() shl 16) or
        ((c.green * 255).toInt() shl 8) or
        (c.blue * 255).toInt()
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun luminanceOf(c: Color): Double {
    fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/** Black text on light seeds (orange/red), white on dark ones (blue/green). */
private fun onColorFor(bg: Color): Color =
    if (luminanceOf(bg) > 0.35) Color(0xFF14171A) else Color.White

@Composable
fun isAppDark(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * All roles derive from the seed hue — no fixed navy text or green tertiary
 * left over from the original blue-only palette.
 */
fun staticColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    val seed = Color(palette.seed)
    val bg = if (dark) DarkBg else LightBg
    val container = lerp(bg, seed, 0.14f)
    return if (dark) {
        darkColorScheme(
            background = bg,
            surface = bg,
            surfaceContainerLowest = Color(0xFF0C0D11),
            surfaceContainerLow = lerp(bg, Color.Black, 0.10f),
            surfaceContainer = lerp(bg, Color.Black, 0.05f),
            surfaceContainerHigh = lerp(bg, Color.White, 0.03f),
            surfaceContainerHighest = lerp(bg, Color.White, 0.07f),
            primary = lighten(seed, 0.18f),
            onPrimary = Color(0xFF0A0A0C),
            primaryContainer = container,
            onPrimaryContainer = lighten(seed, 0.62f),
            secondary = lighten(seed, 0.55f),
            secondaryContainer = lerp(bg, seed, 0.22f),
            onSecondaryContainer = lighten(seed, 0.68f),
            tertiary = lighten(hueRotate(seed, 55f), 0.25f),
        )
    } else {
        lightColorScheme(
            background = bg,
            surface = bg,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(bg, Color.White, 0.62f),
            surfaceContainer = lerp(bg, Color.White, 0.32f),
            surfaceContainerHigh = lerp(bg, Color.White, 0.14f),
            surfaceContainerHighest = lerp(bg, Color.White, 0.05f),
            primary = seed,
            onPrimary = onColorFor(seed),
            primaryContainer = container,
            onPrimaryContainer = darken(seed, 0.52f),
            secondary = lerp(seed, Color(0xFF3C4043), 0.74f),
            secondaryContainer = lerp(bg, seed, 0.18f),
            onSecondaryContainer = darken(seed, 0.52f),
            tertiary = darken(hueRotate(seed, 55f), 0.18f),
        )
    }
}

@Composable
fun MdReaderTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppDark(settings)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        settings.themeSource == ThemeSource.DYNAMIC && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> staticColorScheme(settings.palette, dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
