package com.yumao.mdreader.editor

import io.github.rosemoe.sora.widget.CodeEditor

object EditorActions {

    /** Wraps the current selection (or collapses at caret) with open & close tokens. */
    fun wrap(editor: CodeEditor, open: String, close: String) {
        val cursor = editor.cursor
        val l = cursor.leftLine
        val lc = cursor.leftColumn
        val r = cursor.rightLine
        val rc = cursor.rightColumn
        val a = editor.text.getCharIndex(l, lc)
        val b = editor.text.getCharIndex(r, rc)
        val sel = editor.text.subSequence(a, b).toString()
        val inserted = open + sel + close
        editor.text.replace(l, lc, r, rc, inserted)
        editor.setSelection(l, lc + inserted.length)
    }

    /** Toggles line prefixes (heading / list markers). */
    fun toggleLinePrefix(editor: CodeEditor, prefix: String) {
        val cursor = editor.cursor
        val first = minOf(cursor.leftLine, cursor.rightLine)
        val last = maxOf(cursor.leftLine, cursor.rightLine)
        for (line in last downTo first) {
            val text = editor.text.getLine(line).toString()
            if (text.startsWith(prefix)) {
                editor.text.delete(line, 0, line, prefix.length)
            } else {
                editor.text.insert(line, 0, prefix)
            }
        }
        editor.setSelection(last, 0)
    }

    /** Inserts a block at the cursor, leaving the caret at the given inner offset. */
    fun insertBlock(editor: CodeEditor, body: String, caretWithin: Int) {
        val cursor = editor.cursor
        val line = cursor.rightLine
        val col = cursor.rightColumn
        editor.text.insert(line, col, body)
        val head = body.substring(0, caretWithin)
        val nlBefore = head.count { it == '\n' }
        val targetLine = line + nlBefore
        val targetCol = if (nlBefore == 0) col + head.length else head.substringAfterLast('\n').length
        editor.setSelection(targetLine, targetCol)
    }
}
