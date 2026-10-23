package com.yumao.mdreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun `finds all non overlapping matches case insensitively`() {
        val text = "Markdown markdown MARKDOWN"
        val matches = TextSearch.findMatches(text, "markdown")
        assertEquals(3, matches.size)
        assertEquals(0, matches[0].first)
        assertEquals(9, matches[1].first)
        assertEquals(18, matches[2].first)
    }

    @Test
    fun `matches do not overlap`() {
        val matches = TextSearch.findMatches("aaaa", "aa")
        assertEquals(2, matches.size)
        assertEquals(listOf(0, 2), matches.map { it.first })
    }

    @Test
    fun `case sensitive mode is respected`() {
        val matches = TextSearch.findMatches("Abc abc", "abc", ignoreCase = false)
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].first)
    }

    @Test
    fun `empty query finds nothing`() {
        assertTrue(TextSearch.findMatches("anything", "").isEmpty())
        assertTrue(TextSearch.findMatches("", "x").isEmpty())
    }

    @Test
    fun `result count is capped`() {
        val text = "x".repeat(TextSearch.MAX_MATCHES + 50)
        val matches = TextSearch.findMatches(text, "x")
        assertEquals(TextSearch.MAX_MATCHES, matches.size)
    }
}
