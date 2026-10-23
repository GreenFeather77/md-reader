package com.yumao.mdreader.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class FileMeta(val displayName: String, val size: Long)

object FileIo {

    private const val MAX_READ_BYTES = 8L * 1024 * 1024

    /** Reads a uri fully as UTF-8 text. Throws on failure. */
    fun readText(resolver: ContentResolver, uri: Uri): String {
        resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_READ_BYTES) throw IOException("文件超过 8MB，暂不支持打开过大的文档")
            return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        } ?: throw IOException("无法读取该文件")
    }

    /**
     * Writes text back to a uri. Returns true on success.
     *
     * SAF does not support atomic replace on document URIs, so we write to a
     * local temp file first (atomic tmp + rename), then copy to the destination.
     * If the copy to the URI is interrupted the original file may be truncated,
     * but a complete, valid copy always exists in [context.cacheDir] for recovery.
     */
    fun writeText(context: Context, uri: Uri, content: String): Boolean {
        return try {
            val tmp = File(context.cacheDir, "pending-${System.nanoTime()}.md.tmp")
            val staging = File(context.cacheDir, "pending-${System.nanoTime()}.md")
            try {
                tmp.writeText(content, Charsets.UTF_8)
                tmp.renameTo(staging)
                resolver(context).openOutputStream(uri, "w")?.use { out ->
                    staging.inputStream().use { input ->
                        input.copyTo(out)
                        out.flush()
                    }
                } ?: return false
                true
            } finally {
                tmp.delete()
                staging.delete()
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun queryMeta(resolver: ContentResolver, uri: Uri): FileMeta {
        var name = ""
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0) ?: ""
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
        } catch (_: Throwable) {
        }
        return FileMeta(name, size)
    }

    fun fallbackName(uri: Uri): String = uri.lastPathSegment ?: "未命名文档"

    fun prettyTitle(name: String): String {
        val base = name.substringAfterLast('/')
        return base.substringBeforeLast('.', name)
    }

    /** Copies the source uri into app-private cache so recents can reopen offline. Atomic: tmp + rename. */
    fun copyToCache(context: Context, uri: Uri, title: String): String? = try {
        val dir = File(context.cacheDir, "recent-copies").apply { mkdirs() }
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "doc" }
        val target = File(dir, "${sha8(uri.toString())}_${safeTitle}.md")
        val existing = if (target.exists()) target.length() else -1L
        val declared = queryMeta(context.contentResolver, uri).size
        if (existing >= 0 && declared >= 0 && existing == declared) {
            target.absolutePath
        } else {
            val tmp = File(dir, target.name + ".tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.delete()
                java.io.FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: throw IOException("no stream")
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("cache rename failed")
            }
            target.absolutePath
        }
    } catch (t: Throwable) {
        null
    }

    fun readCache(path: String): String = try {
        File(path).readText(Charsets.UTF_8).removePrefix("\uFEFF")
    } catch (t: Throwable) {
        throw IOException("缓存副本读取失败")
    }

    fun deleteUnusedCache(context: Context, keep: Set<String>) {
        val dir = File(context.cacheDir, "recent-copies")
        dir.listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun sha8(s: String): String = try {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        d.take(4).joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        "cache"
    }

    private fun resolver(context: Context): ContentResolver = context.contentResolver
}
