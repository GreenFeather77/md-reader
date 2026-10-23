package com.yumao.mdreader.markdown

import android.graphics.Color as AColor
import androidx.core.graphics.ColorUtils

/** Single place for deriving foreground/accent colors from a user-picked background. */
object DerivedReaderColors {

    private const val INK = 0xFF17181C.toInt()     // dark ink for light paper
    private const val PAPER_TEXT = 0xFFE8EAED.toInt() // light text for dark paper

    fun luminance(argb: Int): Float =
        ColorUtils.calculateLuminance(argb and 0x00FFFFFF).toFloat()

    /** Whether body text on this background should be dark ink. */
    fun usesInkText(argb: Int, lockedDarkStyle: Boolean): Boolean =
        if (lockedDarkStyle) true else luminance(argb) > 0.42f

    /** Text color for the given background honoring the lock preference. */
    fun foreground(argb: Int, lockedDarkStyle: Boolean = false): Int =
        if (usesInkText(argb, lockedDarkStyle)) INK else PAPER_TEXT

    /** WCAG-ish contrast ratio between text and background for the picker hint. */
    fun contrastRatio(text: Int, bg: Int): Float {
        val l1 = ColorUtils.calculateLuminance(text and 0x00FFFFFF)
        val l2 = ColorUtils.calculateLuminance(bg and 0x00FFFFFF)
        val la = maxOf(l1, l2).toDouble()
        val lb = minOf(l1, l2).toDouble()
        return ((la + 0.05) / (lb + 0.05)).toFloat()
    }

    /** Human readable hint shown under the text-color segmented row. */
    fun contrastInfo(bg: Int, lockedDarkStyle: Boolean): String {
        val ratio = contrastRatio(foreground(bg, lockedDarkStyle), bg)
        val label = when {
            ratio >= 7f -> "对比度 %.1f:1，非常清晰"
            ratio >= 4f -> "对比度 %.1f:1，清晰可读"
            else -> "对比度 %.1f:1，文字可能偏浅/偏深，建议换个背景色或切换文字模式"
        }
        return label.format(ratio)
    }

    /** Accent color tinted from the background hue for links/markers. */
    fun accent(bg: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(bg and 0x00FFFFFF, hsv)
        val dark = luminance(bg) < 0.42f
        hsv[1] = hsv[1].coerceAtLeast(if (dark) 0.35f else 0.40f)
        hsv[2] = if (dark) 0.62f else (hsv[2].coerceAtMost(0.46f)).coerceAtLeast(0.30f)
        return (0xFF shl 24) or (AColor.HSVToColor(hsv) and 0x00FFFFFF)
    }

    /** Overlay of body color at low alpha for hairlines/components. */
    fun mezze(argb: Int, alphaByte: Int): Int =
        (alphaByte shl 24) or (argb and 0x00FFFFFF)

    /** Code block background: a slightly shifted plane of the paper color. */
    fun codePlane(bg: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(bg and 0x00FFFFFF, hsv)
        val dark = luminance(bg) < 0.42f
        hsv[2] = if (dark) (hsv[2] - 0.06f).coerceAtLeast(0.02f) else (hsv[2] - 0.045f).coerceAtLeast(0.0f)
        return (0xFF shl 24) or (AColor.HSVToColor(hsv) and 0x00FFFFFF)
    }

    /** Inline code background: a subtle tint one step lighter/darker. */
    fun codeInlinePlane(bg: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(bg and 0x00FFFFFF, hsv)
        val dark = luminance(bg) < 0.42f
        hsv[2] = if (dark) (hsv[2] + 0.9f / 24f).coerceAtMost(0.60f) else (hsv[2] + 0.03f).coerceAtMost(0.98f)
        return (0xFF shl 24) or (AColor.HSVToColor(hsv) and 0x00FFFFFF)
    }

    /** Table header background: gentle highlight of paper. */
    fun headerPlane(bg: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(bg and 0x00FFFFFF, hsv)
        val dark = luminance(bg) < 0.42f
        hsv[2] = if (dark) (hsv[2] + 0.05f).coerceAtMost(1f) else (hsv[2] - 0.03f)
        return (0xFF shl 24) or (AColor.HSVToColor(hsv) and 0x00FFFFFF)
    }
}
