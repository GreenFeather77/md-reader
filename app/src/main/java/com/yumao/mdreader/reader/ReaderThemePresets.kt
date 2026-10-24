package com.yumao.mdreader.reader

/**
 * One-tap reading themes with hand-tuned bg/accent pairings.
 * Deriving accent from the bg hue turns the whole page monochrome, so each
 * preset ships its own accent (0 = follow theme / auto-derive).
 */
data class ReaderThemePreset(
    val label: String,
    /** -1 = follow app theme. */
    val bgArgb: Int,
    /** 0 = auto-derive from bg. */
    val accentArgb: Int,
)

val ReaderThemePresets = listOf(
    ReaderThemePreset("纸白", -1, 0),
    ReaderThemePreset("护眼绿", 0xFFC7E6C2.toInt(), 0xFF1F6E43.toInt()),
    ReaderThemePreset("羊皮纸", 0xFFF4E8CE.toInt(), 0xFF955F28.toInt()),
    ReaderThemePreset("暗黑极简", 0xFF0E0F12.toInt(), 0xFF7DA9FF.toInt()),
    ReaderThemePreset("墨蓝夜", 0xFF12202E.toInt(), 0xFF5BC8E8.toInt()),
)
