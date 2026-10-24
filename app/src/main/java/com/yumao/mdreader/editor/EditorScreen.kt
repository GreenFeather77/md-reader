package com.yumao.mdreader.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumao.mdreader.MdViewModel
import com.yumao.mdreader.Screen
import com.yumao.mdreader.files.DocUiState
import com.yumao.mdreader.files.DraftStore
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(vm: MdViewModel) {
    val docState by vm.doc.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ready = docState as? DocUiState.Ready

    LaunchedEffect(docState) {
        if (docState is DocUiState.Idle) vm.closeDocument()
    }

    if (ready == null) {
        val err = docState as? DocUiState.Error
        if (err != null) {
            com.yumao.mdreader.ui.DocumentErrorState(
                message = err.message,
                onRetry = null,
                onBack = { vm.closeDocument() },
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val document = ready.document
    val editorKey = document.uri?.toString() ?: DraftStore.UNTITLED_KEY
    val context = LocalContext.current
    val dark = com.yumao.mdreader.ui.isAppDark(settings)
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var dirty by remember(document.uri) {
        mutableStateOf(document.content != document.savedContent)
    }
    var askLeave by remember { mutableStateOf(false) }
    var askSaveAs by remember { mutableStateOf(false) }
    var editorMenu by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var useRegex by remember { mutableStateOf(false) }
    var findCount by remember { mutableStateOf(0) }
    var findIndex by remember { mutableStateOf(-1) }
    var wordCount by remember { mutableStateOf(0) }
    var charCount by remember { mutableStateOf(0) }
    var previewMode by remember { mutableStateOf(false) }

    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                editorRef.value?.let { vm.syncContent(it.text.toString()) }
                if (vm.saveAs(uri)) vm.closeDocument()
            }
        }
    }

    fun flushToVm() {
        editorRef.value?.let { vm.syncContent(it.text.toString()) }
    }

    fun performSave() {
        scope.launch {
            flushToVm()
            val current = (vm.doc.value as? DocUiState.Ready)?.document
            when {
                current == null -> return@launch
                current.uri == null ->
                    saveAsLauncher.launch("${if (current.title.isBlank()) "新建文档" else current.title}.md")
                !current.writable -> askSaveAs = true
                else -> if (vm.saveCurrent()) {
                    dirty = false
                    vm.notify("已保存")
                }
            }
        }
    }

    fun requestLeave() {
        flushToVm()
        if (vm.hasUnsaved()) askLeave = true else vm.closeDocument()
    }

    fun closeFind() {
        findOpen = false
        findQuery = ""
        replaceQuery = ""
        findCount = 0
        findIndex = -1
        runCatching { editorRef.value?.searcher?.stopSearch() }
    }

    BackHandler { requestLeave() }
    BackHandler(enabled = findOpen) { closeFind() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val ed = editorRef.value ?: return@LifecycleEventObserver
                val text = ed.text.toString()
                val cursor = ed.cursor
                editorKey?.let { vm.rememberEditorPosition(it, cursor.leftLine, cursor.leftColumn) }
                vm.flushDraft(text, cursor.leftLine, cursor.leftColumn)
                vm.syncContent(text)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef.value?.let { ed ->
                runCatching { ed.searcher.stopSearch() }
                val key = editorKey ?: return@let
                val text = ed.text.toString()
                vm.syncContent(text)
                val cursor = ed.cursor
                vm.rememberEditorPosition(key, cursor.leftLine, cursor.leftColumn)
                vm.flushDraft(text, cursor.leftLine, cursor.leftColumn)
            }
        }
    }

    LaunchedEffect(findOpen, findQuery, ignoreCase, useRegex) {
        val ed = editorRef.value ?: return@LaunchedEffect
        if (!findOpen || findQuery.isEmpty()) {
            runCatching { ed.searcher.stopSearch() }
            findCount = 0
            findIndex = -1
            return@LaunchedEffect
        }
        delay(180)
        runCatching {
            val type = if (useRegex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            else EditorSearcher.SearchOptions.TYPE_NORMAL
            ed.searcher.search(findQuery, EditorSearcher.SearchOptions(type, ignoreCase))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                    Text(
                        document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (wordCount > 0) {
                        Text(
                            "$wordCount 字 / $charCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (dirty) {
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(8.dp)
                                .background(cs.tertiary, CircleShape)
                        )
                    }
                    IconButton(onClick = { editorRef.value?.undo() }, enabled = !previewMode) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = { editorRef.value?.redo() }, enabled = !previewMode) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "重做")
                    }
                    IconButton(onClick = {
                        if (findOpen) closeFind() else findOpen = true
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "查找替换",
                            tint = if (findOpen) cs.primary else cs.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { editorMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = editorMenu, onDismissRequest = { editorMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("撤销") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null) },
                                onClick = { editorMenu = false; editorRef.value?.undo() },
                            )
                            DropdownMenuItem(
                                text = { Text("重做") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null) },
                                onClick = { editorMenu = false; editorRef.value?.redo() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (previewMode) "返回编辑" else "预览") },
                                leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                                onClick = {
                                    editorMenu = false
                                    flushToVm()
                                    previewMode = !previewMode
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("切换到阅读") },
                                leadingIcon = { Icon(Icons.Outlined.Book, contentDescription = null) },
                                onClick = {
                                    editorMenu = false
                                    flushToVm()
                                    vm.go(Screen.READER)
                                },
                            )
                        }
                    }
                    IconButton(onClick = { performSave() }) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = "保存",
                            tint = if (dirty) cs.primary else cs.onSurface,
                        )
                    }
                }
            }

            if (findOpen) {
                EditorFindPanel(
                    query = findQuery,
                    onQueryChange = { findQuery = it },
                    replaceQuery = replaceQuery,
                    onReplaceChange = { replaceQuery = it },
                    ignoreCase = ignoreCase,
                    onToggleIgnoreCase = { ignoreCase = !ignoreCase },
                    useRegex = useRegex,
                    onToggleRegex = { useRegex = !useRegex },
                    count = findCount,
                    index = findIndex,
                    onPrev = { runCatching { editorRef.value?.searcher?.gotoPrevious() } },
                    onNext = { runCatching { editorRef.value?.searcher?.gotoNext() } },
                    onReplaceOne = {
                        runCatching { editorRef.value?.searcher?.replaceCurrentMatch(replaceQuery) }
                    },
                    onReplaceAll = {
                        runCatching { editorRef.value?.searcher?.replaceAll(replaceQuery) }
                    },
                    onClose = { closeFind() },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (previewMode) {
                    // 异步渲染:预览曾直接在 update 块里主线程 toMarkdown,大文件会卡死 UI
                    val markwon = com.yumao.mdreader.ui.rememberMarkwon(settings, dark)
                    val previewRenderer = remember { com.yumao.mdreader.render.MarkwonRenderer() }
                    val previewTv = remember { mutableStateOf<android.widget.TextView?>(null) }
                    var previewRenderedFor by remember { mutableStateOf<String?>(null) }
                    DisposableEffect(Unit) {
                        onDispose { previewRenderer.cancel() }
                    }
                    LaunchedEffect(document.content, markwon) {
                        val content = com.yumao.mdreader.markdown.RelativeImages.resolve(
                            document.content, document.uri
                        )
                        if (previewRenderedFor == content) return@LaunchedEffect
                        previewRenderedFor = content
                        previewTv.value?.text = "渲染中…"
                        previewRenderer.submit(
                            previewRenderer.nextSeq(), markwon, content, 1,
                            java.util.concurrent.atomic.AtomicBoolean(false),
                        ) { out ->
                            val tv = previewTv.value
                            if (tv != null && out != null) {
                                markwon.setParsedMarkdown(tv, out as android.text.Spanned)
                            }
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize().background(cs.surface),
                        factory = { ctx ->
                            android.widget.ScrollView(ctx).apply {
                                isFillViewport = true
                                val tv = android.widget.TextView(ctx)
                                tv.setPadding(48, 80, 48, 80)
                                addView(tv, android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                                previewTv.value = tv
                            }
                        },
                        update = { },
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            CodeEditor(ctx).apply {
                                val editor = this
                                val language = SoraSetup.markdownLanguage(ctx, dark)
                                setEditorLanguage(language)
                                val scheme = SoraSetup.scheme(ctx, dark)
                                SoraSetup.tintScheme(
                                    scheme, dark,
                                    cs.background.toArgb(), cs.onSurface.toArgb(), cs.outline.toArgb()
                                )
                                setColorScheme(scheme)
                                props.deleteEmptyLineFast = true
                                setText(document.content)
                                editorKey?.let { key ->
                                    vm.editorPosition(key)?.let { (line, column) ->
                                        runCatching { setSelection(line, column) }
                                    }
                                }
                                subscribeEvent(ContentChangeEvent::class.java) { e, _ ->
                                    if (e.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                                        dirty = true
                                        val cursor = editor.cursor
                                        val text = editor.text.toString()
                                        vm.scheduleDraftSave(text, cursor.leftLine, cursor.leftColumn)
                                        wordCount = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                                        charCount = text.length
                                    }
                                }
                                subscribeEvent(PublishSearchResultEvent::class.java) { e, _ ->
                                    val count = e.getSearcher().matchedPositionCount
                                    val index = e.getSearcher().currentMatchedPositionIndex
                                    post {
                                        findCount = count
                                        findIndex = index
                                    }
                                }
                            }.also { editorRef.value = it }
                        },
                        update = { ed ->
                            ed.setTextSizePx(settings.editorFontSize * ed.resources.displayMetrics.density)
                            ed.setWordwrap(settings.editorWordWrap)
                            ed.setLineNumberEnabled(settings.editorLineNumbers)
                            SoraSetup.tintScheme(
                                ed.colorScheme, dark,
                                cs.background.toArgb(), cs.onSurface.toArgb(), cs.outline.toArgb()
                            )
                        },
                    )
                }
            }

            if (!previewMode) {
                EditorChips(
                    editorRef = editorRef,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                )
            }
        }
    }

    if (askSaveAs) {
        AlertDialog(
            onDismissRequest = { askSaveAs = false },
            title = { Text("无法覆盖原文件") },
            text = { Text("该文件来自其他应用的临时授权（只允许读取）。将编辑后的内容保存到新位置吗？") },
            confirmButton = {
                TextButton(onClick = {
                    askSaveAs = false
                    flushToVm()
                    saveAsLauncher.launch("${document.title}.md")
                }) { Text("另存为") }
            },
            dismissButton = {
                TextButton(onClick = { askSaveAs = false }) { Text("取消") }
            },
        )
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("有未保存的更改") },
            text = { Text("保存后再离开，还是放弃修改？") },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    flushToVm()
                    val current = (vm.doc.value as? DocUiState.Ready)?.document
                    when {
                        current == null -> vm.closeDocument()
                        current.uri == null ->
                            saveAsLauncher.launch("${if (current.title.isBlank()) "新建文档" else current.title}.md")
                        !current.writable -> askSaveAs = true
                        else -> scope.launch {
                            if (vm.saveCurrent()) vm.closeDocument()
                        }
                    }
                }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askLeave = false
                    vm.discardDocument()
                }) { Text("放弃更改") }
            },
        )
    }
}

