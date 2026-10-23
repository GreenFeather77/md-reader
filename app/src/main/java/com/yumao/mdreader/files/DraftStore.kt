package com.yumao.mdreader.files

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class Draft(
    val content: String,
    val line: Int,
    val column: Int,
    val savedAt: Long,
)

class DraftStore(context: Context) {

    private val dir = File(context.filesDir, "drafts").apply { mkdirs() }

    fun save(uri: String, content: String, line: Int, column: Int): Boolean {
        if (content.length > MAX_DRAFT_CHARS) return false
        return runCatching {
            val obj = JSONObject().apply {
                put("content", content)
                put("line", line)
                put("column", column)
                put("savedAt", System.currentTimeMillis())
            }
            val f = file(uri)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(f)) return false
            true
        }.getOrDefault(false)
    }

    fun load(uri: String): Draft? = runCatching {
        val f = file(uri)
        if (!f.exists()) return@runCatching null
        val o = JSONObject(f.readText())
        Draft(
            content = o.optString("content", ""),
            line = o.optInt("line", 0),
            column = o.optInt("column", 0),
            savedAt = o.optLong("savedAt", 0L),
        )
    }.getOrNull()

    fun delete(uri: String) {
        runCatching { file(uri).delete() }
    }

    fun clearAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    fun gc(maxAgeMs: Long = 14L * 24 * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        runCatching {
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    private fun file(uri: String) = File(dir, sha8(uri) + ".json")

    private fun sha8(s: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        "draft"
    }

    companion object {
        const val UNTITLED_KEY = "untitled"
        private const val MAX_DRAFT_CHARS = 5_000_000
    }
}
