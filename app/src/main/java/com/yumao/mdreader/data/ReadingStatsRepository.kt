package com.yumao.mdreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "md_reader_stats")

data class ReadingStats(
    val todaySeconds: Long = 0,
    val todayDocs: Int = 0,
    val totalSeconds: Long = 0,
    val totalDocs: Long = 0,
    /** Oldest-first seconds per day for the last 7 days. */
    val last7: List<Pair<String, Long>> = emptyList(),
)

/**
 * Daily reading-time aggregates in one JSON blob; only the last 120 days are
 * kept so the file stays tiny no matter how long the app is used.
 */
class ReadingStatsRepository(private val context: Context) {

    private val keyJson = stringPreferencesKey("reading_stats_json")
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val stats: Flow<ReadingStats> = context.statsStore.data.map { p ->
        val root = runCatching { JSONObject(p[keyJson] ?: "{}") }.getOrNull() ?: JSONObject()
        val days = root.optJSONObject("days") ?: JSONObject()
        val today = dayFmt.format(Date())
        val todayObj = days.optJSONObject(today)
        val last7 = buildList {
            for (i in 6 downTo 0) {
                val d = dayFmt.format(Date(System.currentTimeMillis() - i * 86_400_000L))
                add(d to (days.optJSONObject(d)?.optLong("s") ?: 0L))
            }
        }
        ReadingStats(
            todaySeconds = todayObj?.optLong("s") ?: 0L,
            todayDocs = todayObj?.optInt("d") ?: 0,
            totalSeconds = days.keys().asSequence().sumOf { days.optJSONObject(it)?.optLong("s") ?: 0L },
            totalDocs = root.optLong("totalDocs"),
            last7 = last7,
        )
    }

    suspend fun addReadingTime(seconds: Int) {
        if (seconds <= 0) return
        context.statsStore.edit { p ->
            val root = runCatching { JSONObject(p[keyJson] ?: "{}") }.getOrNull() ?: JSONObject()
            val days = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
            val key = dayFmt.format(Date())
            val day = days.optJSONObject(key) ?: JSONObject().also { days.put(key, it) }
            day.put("s", day.optLong("s") + seconds)
            trim(days)
            p[keyJson] = root.toString()
        }
    }

    suspend fun addDocOpen() {
        context.statsStore.edit { p ->
            val root = runCatching { JSONObject(p[keyJson] ?: "{}") }.getOrNull() ?: JSONObject()
            val days = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
            val key = dayFmt.format(Date())
            val day = days.optJSONObject(key) ?: JSONObject().also { days.put(key, it) }
            day.put("d", day.optInt("d") + 1)
            root.put("totalDocs", root.optLong("totalDocs") + 1)
            trim(days)
            p[keyJson] = root.toString()
        }
    }

    private fun trim(days: JSONObject) {
        val keys = days.keys().asSequence().toMutableList()
        if (keys.size <= 120) return
        keys.sorted().take(keys.size - 120).forEach { days.remove(it) }
    }
}
