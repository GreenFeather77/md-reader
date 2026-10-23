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

private val LightBg = Color(0xFFF5F8FE)
private val DarkBg = Color(0xFF111218)

@Composable
fun isAppDark(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

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
            primary = lerp(seed, Color.White, 0.18f),
            onPrimary = Color(0xFF0A0A0C),
            primaryContainer = container,
            onPrimaryContainer = lerp(seed, Color.White, 0.65f),
            secondary = lerp(seed, Color.White, 0.55f),
            secondaryContainer = lerp(bg, seed, 0.22f),
            onSecondaryContainer = lerp(seed, Color.White, 0.72f),
            tertiary = lerp(seed, Color(0xFF8FE09A), 0.45f),
        )
    } else {
        lightColorScheme(
            background = bg,
            surface = bg,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = lerp(bg, Color.White, 0.62f),
            surfaceContainer = lerp(bg, Color.White, 0.32f),
            surfaceContainerHigh = lerp(bg, Color.White, 0.14f),
            surfaceContainerHighest = lerp(bg, Color.White, 0.05f),
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = container,
            onPrimaryContainer = Color(0xFF00203F),
            secondary = lerp(seed, Color(0xFF3C4043), 0.74f),
            secondaryContainer = lerp(bg, seed, 0.18f),
            onSecondaryContainer = Color(0xFF00203F),
            tertiary = lerp(seed, Color(0xFF1E8E3E), 0.55f),
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
