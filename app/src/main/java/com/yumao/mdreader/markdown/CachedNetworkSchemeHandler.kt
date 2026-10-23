package com.yumao.mdreader.markdown

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Network image scheme handler with a simple disk cache stored in app cacheDir/img-cache.
 * First fetch downloads to disk; subsequent renders load from disk without network.
 */
class CachedNetworkSchemeHandler(private val context: Context) : SchemeHandler() {

    private val cacheDir = File(context.cacheDir, "img-cache").apply { mkdirs() }

    override fun supportedSchemes(): Collection<String> = listOf("http", "https")

    override fun handle(raw: String, uri: Uri): ImageItem {
        val file = cacheFile(raw)
        if (!file.exists() || file.length() == 0L) {
            runCatching { download(raw, file) }
        }
        if (file.exists() && file.length() > 0L) {
            val drawable = runCatching { Drawable.createFromPath(file.absolutePath) }.getOrNull()
            if (drawable != null) return ImageItem.withResult(drawable)
        }
        return ImageItem.withResult(android.graphics.drawable.ColorDrawable(0x00000000))
    }

    private fun download(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MDReader")
        }
        conn.inputStream.use { input ->
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.outputStream().use { input.copyTo(it) }
            if (!tmp.renameTo(target)) tmp.delete()
        }
        conn.disconnect()
    }

    private fun cacheFile(url: String): File {
        val hash = runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray())
                .take(16)
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault("img_${url.hashCode()}")
        val ext = url.substringAfterLast('.', "").let {
            if (it.length in 1..5 && it.all { c -> c.isLetterOrDigit() }) ".$it" else ".bin"
        }
        return File(cacheDir, "$hash$ext")
    }
}
