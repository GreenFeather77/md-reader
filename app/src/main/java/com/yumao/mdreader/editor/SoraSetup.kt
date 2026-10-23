package com.yumao.mdreader.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

object SoraSetup {

    @Volatile
    private var registered = false

    fun markdownLanguage(context: Context, dark: Boolean): io.github.rosemoe.sora.lang.Language {
        synchronized(this) {
            if (!registered) {
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(context.assets)
                )
                GrammarRegistry.getInstance().loadGrammar(
                    DefaultGrammarDefinition.withGrammarSource(
                        IGrammarSource.fromInputStream(
                            context.assets.open("textmate/markdown.tmLanguage.json"),
                            "markdown.tmLanguage.json",
                            Charsets.UTF_8
                        ),
                        "markdown",
                        "source.markdown"
                    )
                )
                registered = true
            }
            loadTheme(context, dark)
            val language: io.github.rosemoe.sora.lang.Language =
                try {
                    TextMateLanguage.create("source.markdown", false)
                } catch (t: Throwable) {
                    io.github.rosemoe.sora.lang.EmptyLanguage()
                }
            return language
        }
    }

    fun loadTheme(context: Context, dark: Boolean) {
        val file = if (dark) "tm-theme-md-dark.json" else "tm-theme-md-light.json"
        val source = IThemeSource.fromInputStream(
            context.assets.open("textmate/$file"), file, Charsets.UTF_8
        )
        val registry = ThemeRegistry.getInstance()
        val model = ThemeModel(source)
        try {
            registry.loadTheme(model)
            registry.setTheme(model)
        } catch (t: Throwable) {
            // theme already registered; keep current
        }
    }

    fun scheme(context: Context, dark: Boolean): TextMateColorScheme {
        val file = if (dark) "tm-theme-md-dark.json" else "tm-theme-md-light.json"
        val source = IThemeSource.fromInputStream(
            context.assets.open("textmate/$file"), file, Charsets.UTF_8
        )
        return TextMateColorScheme.create(source)
    }

    /** Blend editor palette into the Material theme backgrounds. */
    fun tintScheme(
        scheme: EditorColorScheme,
        dark: Boolean,
        surfaceArgb: Int,
        onSurfaceArgb: Int,
        outlineArgb: Int,
    ) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surfaceArgb)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceArgb)
        scheme.setColor(EditorColorScheme.LINE_NUMBER, outlineArgb)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, outlineArgb)
        scheme.setColor(EditorColorScheme.CURRENT_LINE, blend(surfaceArgb, dark, 0x24FFFFFF.toInt(), 0x240B57D0.toInt()))
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurfaceArgb)
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, if (dark) 0xFFB7C7F5.toInt() else 0xFF0B57D0.toInt())
    }

    private fun blend(base: Int, dark: Boolean, darkOverlay: Int, lightOverlay: Int): Int =
        if (dark) lerp(Color(base), Color(darkOverlay), 0.10f).toArgb()
        else lerp(Color(base), Color(lightOverlay), 0.10f).toArgb()
}
