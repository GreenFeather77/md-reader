package com.yumao.mdreader.data

import org.json.JSONObject

data class RecentFile(
    val uri: String,
    val title: String,
    val size: Long,
    val lastOpened: Long,
    val cachePath: String? = null,
    val anchor: Int = 0,
    val progress: Float = 0f,
    val pinned: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("uri", uri)
        put("title", title)
        put("size", size)
        put("lastOpened", lastOpened)
        put("cachePath", cachePath ?: "")
        put("anchor", anchor)
        put("progress", progress.toDouble())
        put("pinned", pinned)
    }.toString()

    companion object {
        fun fromJson(s: String): RecentFile? = runCatching {
            val o = JSONObject(s)
            RecentFile(
                uri = o.getString("uri"),
                title = o.optString("title", "未命名"),
                size = o.optLong("size", 0L),
                lastOpened = o.optLong("lastOpened", 0L),
                cachePath = o.optString("cachePath").takeIf { it.isNotEmpty() },
                anchor = o.optInt("anchor", 0).coerceAtLeast(0),
                progress = o.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                pinned = o.optBoolean("pinned", false),
            )
        }.getOrNull()
    }
}
