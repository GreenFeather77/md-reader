package com.yumao.mdreader

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yumao.mdreader.data.AppSettings
import com.yumao.mdreader.data.RecentFile
import com.yumao.mdreader.data.RecentFilesRepository
import com.yumao.mdreader.data.ReadingStats
import com.yumao.mdreader.data.ReadingStatsRepository
import com.yumao.mdreader.data.SettingsRepository
import com.yumao.mdreader.files.DocUiState
import com.yumao.mdreader.files.Document
import com.yumao.mdreader.files.DraftStore
import com.yumao.mdreader.files.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, READER, EDITOR, SETTINGS, COMPARE }

class MdViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app.applicationContext
    val settingsRepo = SettingsRepository(appCtx)
    val recentsRepo = RecentFilesRepository(appCtx)
    val statsRepo = ReadingStatsRepository(appCtx)
    val draftStore = DraftStore(appCtx)

    private var draftJob: Job? = null
    private val editorPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var lastOpenUri: Uri? = null
    private var lastBigDraftNotify: Long = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) { draftStore.gc() }
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val recents: StateFlow<List<RecentFile>> = recentsRepo.recents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the recents DataStore has emitted at least once (cold-start restore guard). */
    val recentsLoaded: StateFlow<Boolean> = recentsRepo.recents.map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val readingStats: StateFlow<ReadingStats> = statsRepo.stats
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingStats())

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _doc = MutableStateFlow<DocUiState>(DocUiState.Idle)
    val doc: StateFlow<DocUiState> = _doc.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun go(to: Screen) { _screen.value = to }

    fun consumeMessage() { _message.value = null }

    fun notify(text: String) { _message.value = text }

    // ---------- Reading stats ----------

    fun recordReadingTime(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { statsRepo.addReadingTime(seconds) }
    }

    fun recordDocOpened() {
        viewModelScope.launch(Dispatchers.IO) { statsRepo.addDocOpen() }
    }

    /** Loads a recent entry's title + content for compare mode, cache first for speed. */
    suspend fun loadRecentForCompare(recent: RecentFile): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            val fromCache = recent.cachePath?.let { path -> runCatching { FileIo.readCache(path) }.getOrNull() }
            val content = fromCache ?: runCatching {
                FileIo.readText(appCtx.contentResolver, Uri.parse(recent.uri))
            }.getOrNull() ?: return@withContext null
            recent.title to content
        }
    }

    // ---------- External intents ----------

    fun handleIncoming(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { openExternal(it) }
            Intent.ACTION_SEND -> {
                val stream = intent.sharedStreamUri()
                if (stream != null) {
                    openExternal(stream)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) openComposeDraft(text)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val stream = intent.sharedStreamUri()
                if (stream != null) {
                    openExternal(stream)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) openComposeDraft(text)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.sharedStreamUri(): Uri? {
        val direct = getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (direct != null) return direct
        val clip = clipData ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).uri
    }

    private fun openExternal(uri: Uri) {
        viewModelScope.launch {
            lastOpenUri = uri
            _doc.value = DocUiState.Loading
            val doc = withContext(Dispatchers.IO) { loadExternal(uri) }
            if (doc != null) {
                _doc.value = DocUiState.Ready(restoreDraft(doc))
                _screen.value = Screen.READER
            } else {
                _doc.value = DocUiState.Error("文件无法读取，可能已被移动或禁止访问")
                _screen.value = Screen.READER
            }
        }
    }

    private suspend fun loadExternal(uri: Uri): Document? {
        val resolver = appCtx.contentResolver
        var persistable = false
        try {
            appCtx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            persistable = true
        } catch (_: Throwable) {
            try {
                appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                persistable = true
            } catch (_: Throwable) {
            }
        }
        val meta = FileIo.queryMeta(resolver, uri)
        val title = FileIo.prettyTitle(
            meta.displayName.ifBlank { FileIo.fallbackName(uri) }
        )
        return try {
            val content = FileIo.readText(resolver, uri)
            val cache = FileIo.copyToCache(appCtx, uri, title)
            if (cache != null) recentsRepo.setCachePath(uri.toString(), cache)
            recentsRepo.add(uri.toString(), title, if (meta.size >= 0) meta.size else content.length.toLong(), cache)
            android.util.Log.i("MDReader", "loadExternal uri=$uri title=$title len=${content.length} persistable=$persistable")
            Document(
                uri = uri,
                title = title,
                content = content,
                writable = persistable,
                fromExternal = true,
                cachePath = cache,
                size = content.length.toLong(),
            )
        } catch (t: Throwable) {
            android.util.Log.w("MDReader", "loadExternal failed uri=$uri", t)
            val cachePath = recents.value.firstOrNull { r -> r.uri == uri.toString() }?.cachePath
            if (cachePath != null) {
                try {
                    val cached = FileIo.readCache(cachePath)
                    android.util.Log.i("MDReader", "loadExternal cache fallback len=${cached.length}")
                    return Document(uri, title, cached, writable = false, fromExternal = true, cachePath = cachePath, size = cached.length.toLong())
                } catch (t2: Throwable) {
                    android.util.Log.w("MDReader", "cache fallback failed", t2)
                }
            }
            null
        }
    }

    private fun openComposeDraft(text: String) {
        _doc.value = DocUiState.Ready(
            Document(
                uri = null,
                title = "新建文档",
                content = text,
                writable = false,
                fromExternal = true,
                cachePath = null,
                size = text.length.toLong(),
            )
        )
        _screen.value = Screen.EDITOR
    }

    // ---------- SAF & home ----------

    fun openSaf(uri: Uri) {
        viewModelScope.launch {
            lastOpenUri = uri
            _doc.value = DocUiState.Loading
            val doc = withContext(Dispatchers.IO) { loadExternal(uri) }
            if (doc != null) {
                _doc.value = DocUiState.Ready(restoreDraft(doc.copy(writable = true, fromExternal = false)))
                _screen.value = Screen.READER
            } else {
                _doc.value = DocUiState.Error("打开文件失败")
                _screen.value = Screen.READER
            }
        }
    }

    fun retryLast() {
        lastOpenUri?.let { openExternal(it) } ?: run { closeDocument() }
    }

    fun reopen(recent: RecentFile) {
        openExternal(Uri.parse(recent.uri))
    }

    fun removeRecent(recent: RecentFile) {
        viewModelScope.launch {
            recentsRepo.remove(recent.uri)
            draftStore.delete(recent.uri)
            FileIo.deleteFile(recent.cachePath)
        }
    }

    fun togglePinned(recent: RecentFile) {
        viewModelScope.launch { recentsRepo.setPinned(recent.uri, !recent.pinned) }
    }

    fun hasDraft(uri: String): Boolean = runCatching {
        draftStore.load(uri)?.let { it.content.isNotBlank() } ?: false
    }.getOrDefault(false)

    fun clearRecents() {
        viewModelScope.launch {
            recentsRepo.clear()
            draftStore.clearAll()
            FileIo.deleteUnusedCache(appCtx, emptySet())
        }
    }

    fun clearDrafts() {
        viewModelScope.launch(Dispatchers.IO) { draftStore.clearAll() }
    }

    fun createUntitled() {
        viewModelScope.launch {
            val draft = withContext(Dispatchers.IO) { draftStore.load(DraftStore.UNTITLED_KEY) }
            val restored = if (settings.value.autoDraftEnabled && !draft?.content.isNullOrBlank()) {
                notify("已恢复未保存的草稿")
                editorPositions[DraftStore.UNTITLED_KEY] = draft!!.line to draft.column
                draft.content
            } else ""
            _doc.value = DocUiState.Ready(
                Document(null, "新建文档", restored, writable = false, fromExternal = false, cachePath = null, size = restored.length.toLong())
            )
            _screen.value = Screen.EDITOR
        }
    }

    fun closeDocument() {
        val d = (_doc.value as? DocUiState.Ready)?.document
        draftJob?.cancel()
        if (d != null && d.content == d.savedContent) {
            val key = d.uri?.toString() ?: DraftStore.UNTITLED_KEY
            viewModelScope.launch(Dispatchers.IO) { draftStore.delete(key) }
        }
        _doc.value = DocUiState.Idle
        _screen.value = Screen.HOME
    }

    fun discardDocument() {
        val d = (_doc.value as? DocUiState.Ready)?.document
        draftJob?.cancel()
        val key = d?.uri?.toString() ?: DraftStore.UNTITLED_KEY
        viewModelScope.launch(Dispatchers.IO) { draftStore.delete(key) }
        _doc.value = DocUiState.Idle
        _screen.value = Screen.HOME
    }

    fun saveReadingPosition(uri: Uri, anchor: Int, progress: Float) {
        android.util.Log.i("MDReader", "saveReadingPosition anchor=$anchor progress=$progress")
        viewModelScope.launch {
            // Activity finish 会取消 viewModelScope,NonCancellable 保证 DataStore 写入完成
            withContext(NonCancellable) {
                recentsRepo.updateReading(uri.toString(), anchor, progress)
            }
        }
    }

    fun rememberEditorPosition(key: String, line: Int, column: Int) {
        editorPositions[key] = line.coerceAtLeast(0) to column.coerceAtLeast(0)
    }

    fun editorPosition(key: String): Pair<Int, Int>? = editorPositions[key]

    fun scheduleDraftSave(content: String, line: Int, column: Int) {
        if (!settings.value.autoDraftEnabled) return
        val ready = _doc.value as? DocUiState.Ready ?: return
        val d = ready.document
        val key = d.uri?.toString() ?: DraftStore.UNTITLED_KEY
        draftJob?.cancel()
        draftJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1200)
            if (content == d.savedContent) draftStore.delete(key)
            else if (!draftStore.save(key, content, line, column)) notifyBigDraft()
        }
    }

    fun flushDraft(content: String, line: Int, column: Int) {
        if (!settings.value.autoDraftEnabled) return
        val ready = _doc.value as? DocUiState.Ready ?: return
        val d = ready.document
        val key = d.uri?.toString() ?: DraftStore.UNTITLED_KEY
        draftJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            if (content == d.savedContent) draftStore.delete(key)
            else if (!draftStore.save(key, content, line, column)) notifyBigDraft()
        }
    }

    private fun notifyBigDraft() {
        val now = System.currentTimeMillis()
        if (now - lastBigDraftNotify < 30_000) return
        lastBigDraftNotify = now
        notify("草稿超过 5MB 大小上限，本次未自动保存")
    }

    private suspend fun restoreDraft(doc: Document): Document {
        if (!settings.value.autoDraftEnabled) return doc
        val uri = doc.uri?.toString() ?: return doc
        val draft = withContext(Dispatchers.IO) { draftStore.load(uri) } ?: return doc
        if (draft.content.isEmpty() || draft.content == doc.content) return doc
        editorPositions[uri] = draft.line to draft.column
        notify("已恢复未保存的草稿")
        android.util.Log.i("MDReader", "restoreDraft uri=$uri draftLen=${draft.content.length}")
        return doc.copy(content = draft.content)
    }

    /** Editor content flows into the shared document so mode switching stays coherent. */
    fun syncContent(content: String) {
        val s = _doc.value as? DocUiState.Ready ?: return
        _doc.value = DocUiState.Ready(s.document.copy(content = content))
    }

    fun hasUnsaved(): Boolean {
        val s = _doc.value as? DocUiState.Ready ?: return false
        return s.document.content != s.document.savedContent
    }

    // ---------- Saving ----------

    suspend fun saveCurrent(): Boolean {
        val ready = _doc.value as? DocUiState.Ready ?: return false
        val d = ready.document
        val uri = d.uri ?: return false
        val ok = withContext(Dispatchers.IO) { FileIo.writeText(appCtx, uri, d.content) }
        if (!ok) {
            notify("无法写入原文件，请使用“另存为”")
            return false
        }
        val cur = _doc.value as? DocUiState.Ready
        if (cur != null) {
            _doc.value = DocUiState.Ready(cur.document.copy(savedContent = cur.document.content))
        }
        draftJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { draftStore.delete(uri.toString()) }
        return true
    }

    suspend fun saveAs(target: Uri): Boolean {
        val ready = _doc.value as? DocUiState.Ready ?: return false
        val d = ready.document
        val ok = withContext(Dispatchers.IO) { FileIo.writeText(appCtx, target, d.content) }
        if (!ok) { notify("保存失败"); return false }
        try {
            appCtx.contentResolver.takePersistableUriPermission(
                target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Throwable) {
        }
        val title = FileIo.prettyTitle(FileIo.queryMeta(appCtx.contentResolver, target).displayName.ifBlank { FileIo.fallbackName(target) })
        val cachePath = FileIo.copyToCache(appCtx, target, title)
        _doc.value = DocUiState.Ready(
            Document(target, title, d.content, writable = true, fromExternal = false,
                cachePath = cachePath, size = d.content.length.toLong(), savedContent = d.content)
        )
        recentsRepo.add(target.toString(), title, d.content.length.toLong(), cachePath)
        draftJob?.cancel()
        val oldKey = d.uri?.toString() ?: DraftStore.UNTITLED_KEY
        viewModelScope.launch(Dispatchers.IO) { draftStore.delete(oldKey) }
        notify("已保存")
        return true
    }
}
