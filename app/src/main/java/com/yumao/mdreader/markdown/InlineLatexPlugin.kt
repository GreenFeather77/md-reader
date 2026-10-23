package com.yumao.mdreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import android.util.LruCache
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.inlineparser.InlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Renders single $...$ inline math with baseline-correct alignment.
 *
 * Markwon's stock inline span vertically centers the formula on the text baseline
 * (ascent = -h/2, descent = h/2), which detaches formulas from surrounding text.
 * Here the TeX icon depth is used instead: ascent = -(height - depth), descent = depth.
 * Inline $$...$$ keeps being served by JLatexMathPlugin.
 */
class InlineLatexPlugin(
    private val textPx: Float,
    private val color: Int,
) : AbstractMarkwonPlugin() {

    override fun configure(registry: MarkwonPlugin.Registry) {
        registry.require(MarkwonInlineParserPlugin::class.java)
            .factoryBuilder()
            .addInlineProcessor(SingleDollar())
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(InlineMathNode::class.java) { visitor, node ->
            val start = visitor.builder().length
            visitor.builder().append(PLACEHOLDER)
            visitor.builder().setSpan(InlineMathSpan(node.latex, textPx, color), start, start + 1)
        }
    }

    private class InlineMathNode : CustomNode() {
        var latex: String = ""
    }

    private class SingleDollar : InlineProcessor() {
        override fun specialCharacter(): Char = '$'

        override fun parse(): Node? {
            val matched = match(InlineLatexPatterns.SINGLE_DOLLAR) ?: return null
            val content = matched.substring(1, matched.length - 1)
            if (content.isBlank()) return null
            return InlineMathNode().apply { latex = content }
        }
    }

    private class InlineMathSpan(
        private val latex: String,
        private val textPx: Float,
        private val color: Int,
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            val entry = MathCache.get(latex, textPx, color)
            if (entry == null) {
                if (fm != null) {
                    val metrics = paint.fontMetricsInt
                    fm.ascent = metrics.ascent
                    fm.descent = metrics.descent
                    fm.top = metrics.top
                    fm.bottom = metrics.bottom
                }
                return paint.measureText(latex).toInt()
            }
            if (fm != null) {
                fm.ascent = -(entry.height - entry.depth)
                fm.descent = entry.depth
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
            return entry.width
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val entry = MathCache.get(latex, textPx, color)
            if (entry == null) {
                canvas.drawText(latex, x, y.toFloat(), paint)
                return
            }
            val left = x.toInt()
            val drawTop = y + entry.depth - entry.height
            entry.drawable.setBounds(0, 0, entry.width, entry.height)
            val save = canvas.save()
            canvas.translate(left.toFloat(), drawTop.toFloat())
            entry.drawable.draw(canvas)
            canvas.restoreToCount(save)
        }
    }

    private class MathEntry(
        val drawable: JLatexMathDrawable,
        val width: Int,
        val height: Int,
        val depth: Int,
    )

    private object MathCache {
        private val cache = object : LruCache<String, MathEntry>(64) {}

        fun get(latex: String, textPx: Float, color: Int): MathEntry? {
            val key = "$textPx|$color|$latex"
            cache.get(key)?.let { return it }
            return try {
                val drawable = JLatexMathDrawable.builder(latex)
                    .textSize(textPx)
                    .color(color)
                    .build()
                val icon = drawable.icon()
                val entry = MathEntry(
                    drawable = drawable,
                    width = drawable.intrinsicWidth,
                    height = drawable.intrinsicHeight,
                    depth = icon.iconDepth,
                )
                cache.put(key, entry)
                entry
            } catch (t: Throwable) {
                null
            }
        }
    }

    companion object {
        private const val PLACEHOLDER = '\uFFFC'
    }
}
