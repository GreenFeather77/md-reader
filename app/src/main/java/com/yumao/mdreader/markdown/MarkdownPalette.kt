package com.yumao.mdreader.markdown

data class MarkdownPalette(
    val bodyText: Int,
    val link: Int,
    val quoteText: Int,
    val quoteBar: Int,
    val ruleColor: Int,
    val headingRule: Int,
    val listMarker: Int,
    val codeInlineText: Int,
    val codeInlineBg: Int,
    val codeText: Int,
    val codeBg: Int,
    val codeKeywords: Int,
    val codeStrings: Int,
    val codeNumbers: Int,
    val codeComments: Int,
    val latexText: Int,
    val tableBorder: Int,
    val tableHeaderBg: Int,
    val tableRowBg: Int,
)
