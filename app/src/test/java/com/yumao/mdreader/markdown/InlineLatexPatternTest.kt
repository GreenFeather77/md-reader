package com.yumao.mdreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InlineLatexPatternTest {

    private fun group(text: String): String? {
        val m = InlineLatexPatterns.SINGLE_DOLLAR.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    @Test
    fun `matches simple single dollar math`() {
        assertEquals("a", group("see \$a\$ here"))
        assertEquals("a+b", group("\$a+b\$"))
        assertEquals("E=mc^2", group("质能方程 \$E=mc^2\$ 成立"))
    }

    @Test
    fun `rejects math with leading or trailing spaces`() {
        assertNull(group("\$ a\$"))
        assertNull(group("\$a \$"))
    }

    @Test
    fun `does not swallow currency ranges`() {
        assertNull(group("\$5 and \$6"))
        assertNull(group("\$10"))
    }

    @Test
    fun `keeps inner spaces`() {
        val m = InlineLatexPatterns.SINGLE_DOLLAR.matcher("\$a + b\$")
        assertNotNull(m.find())
        assertEquals("a + b", m.group(1))
    }
}
