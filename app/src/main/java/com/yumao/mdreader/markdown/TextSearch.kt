package com.yumao.mdreader.markdown

object TextSearch {

    const val MAX_MATCHES = 2000

    fun findMatches(text: CharSequence, query: String, ignoreCase: Boolean = true): List<IntRange> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val src = text.toString()
        val out = ArrayList<IntRange>()
        var index = src.indexOf(query, 0, ignoreCase)
        while (index >= 0 && out.size < MAX_MATCHES) {
            out.add(index until index + query.length)
            index = src.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase)
        }
        return out
    }
}
