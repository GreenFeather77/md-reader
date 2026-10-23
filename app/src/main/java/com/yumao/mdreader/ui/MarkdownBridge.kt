package com.yumao.mdreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.yumao.mdreader.data.AppSettings
import com.yumao.mdreader.data.CodeThemeMode
import com.yumao.mdreader.markdown.DerivedReaderColors
import com.yumao.mdreader.markdown.MarkdownPalette
import com.yumao.mdreader.markdown.MarkwonFactory
import io.noties.markwon.Markwon

@Composable
fun rememberMarkdownPalette(settings: AppSettings, dark: Boolean): MarkdownPalette {
    val cs = MaterialTheme.colorScheme
    val base = if (dark) {
        val codeMode = if (settings.codeTheme == CodeThemeMode.LIGHT) CodeLite else CodeDark
        MarkdownPalette(
            bodyText = cs.onSurface.toArgbColor(),
            link = cs.primary.toArgbColor(),
            quoteText = cs.onSurfaceVariant.toArgbColor(),
            quoteBar = cs.primary.toArgbColor(),
            ruleColor = cs.outlineVariant.toArgbColor(),
            headingRule = cs.outlineVariant.toArgbColor(),
            listMarker = cs.primary.toArgbColor(),
            codeInlineText = codeMode.first,
            codeInlineBg = codeMode.second,
            codeText = codeMode.third,
            codeBg = codeMode.fourth,
            codeKeywords = 0xFFB8C9FF.toInt(),
            codeStrings = 0xFFA6E3A5.toInt(),
            codeNumbers = 0xFFF5C26B.toInt(),
            codeComments = 0xFF8B8F98.toInt(),
            latexText = cs.onSurface.toArgbColor(),
            tableBorder = cs.outlineVariant.toArgbColor(),
            tableHeaderBg = cs.surfaceContainerHigh.toArgbColor(),
            tableRowBg = 0x00000000,
        )
    } else {
        val codeMode = if (settings.codeTheme == CodeThemeMode.DARK) CodeDark else CodeLite
        MarkdownPalette(
            bodyText = cs.onSurface.toArgbColor(),
            link = cs.primary.toArgbColor(),
            quoteText = cs.onSurfaceVariant.toArgbColor(),
            quoteBar = cs.primary.toArgbColor(),
            ruleColor = cs.outlineVariant.toArgbColor(),
            headingRule = cs.outlineVariant.toArgbColor(),
            listMarker = cs.primary.toArgbColor(),
            codeInlineText = codeMode.first,
            codeInlineBg = codeMode.second,
            codeText = codeMode.third,
            codeBg = codeMode.fourth,
            codeKeywords = 0xFF9334E6.toInt(),
            codeStrings = 0xFF146C2E.toInt(),
            codeNumbers = 0xFFB26500.toInt(),
            codeComments = 0xFF757A80.toInt(),
            latexText = cs.onSurface.toArgbColor(),
            tableBorder = cs.outlineVariant.toArgbColor(),
            tableHeaderBg = cs.surfaceContainerHigh.toArgbColor(),
            tableRowBg = 0x00000000,
        )
    }
    val custom = settings.readerBgColor
    if (custom == -1) return base

    val bg = custom and 0x00FFFFFF
    val body = DerivedReaderColors.foreground(custom, settings.readerTextLockedDark)
    val accent = DerivedReaderColors.accent(custom)
    val darkBg = DerivedReaderColors.luminance(bg) < 0.42f
    val codeProfile = if (darkBg) CodeDark else CodeLite
    val codeKeywords = if (darkBg) 0xFFB8C9FF.toInt() else 0xFF9334E6.toInt()
    val codeStrings = if (darkBg) 0xFFA6E3A5.toInt() else 0xFF146C2E.toInt()
    val codeNumbers = if (darkBg) 0xFFF5C26B.toInt() else 0xFFB26500.toInt()
    val codeComments = DerivedReaderColors.mezze(body, 0x66)
    return base.copy(
        bodyText = body,
        link = accent,
        quoteText = DerivedReaderColors.mezze(body, 0xB4),
        quoteBar = accent,
        ruleColor = DerivedReaderColors.mezze(body, 0x42),
        headingRule = DerivedReaderColors.mezze(body, 0x42),
        listMarker = accent,
        codeInlineText = codeProfile.first,
        codeInlineBg = DerivedReaderColors.codeInlinePlane(bg),
        codeText = codeProfile.third,
        codeBg = DerivedReaderColors.codePlane(bg),
        codeKeywords = codeKeywords,
        codeStrings = codeStrings,
        codeNumbers = codeNumbers,
        codeComments = codeComments,
        latexText = body,
        tableBorder = DerivedReaderColors.mezze(body, 0x66),
        tableHeaderBg = DerivedReaderColors.headerPlane(bg),
        tableRowBg = 0x00000000,
    )
}

private data class CodeProfile(val first: Int, val second: Int, val third: Int, val fourth: Int)

private val CodeLite = CodeProfile(0xFF202124.toInt(), 0xFFEDF0F3.toInt(), 0xFF202124.toInt(), 0xFFF1F3F4.toInt())
private val CodeDark = CodeProfile(0xFFE6EAEE.toInt(), 0xFF23262B.toInt(), 0xFFE6EAEE.toInt(), 0xFF15181C.toInt())

private fun Color.toArgbColor(): Int =
    (255 shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()

@Composable
fun rememberMarkwon(settings: AppSettings, dark: Boolean): Markwon {
    val context = LocalContext.current
    val palette = rememberMarkdownPalette(settings, dark)
    val density = LocalDensity.current
    val fontPx = with(density) { (16f * settings.readerFontScale).sp.toPx() }.toInt()
    return remember(
        settings.latexEnabled,
        settings.readerFontScale,
        settings.readerLineHeight,
        settings.codeTheme,
        settings.readerBgColor,
        settings.readerTextLockedDark,
        dark,
        fontPx,
    ) {
        MarkwonFactory.build(context, settings, palette, fontPx)
    }
}
