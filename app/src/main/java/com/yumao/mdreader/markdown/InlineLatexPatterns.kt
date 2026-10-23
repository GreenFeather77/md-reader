package com.yumao.mdreader.markdown

import java.util.regex.Pattern

internal object InlineLatexPatterns {
    /** Single $...$ without leading/trailing spaces; closing $ must not be followed by a digit. */
    val SINGLE_DOLLAR: Pattern =
        Pattern.compile("\\$([^\\s$](?:[^$\\n]*[^\\s$])?)\\$(?![0-9])")
}
