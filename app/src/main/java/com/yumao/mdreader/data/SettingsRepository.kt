package com.yumao.mdreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "md_reader_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeSource = stringPreferencesKey("theme_source")
        val palette = stringPreferencesKey("theme_palette")
        val themeMode = stringPreferencesKey("theme_mode")
        val readerFontScale = floatPreferencesKey("reader_font_scale")
        val readerLineHeight = floatPreferencesKey("reader_line_height")
        val codeTheme = stringPreferencesKey("code_theme")
        val latexEnabled = booleanPreferencesKey("latex_enabled")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val editorFontSize = intPreferencesKey("editor_font_size")
        val editorWordWrap = booleanPreferencesKey("editor_word_wrap")
        val editorLineNumbers = booleanPreferencesKey("editor_line_numbers")
        val autoDraftEnabled = booleanPreferencesKey("auto_draft_enabled")
        val searchHistory = stringPreferencesKey("search_history")
        val readerBgColor = intPreferencesKey("reader_bg_color")
        val readerTextLockedDark = booleanPreferencesKey("reader_text_locked_dark")
        val readerAccent = intPreferencesKey("reader_accent")
        val searchMatchCase = booleanPreferencesKey("search_match_case")
        val htmlEnabled = booleanPreferencesKey("html_enabled")
        val linkifyEnabled = booleanPreferencesKey("linkify_enabled")
        val headingScale = floatPreferencesKey("heading_scale")
        val readerMaxWidth = intPreferencesKey("reader_max_width")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeSource = p[Keys.themeSource]?.let { runCatching { ThemeSource.valueOf(it) }.getOrNull() }
                ?: ThemeSource.DYNAMIC,
            palette = ThemePalette.from(p[Keys.palette]),
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            readerFontScale = p[Keys.readerFontScale] ?: 1.0f,
            readerLineHeight = p[Keys.readerLineHeight] ?: 1.55f,
            codeTheme = p[Keys.codeTheme]?.let { runCatching { CodeThemeMode.valueOf(it) }.getOrNull() }
                ?: CodeThemeMode.FOLLOW,
            latexEnabled = p[Keys.latexEnabled] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: false,
            editorFontSize = p[Keys.editorFontSize] ?: 15,
            editorWordWrap = p[Keys.editorWordWrap] ?: true,
            editorLineNumbers = p[Keys.editorLineNumbers] ?: true,
            autoDraftEnabled = p[Keys.autoDraftEnabled] ?: true,
            readerBgColor = p[Keys.readerBgColor] ?: -1,
            readerTextLockedDark = p[Keys.readerTextLockedDark] ?: false,
            readerAccent = p[Keys.readerAccent] ?: 0,
            htmlEnabled = p[Keys.htmlEnabled] ?: true,
            linkifyEnabled = p[Keys.linkifyEnabled] ?: true,
            headingScale = (p[Keys.headingScale] ?: 1.0f).coerceIn(0.8f, 1.5f),
            readerMaxWidth = (p[Keys.readerMaxWidth] ?: 680).coerceIn(480, 800),
        )
    }

    val searchHistoryFlow: Flow<List<String>> = context.dataStore.data.map { p ->
        p[Keys.searchHistory]?.split("\n")?.filter { it.isNotBlank() }?.take(8) ?: emptyList()
    }

    val searchMatchCaseFlow: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.searchMatchCase] ?: false
    }

    suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { p ->
            val old = p[Keys.searchHistory]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            val next = (listOf(trimmed) + old.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
            p[Keys.searchHistory] = next.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() = edit { it.remove(Keys.searchHistory) }

    suspend fun setSearchMatchCase(value: Boolean) = edit { it[Keys.searchMatchCase] = value }

    suspend fun setThemeSource(value: ThemeSource) = edit { it[Keys.themeSource] = value.name }
    suspend fun setPalette(value: ThemePalette) = edit { it[Keys.palette] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setReaderFontScale(value: Float) = edit { it[Keys.readerFontScale] = value }
    suspend fun setReaderLineHeight(value: Float) = edit { it[Keys.readerLineHeight] = value }
    suspend fun setCodeTheme(value: CodeThemeMode) = edit { it[Keys.codeTheme] = value.name }
    suspend fun setLatexEnabled(value: Boolean) = edit { it[Keys.latexEnabled] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }
    suspend fun setEditorFontSize(value: Int) = edit { it[Keys.editorFontSize] = value }
    suspend fun setEditorWordWrap(value: Boolean) = edit { it[Keys.editorWordWrap] = value }
    suspend fun setEditorLineNumbers(value: Boolean) = edit { it[Keys.editorLineNumbers] = value }
    suspend fun setAutoDraftEnabled(value: Boolean) = edit { it[Keys.autoDraftEnabled] = value }
    suspend fun setReaderBgColor(value: Int) = edit { it[Keys.readerBgColor] = value }
    suspend fun setReaderTextLockedDark(value: Boolean) = edit { it[Keys.readerTextLockedDark] = value }
    suspend fun setReaderAccent(value: Int) = edit { it[Keys.readerAccent] = value }
    suspend fun setHtmlEnabled(value: Boolean) = edit { it[Keys.htmlEnabled] = value }
    suspend fun setLinkifyEnabled(value: Boolean) = edit { it[Keys.linkifyEnabled] = value }
    suspend fun setHeadingScale(value: Float) = edit { it[Keys.headingScale] = value.coerceIn(0.8f, 1.5f) }
    suspend fun setReaderMaxWidth(value: Int) = edit { it[Keys.readerMaxWidth] = value.coerceIn(480, 800) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
