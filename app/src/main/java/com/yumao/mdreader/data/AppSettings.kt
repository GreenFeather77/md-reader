package com.yumao.mdreader.data

enum class ThemeSource { DYNAMIC, PALETTE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CodeThemeMode { FOLLOW, LIGHT, DARK }

enum class ThemePalette(val label: String, val seed: Long) {
    BLUE("山蓝", 0xFF0B57D0),
    GREEN("苍绿", 0xFF1E8E3E),
    ORANGE("暖橙", 0xFFEA8600),
    PURPLE("紫晶", 0xFF8430CB),
    RED("绯红", 0xFFC5221F);

    companion object {
        fun from(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: BLUE
    }
}

data class AppSettings(
    val themeSource: ThemeSource = ThemeSource.DYNAMIC,
    val palette: ThemePalette = ThemePalette.BLUE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val readerFontScale: Float = 1.0f,
    val readerLineHeight: Float = 1.55f,
    val codeTheme: CodeThemeMode = CodeThemeMode.FOLLOW,
    val latexEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val editorFontSize: Int = 15,
    val editorWordWrap: Boolean = true,
    val editorLineNumbers: Boolean = true,
    val autoDraftEnabled: Boolean = true,
    /** Reader content background override; -1 = follow app theme. */
    val readerBgColor: Int = -1,
    /** Foreground deriving for custom bg color: false = auto contrast, true = force dark text style. */
    val readerTextLockedDark: Boolean = false,
)
