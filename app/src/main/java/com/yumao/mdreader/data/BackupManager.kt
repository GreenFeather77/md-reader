package com.yumao.mdreader.data

import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup of settings + recent files. Restore applies every settings key
 * and replaces the recents list; content:// URIs from another device may lack
 * persisted grants and will simply fail to reopen (with cache fallback gone).
 */
object BackupManager {

    private const val VERSION = 1

    data class BackupPayload(
        val settings: AppSettings,
        val recents: List<RecentFile>,
    )

    suspend fun export(settingsRepo: SettingsRepository, recentsRepo: RecentFilesRepository): String {
        val s = settingsRepo.settings.first()
        val recents = recentsRepo.recents.first()
        return JSONObject().apply {
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("themeSource", s.themeSource.name)
                put("palette", s.palette.name)
                put("themeMode", s.themeMode.name)
                put("readerFontScale", s.readerFontScale.toDouble())
                put("readerLineHeight", s.readerLineHeight.toDouble())
                put("codeTheme", s.codeTheme.name)
                put("latexEnabled", s.latexEnabled)
                put("keepScreenOn", s.keepScreenOn)
                put("editorFontSize", s.editorFontSize)
                put("editorWordWrap", s.editorWordWrap)
                put("editorLineNumbers", s.editorLineNumbers)
                put("autoDraftEnabled", s.autoDraftEnabled)
                put("readerBgColor", s.readerBgColor)
                put("readerTextLockedDark", s.readerTextLockedDark)
                put("readerAccent", s.readerAccent)
                put("htmlEnabled", s.htmlEnabled)
                put("linkifyEnabled", s.linkifyEnabled)
                put("headingScale", s.headingScale.toDouble())
                put("readerMaxWidth", s.readerMaxWidth)
            })
            put("recents", JSONArray().apply { recents.forEach { put(JSONObject(it.toJson())) } })
        }.toString(2)
    }

    fun parsePayload(json: String): BackupPayload? = runCatching {
        val root = JSONObject(json)
        if (root.optInt("version", 0) !in 1..VERSION) return null
        val o = root.optJSONObject("settings") ?: return null
        val settings = AppSettings(
            themeSource = enumOr(o, "themeSource", ThemeSource.DYNAMIC),
            palette = ThemePalette.from(o.optString("palette")),
            themeMode = enumOr(o, "themeMode", ThemeMode.SYSTEM),
            readerFontScale = o.optDouble("readerFontScale", 1.0).toFloat(),
            readerLineHeight = o.optDouble("readerLineHeight", 1.55).toFloat(),
            codeTheme = enumOr(o, "codeTheme", CodeThemeMode.FOLLOW),
            latexEnabled = o.optBoolean("latexEnabled", true),
            keepScreenOn = o.optBoolean("keepScreenOn", false),
            editorFontSize = o.optInt("editorFontSize", 15),
            editorWordWrap = o.optBoolean("editorWordWrap", true),
            editorLineNumbers = o.optBoolean("editorLineNumbers", true),
            autoDraftEnabled = o.optBoolean("autoDraftEnabled", true),
            readerBgColor = o.optInt("readerBgColor", -1),
            readerTextLockedDark = o.optBoolean("readerTextLockedDark", false),
            readerAccent = o.optInt("readerAccent", 0),
            htmlEnabled = o.optBoolean("htmlEnabled", true),
            linkifyEnabled = o.optBoolean("linkifyEnabled", true),
            headingScale = o.optDouble("headingScale", 1.0).toFloat(),
            readerMaxWidth = o.optInt("readerMaxWidth", 680),
        )
        val arr = root.optJSONArray("recents") ?: JSONArray()
        val recents = buildList {
            for (i in 0 until arr.length()) {
                RecentFile.fromJson(arr.optJSONObject(i)?.toString() ?: continue)?.let { add(it) }
            }
        }
        BackupPayload(settings, recents)
    }.getOrNull()

    suspend fun apply(settingsRepo: SettingsRepository, recentsRepo: RecentFilesRepository, payload: BackupPayload) {
        val s = payload.settings
        settingsRepo.setThemeSource(s.themeSource)
        settingsRepo.setPalette(s.palette)
        settingsRepo.setThemeMode(s.themeMode)
        settingsRepo.setReaderFontScale(s.readerFontScale)
        settingsRepo.setReaderLineHeight(s.readerLineHeight)
        settingsRepo.setCodeTheme(s.codeTheme)
        settingsRepo.setLatexEnabled(s.latexEnabled)
        settingsRepo.setKeepScreenOn(s.keepScreenOn)
        settingsRepo.setEditorFontSize(s.editorFontSize)
        settingsRepo.setEditorWordWrap(s.editorWordWrap)
        settingsRepo.setEditorLineNumbers(s.editorLineNumbers)
        settingsRepo.setAutoDraftEnabled(s.autoDraftEnabled)
        settingsRepo.setReaderBgColor(s.readerBgColor)
        settingsRepo.setReaderTextLockedDark(s.readerTextLockedDark)
        settingsRepo.setReaderAccent(s.readerAccent)
        settingsRepo.setHtmlEnabled(s.htmlEnabled)
        settingsRepo.setLinkifyEnabled(s.linkifyEnabled)
        settingsRepo.setHeadingScale(s.headingScale)
        settingsRepo.setReaderMaxWidth(s.readerMaxWidth)
        recentsRepo.replaceAll(payload.recents)
    }

    private inline fun <reified T : Enum<T>> enumOr(o: JSONObject, key: String, def: T): T {
        val name = o.optString(key)
        if (name.isEmpty()) return def
        return try {
            enumValueOf<T>(name)
        } catch (_: Throwable) {
            def
        }
    }
}
