package com.yumao.mdreader.files

import android.net.Uri

data class Document(
    /** Original source uri; null for a brand-new untitled document. */
    val uri: Uri?,
    val title: String,
    val content: String,
    /** Whether the source uri is believed to be writable (SAF persist or file we created). */
    val writable: Boolean,
    /** True when loaded from an external temporary grant (QQ etc.) and we hold a cached copy. */
    val fromExternal: Boolean,
    val cachePath: String?,
    val size: Long,
    /** Content as last written to (or read from) the backing file; baseline for dirty checks. */
    val savedContent: String = content,
) {
    val isUntitled: Boolean get() = uri == null
}

sealed interface DocUiState {
    data object Idle : DocUiState
    data object Loading : DocUiState
    data class Ready(val document: Document) : DocUiState
    data class Error(val message: String) : DocUiState
}
