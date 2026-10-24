package com.yumao.mdreader.markdown

/**
 * Recovers a tapped table's markdown source. Rendered TableSpans appear in
 * document order, so the Nth span maps to the Nth pipe-table block found by a
 * line scan of the source markdown (commonmark 0.13 has no source spans).
 */
object TableSource {

    /** Returns the raw markdown block (including delimiter row) of the index-th table, or null. */
    fun markdownAt(source: String, index: Int): String? = blocks(source).getOrNull(index)

    /** Converts the index-th table block to CSV. */
    fun csvAt(source: String, index: Int): String? {
        val block = markdownAt(source, index) ?: return null
        val rows = block.lineSequence()
            .map(::splitRow)
            .filter { cells -> cells.any { it.isNotBlank() } }
            .filterNot { cells -> cells.all { SEP_RE.matches(it.trim()) } }
            .toList()
        if (rows.isEmpty()) return null
        val width = rows.maxOf { it.size }
        return rows.joinToString("\n") { row ->
            (0 until width).joinToString(",") { i -> escape(row.getOrElse(i) { "" }) }
        }
    }

    private val SEP_RE = Regex(":?-{3,}:?")

    /** Contiguous line ranges that look like pipe tables: a header line followed by a delimiter row. */
    private fun blocks(source: String): List<String> {
        val lines = source.lines()
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size - 1) {
            val line = lines[i].trim()
            // fenced code regions are never rendered as tables; skip them
            if (line.startsWith("```") || line.startsWith("~~~")) {
                val fence = line.take(3)
                i += 1
                while (i < lines.size && !lines[i].trim().startsWith(fence)) i++
                i += 1
                continue
            }
            if (isHeaderish(lines[i]) && isDelimiter(lines[i + 1])) {
                val start = i
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].trim().isNotEmpty()) i++
                out.add(lines.subList(start, i).joinToString("\n"))
            } else {
                i++
            }
        }
        return out
    }

    private fun isHeaderish(line: String): Boolean {
        val t = line.trim()
        return t.contains('|') && t.isNotEmpty() && !t.startsWith("#")
    }

    private fun isDelimiter(line: String): Boolean {
        val cells = splitRow(line).map { it.trim() }
        return cells.isNotEmpty() && cells.all { SEP_RE.matches(it) }
    }

    private fun splitRow(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.substring(0, t.length - 1)
        return t.split('|')
    }

    private fun escape(cell: String): String {
        val c = cell.trim()
        return if (c.contains(',') || c.contains('"') || c.contains('\n')) {
            "\"" + c.replace("\"", "\"\"") + "\""
        } else c
    }
}