@Composable
private fun EditorFindPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceChange: (String) -> Unit,
    ignoreCase: Boolean,
    onToggleIgnoreCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    count: Int,
    index: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "查找…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Text(
                    if (count == 0) "0/0" else "${(index + 1).coerceAtLeast(0)}/$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(onClick = onPrev, enabled = count > 0, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "上一个",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = count > 0, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "下一个",
                        modifier = Modifier.size(20.dp),
                    )
                }
                TextButton(onClick = onToggleIgnoreCase) {
                    Text(
                        "Aa",
                        fontWeight = FontWeight.SemiBold,
                        color = if (ignoreCase) cs.primary else cs.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggleRegex) {
                    Text(
                        ".*",
                        fontWeight = FontWeight.SemiBold,
                        color = if (useRegex) cs.primary else cs.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭查找",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (replaceQuery.isEmpty()) {
                            Text(
                                "替换为…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                TextButton(onClick = onReplaceOne, enabled = count > 0) { Text("替换") }
                TextButton(onClick = onReplaceAll, enabled = count > 0) { Text("全部") }
            }
        }
    }
}

@Composable
private fun EditorChips(editorRef: State<CodeEditor?>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun ed(): CodeEditor? = editorRef.value
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "# ") } }, label = { Text("H1") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "## ") } }, label = { Text("H2") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "### ") } }, label = { Text("H3") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "#### ") } }, label = { Text("H4") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "> ") } }, label = { Text("引用") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "- ") } }, label = { Text("列表") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.toggleLinePrefix(it, "- [ ] ") } }, label = { Text("任务") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "**", "**") } }, label = { Text("粗体") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "*", "*") } }, label = { Text("斜体") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "`", "`") } }, label = { Text("代码") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "~~", "~~") } }, label = { Text("删除线") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "$", "$") } }, label = { Text("行内公式") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.insertBlock(it, "```\n\n```", 4) } }, label = { Text("代码块") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.insertBlock(it, "$$\n\n$$", 3) } }, label = { Text("公式块") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.insertBlock(it, "\n---\n", 5) } }, label = { Text("分隔线") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "[", "](https://)") } }, label = { Text("链接") })
            SuggestionChip(onClick = { ed()?.let { EditorActions.wrap(it, "![", "](https://)") } }, label = { Text("图片") })
            SuggestionChip(
                onClick = {
                    ed()?.let {
                        EditorActions.insertBlock(
                            it,
                            "| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |",
                            0
                        )
                    }
                },
                label = { Text("表格") },
            )
        }
    }
}
