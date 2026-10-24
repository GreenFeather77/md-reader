package com.yumao.mdreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentStore: DataStore<Preferences> by preferencesDataStore(name = "md_reader_recents")

class RecentFilesRepository(private val context: Context) {

    private val keyIndex = longPreferencesKey("stamp_index")
    private val keyList = stringPreferencesKey("recent_files_json")

    val recents: Flow<List<RecentFile>> = context.recentStore.data.map { p ->
        parse(p[keyList]).sortedWith(
            compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.lastOpened }
        )
    }

    suspend fun setPinned(uri: String, pinned: Boolean) {
        context.recentStore.edit { p ->
            val list = parse(p[keyList]).toMutableList()
            val idx = list.indexOfFirst { it.uri == uri }
            if (idx >= 0) {
                list[idx] = list[idx].copy(pinned = pinned)
                p[keyList] = encode(list)
            }
        }
    }

    suspend fun add(uri: String, title: String, size: Long, cachePath: String? = null) {
        context.recentStore.edit { p ->
            val list = parse(p[keyList]).toMutableList()
            val previous = list.firstOrNull { it.uri == uri }
            list.removeAll { it.uri == uri }
            list.add(
                0,
                RecentFile(
                    uri = uri,
                    title = title,
                    size = size,
                    lastOpened = System.currentTimeMillis(),
                    cachePath = cachePath ?: previous?.cachePath,
                    anchor = previous?.anchor ?: 0,
                    progress = previous?.progress ?: 0f,
                ),
            )
            while (list.size > MAX_ENTRIES) list.removeAt(list.lastIndex)
            p[keyList] = encode(list)
            p[keyIndex] = (p[keyIndex] ?: 0L) + 1L
        }
    }

    suspend fun updateReading(uri: String, anchor: Int, progress: Float) {
        context.recentStore.edit { p ->
            val list = parse(p[keyList]).toMutableList()
            val idx = list.indexOfFirst { it.uri == uri }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    anchor = anchor.coerceAtLeast(0),
                    progress = progress.coerceIn(0f, 1f),
                )
                p[keyList] = encode(list)
            }
        }
    }

    /** Replaces the whole list; used by backup import. Imported cache paths are
     *  device-local so they are dropped to avoid stale fallbacks. */
    suspend fun replaceAll(files: List<RecentFile>) {
        context.recentStore.edit { p ->
            p[keyList] = encode(files.map { it.copy(cachePath = null) })
        }
    }

    suspend fun remove(uri: String) {
        context.recentStore.edit { p ->
            val list = parse(p[keyList]).filterNot { it.uri == uri }
            p[keyList] = encode(list)
        }
    }

    suspend fun setCachePath(uri: String, cachePath: String) {
        context.recentStore.edit { p ->
            val list = parse(p[keyList]).toMutableList()
            val idx = list.indexOfFirst { it.uri == uri }
            if (idx >= 0) {
                list[idx] = list[idx].copy(cachePath = cachePath)
                p[keyList] = encode(list)
            }
        }
    }

    suspend fun clear() {
        context.recentStore.edit { it.remove(keyList) }
    }

    private fun parse(raw: String?): List<RecentFile> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { RecentFile.fromJson(arr.getString(it)) }
    }.getOrDefault(emptyList())

    private fun encode(list: List<RecentFile>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject(it.toJson())) }
        return arr.toString()
    }

    companion object { const val MAX_ENTRIES = 50 }
}
