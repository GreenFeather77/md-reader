package com.yumao.mdreader.markdown

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Rewrites scheme-less markdown image refs to absolute URIs so the existing
 * file/content scheme handlers can load them:
 *  - `![](pic.png)`  → resolved against the document's own directory
 *  - `![](/abs.png)` → file:///abs.png
 *
 * For SAF documents the sibling URI is only addressable for path-like providers
 * (external storage); other providers are left untouched and will show the
 * loading placeholder, which is the pre-existing behavior.
 */
object RelativeImages {

    private val IMG_RE = Regex("""(!\[[^\]]*\]\()\s*([^)\s]+)\s*(\))""")

    fun resolve(content: String, base: Uri?): String {
        if (base == null || content.length > 2_000_000) return content
        return IMG_RE.replace(content) { m ->
            val raw = m.groupValues[2]
            m.groupValues[1] + rewrite(raw, base) + m.groupValues[3]
        }
    }

    private fun rewrite(raw: String, base: Uri): String {
        if (raw.contains("://") || raw.startsWith("#") || raw.startsWith("data:")) return raw
        if (raw.startsWith("/")) return "file://$raw"
        return when (base.scheme) {
            "file" -> resolveFile(raw, base)
            "content" -> resolveSaf(raw, base)
            else -> raw
        }
    }

    private fun resolveFile(raw: String, base: Uri): String = runCatching {
        val parent = File(base.path ?: return@runCatching raw).parentFile ?: return@runCatching raw
        File(parent, raw).canonicalFile.toURI().toString().removeSuffix("/")
    }.getOrDefault(raw)

    /**
     * Path-like SAF doc ids look like `primary:Download/notes/a.md`; the sibling
     * image gets the same root with the relative path appended. Non path-like
     * providers (docId is an opaque number) are skipped.
     */
    private fun resolveSaf(raw: String, base: Uri): String = runCatching {
        val docId = DocumentsContract.getDocumentId(base)
        val sep = docId.indexOf(':')
        if (sep <= 0) return@runCatching raw
        val root = docId.substring(0, sep + 1)
        val dir = docId.substring(sep + 1).substringBeforeLast('/', "")
        val joined = normalize("$dir/$raw") ?: return@runCatching raw
        DocumentsContract.buildDocumentUri(base.authority, root + joined).toString()
    }.getOrDefault(raw)

    /** Collapses `.` / `..` segments; null if it escapes above the root. */
    private fun normalize(path: String): String? {
        val out = ArrayDeque<String>()
        for (seg in path.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isEmpty()) return null else out.removeLast()
                else -> out.addLast(seg)
            }
        }
        return out.joinToString("/")
    }
}
