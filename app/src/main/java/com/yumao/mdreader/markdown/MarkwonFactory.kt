package com.yumao.mdreader.markdown

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.yumao.mdreader.data.AppSettings
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.movement.MovementMethodPlugin

object MarkwonFactory {

    fun build(context: Context, settings: AppSettings, palette: MarkdownPalette, fontPx: Int): Markwon {
        val density = context.resources.displayMetrics.density
        val codeFontPx = (fontPx * 0.86f).toInt().coerceAtLeast(9)
        val latexPx = fontPx * 1.02f

        return Markwon.builderNoCore(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .apply {
                if (settings.latexEnabled) {
                    usePlugin(JLatexMathPlugin.create(latexPx) { b ->
                        b.inlinesEnabled(true).blocksEnabled(true)
                        b.theme()
                            .textColor(palette.latexText)
                            .blockPadding(
                                JLatexMathTheme.Padding(
                                    (6 * density).toInt(), (12 * density).toInt(),
                                    (6 * density).toInt(), (12 * density).toInt()
                                )
                            )
                    })
                    usePlugin(InlineLatexPlugin(latexPx, palette.latexText))
                }
            }
            .usePlugin(TablePlugin.create { theme ->
                theme.tableBorderColor(palette.tableBorder)
                    .tableBorderWidth((1 * density).toInt())
                    .tableCellPadding((10 * density).toInt())
                    .tableHeaderRowBackgroundColor(palette.tableHeaderBg)
                    .tableOddRowBackgroundColor(palette.tableRowBg)
                    .tableEvenRowBackgroundColor(palette.tableRowBg)
            })
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(ImagesPlugin.create { images ->
                images.addSchemeHandler(CachedNetworkSchemeHandler(context))
                images.addSchemeHandler(FileSchemeHandler.create())
                images.addSchemeHandler(ContentSchemeHandler(context))
            })
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(MovementMethodPlugin.link())
            .usePlugin(CodeHighlightPlugin(palette))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(b: MarkwonTheme.Builder) {
                    b.linkColor(palette.link)
                        .isLinkUnderlined(false)
                        .codeTextColor(palette.codeInlineText)
                        .codeBackgroundColor(palette.codeInlineBg)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockTextColor(palette.codeText)
                        .codeBlockBackgroundColor(palette.codeBg)
                        .codeBlockTypeface(Typeface.MONOSPACE)
                        .codeBlockTextSize(codeFontPx)
                        .codeTextSize(codeFontPx)
                        .codeBlockMargin((12 * density).toInt())
                        .blockQuoteColor(palette.quoteBar)
                        .blockQuoteWidth((3 * density).toInt())
                        .listItemColor(palette.listMarker)
                        .headingBreakColor(0x00000000)
                        .headingBreakHeight(0)
                        .headingTextSizeMultipliers(
                            floatArrayOf(1.75f, 1.45f, 1.25f, 1.10f, 1.0f, 0.95f)
                        )
                        .thematicBreakColor(palette.ruleColor)
                        .thematicBreakHeight((1 * density).toInt().coerceAtLeast(1))
                        .bulletWidth((5 * density).toInt())
                        .bulletListItemStrokeWidth((2 * density).toInt())
                }
            })
            .build()
    }
}

/**
 * Lightweight generic code-block token coloring applied after rendering.
 * Skims keywords / strings / numbers / comments inside CodeBlockSpan regions
 * with per-theme colors. No external grammar dependency.
 */
class CodeHighlightPlugin(private val palette: MarkdownPalette) : AbstractMarkwonPlugin() {

    override fun afterSetText(textView: TextView) {
        val text = textView.text as? Spannable ?: return
        val length = text.length
        for (bs in text.getSpans(0, length, CodeBlockSpan::class.java)) {
            val s = text.getSpanStart(bs)
            val e = text.getSpanEnd(bs)
            if (e > s) highlightRegion(text, s, e)
        }
    }

    private fun highlightRegion(text: Spannable, start: Int, end: Int) {
        var i = start
        while (i < end) {
            var nl = text.toString().indexOf('\n', i.coerceIn(0, text.length))
            if (nl < 0 || nl > end) nl = end
            highlightLine(text, i, nl)
            i = nl + 1
        }
    }

    private fun highlightLine(text: Spannable, start: Int, end: Int) {
        if (end <= start || start < 0 || end > text.length) return
        val line = text.toString().substring(start, end)
        val t = line.trimStart()
        if (t.isEmpty()) return
        val indent = line.length - t.length
        val cs = start + indent

        if (t.startsWith("//") || t.startsWith("--")) {
            paint(text, cs, end, palette.codeComments)
            return
        }

        STR_RE.findAll(line).forEach { paint(text, start + it.range.first, start + it.range.last + 1, palette.codeStrings) }
        CMT_RE.findAll(line).forEach { paint(text, start + it.range.first, start + it.range.last + 1, palette.codeComments) }
        NUM_RE.findAll(line).forEach { paint(text, start + it.range.first, start + it.range.last + 1, palette.codeNumbers) }
        KEY_RE.findAll(line).forEach { paint(text, start + it.range.first, start + it.range.last + 1, palette.codeKeywords) }
    }

    private fun paint(text: Spannable, a: Int, b: Int, color: Int) {
        if (b > a) text.setSpan(ForegroundColorSpan(color), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    companion object {
        private val STR_RE = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`")
        private val CMT_RE = Regex("(?:(?<=\\s)|^)(?://.*|#.*|--.*|/\\*.*\\*/)")
        private val NUM_RE = Regex("(?<![\\w])[0-9][0-9_]*(?:\\.[0-9]+)?(?:[fFL]|e-?[0-9]+)?(?![\\w])")
        private val KEY_RE = Regex(
            "(?<![\\w.])(?:def|func|function|import|package|class|struct|interface|extends|implements|async|await|yield|return|if|else|elif|for|while|case|switch|try|except|catch|finally|throw|throws|let|const|val|var|new|static|public|private|protected|abstract|final|void|int|float|double|bool|long|short|byte|char|this|self|super|null|nil|true|false|select|where|join|order|group|limit|fn|pub|impl|trait|echo|print|println|console|local|global|end|do|then|begin)(?![\\w.])",
            RegexOption.IGNORE_CASE
        )
    }
}
