package com.yumao.mdreader.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import com.yumao.mdreader.MdViewModel
import com.yumao.mdreader.Screen
import com.yumao.mdreader.data.CodeThemeMode
import com.yumao.mdreader.files.DocUiState
import com.yumao.mdreader.markdown.TextSearch
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.image.AsyncDrawableSpan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class TocEntry(val title: String, val level: Int, val offset: Int)

private data class ScrollInfo(val anchor: Int, val progress: Float, val heading: Int)

// Render pipeline deliberately avoids kotlinx.coroutines: withContext resumption
// back to the Compose dispatcher was silently lost under both debug and release
// (stuck renders). A plain executor + Handler(Looper.getMainLooper()) is reliable.
private val markwonRenderExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "md-render").apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY
    }
}
private val markwonMainHandler = Handler(Looper.getMainLooper())
private val markwonRenderSeq = AtomicLong(0)

private const val RENDER_TIMEOUT_MS = 20_000L

private fun exportHtml(markdown: String, title: String): String {
    val parser = org.commonmark.parser.Parser.builder().build()
    val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
    val body = renderer.render(parser.parse(markdown))
    return buildString {
        append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>")
        append(title)
        append("</title><style>")
        append("body{max-width:720px;margin:24px auto;padding:0 16px;font-family:-apple-system,sans-serif;line-height:1.6;color:#222}")
        append("code{background:#f0f0f0;padding:2px 6px;border-radius:4px;font-family:monospace}")
        append("pre{background:#f6f8fa;padding:16px;border-radius:8px;overflow-x:auto}")
        append("blockquote{border-left:4px solid #ccc;margin:0;padding-left:16px;color:#666}")
        append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:8px}")
        append("img{max-width:100%}a{color:#0B57D0}")
        append("</style></head><body>")
        append(body)
        append("</body></html>")
    }
}

@SuppressLint("WrongConstant")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: MdViewModel, markwon: Markwon) {
    val docState by vm.doc.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val searchHistory by vm.settingsRepo.searchHistoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchMatchCase by vm.settingsRepo.searchMatchCaseFlow.collectAsStateWithLifecycle(initialValue = false)
    val dtoScope = rememberCoroutineScope()
    BackHandler { vm.closeDocument() }

    LaunchedEffect(docState) {
        if (docState is DocUiState.Idle) vm.closeDocument()
    }

    val docError = docState as? DocUiState.Error
    if (docError != null) {
        com.yumao.mdreader.ui.DocumentErrorState(
            message = docError.message,
            onRetry = { vm.retryLast() },
            onBack = { vm.closeDocument() },
        )
        return
    }

    val ready = docState as? DocUiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val document = ready.document
    val docUri = document.uri

    var showToc by remember { mutableStateOf(false) }
    var showType by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var toc by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var chromeVisible by remember { mutableStateOf(true) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<IntRange>>(emptyList()) }
    var currentMatch by remember { mutableStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val initialAnchor = remember(docUri) {
        recents.firstOrNull { it.uri == docUri?.toString() }?.anchor ?: 0
    }
    var restorePending by remember(docUri) { mutableStateOf(initialAnchor > 0) }
    var scrollAnchor by remember(docUri) { mutableStateOf(initialAnchor) }
    var scrollProgress by remember(docUri) { mutableStateOf(0f) }
    var currentHeading by remember(docUri) { mutableStateOf(-1) }

    var rendering by remember { mutableStateOf(false) }
    var showRendering by remember { mutableStateOf(false) }
    var contentRendered by remember(docUri) { mutableStateOf(false) }
    var percentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(rendering) {
        if (rendering) {
            delay(120)
            if (rendering) showRendering = true
        } else {
            showRendering = false
        }
    }
    LaunchedEffect(scrollProgress, contentRendered, showRendering) {
        if (!contentRendered || showRendering) {
            percentVisible = false
        } else {
            percentVisible = true
            delay(1200)
            percentVisible = false
        }
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchQuery = ""
        matches = emptyList()
        chromeVisible = true
    }
    BackHandler(enabled = previewBitmap != null) { previewBitmap = null }

    val activity = LocalContext.current as? Activity
    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(searchOpen) { if (searchOpen) chromeVisible = true }

    val context = LocalContext.current
    val scope3 = rememberCoroutineScope()
    val scrollRef = remember { mutableStateOf<ScrollView?>(null) }
    val textRef = remember { mutableStateOf<TextView?>(null) }
    var renderedKey by remember { mutableStateOf("") }
    var renderTick by remember { mutableStateOf(0) }
    var viewReady by remember { mutableStateOf(false) }
    val newKey = "${document.content.hashCode()}-${settings.readerFontScale}-" +
        "${settings.latexEnabled}-${settings.codeTheme}-$markwon-" +
        "${settings.readerBgColor}-${settings.readerTextLockedDark}"

    val saveImageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png")
    ) { target ->
        val bmp = previewBitmap ?: return@rememberLauncherForActivityResult
        if (target != null) {
            scope3.launch {
                runCatching {
                    context.contentResolver.openOutputStream(target)?.use { os ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                    vm.notify("图片已保存")
                }.onFailure { vm.notify("保存失败") }
            }
        }
    }

    fun shareImage(bmp: Bitmap) {
        scope3.launch {
            runCatching {
                val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                val file = java.io.File(dir, "mdreader_img_${System.currentTimeMillis()}.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "分享图片"))
            }.onFailure { vm.notify("分享失败") }
        }
    }

    LaunchedEffect(searchOpen, searchQuery, searchMatchCase, renderTick) {
        if (!searchOpen) {
            matches = emptyList()
            currentMatch = 0
            return@LaunchedEffect
        }
        matches = TextSearch.findMatches(textRef.value?.text ?: "", searchQuery, ignoreCase = !searchMatchCase)
        currentMatch = 0
    }

    val searchAllArgb = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f).toArgb()
    val searchCurrentArgb = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f).toArgb()

    LaunchedEffect(matches, currentMatch, searchOpen, renderTick) {
        val tv = textRef.value ?: return@LaunchedEffect
        applySearchHighlights(tv, searchOpen, matches, currentMatch, searchAllArgb, searchCurrentArgb)
        if (searchOpen && matches.isNotEmpty()) {
            val idx = currentMatch.coerceIn(matches.indices)
            scrollToOffset(tv, scrollRef.value, matches[idx].first)
        }
    }

    LaunchedEffect(docUri) {
        if (docUri == null) return@LaunchedEffect
        snapshotFlow { scrollAnchor to scrollProgress }.collectLatest { (anchor, progress) ->
            delay(500)
            if (contentRendered) vm.saveReadingPosition(docUri, anchor, progress)
        }
    }

    DisposableEffect(docUri) {
        onDispose {
            Log.i("MDReader", "dispose save anchor=$scrollAnchor progress=$scrollProgress rendered=$contentRendered")
            markwonRenderSeq.incrementAndGet()
            if (docUri != null && contentRendered) vm.saveReadingPosition(docUri, scrollAnchor, scrollProgress)
        }
    }

    LaunchedEffect(newKey, viewReady) {
        if (!viewReady) return@LaunchedEffect
        val tv = textRef.value ?: return@LaunchedEffect
        if (renderedKey == newKey) return@LaunchedEffect
        val first = renderedKey.isEmpty()
        val keepY = scrollRef.value?.scrollY ?: 0
        renderedKey = newKey
        rendering = true
        val content = document.content
        val seq = markwonRenderSeq.incrementAndGet()
        val finished = AtomicBoolean(false)
        Log.i("MDReader", "render start len=${content.length} first=$first seq=$seq")

        fun finishRender(rendered: CharSequence?) {
            val workspace = textRef.value ?: tv
            if (rendered != null) {
                runCatching { io.noties.markwon.image.AsyncDrawableScheduler.unschedule(workspace) }
                workspace.setText(rendered, TextView.BufferType.SPANNABLE)
                runCatching { io.noties.markwon.image.AsyncDrawableScheduler.schedule(workspace) }
                    .onFailure { Log.w("MDReader", "schedule drawables failed", it) }
            } else {
                Log.w("MDReader", "render watchdog exhausted (seq=$seq); fallback to main-thread render")
                runCatching { markwon.setMarkdown(workspace, content) }
                    .onFailure { Log.e("MDReader", "fallback render failed", it) }
            }
            Log.i("MDReader", "render done seq=$seq textLen=${workspace.text?.length}")
            toc = collectToc(workspace)
            contentRendered = workspace.text?.isNotEmpty() == true
            if (restorePending) {
                restorePending = false
                val anchor = initialAnchor
                workspace.doOnLayout {
                    val y = restoreScroll(workspace, scrollRef.value, anchor)
                    Log.i("MDReader", "restore anchor=$anchor y=$y")
                    scrollRef.value?.let { sv ->
                        scrollInfo(workspace, sv, toc)?.let {
                            scrollAnchor = it.anchor
                            scrollProgress = it.progress
                            currentHeading = it.heading
                        }
                    }
                }
            } else if (!first) {
                workspace.doOnLayout { scrollRef.value?.scrollTo(0, keepY) }
            }
            renderTick++
            rendering = false
        }

        startMarkwonRender(seq, markwon, content, attempt = 1, finished, ::finishRender)
    }

    Box(Modifier.fillMaxSize()) {
        val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
        val highlightArgb = MaterialTheme.colorScheme.secondaryContainer.toArgb()
        val hasCustomBg = settings.readerBgColor != -1
        val contentBgArgb = if (hasCustomBg) settings.readerBgColor else 0
        val contentTextArgb = if (hasCustomBg) {
            com.yumao.mdreader.markdown.DerivedReaderColors.foreground(contentBgArgb, settings.readerTextLockedDark)
        } else onSurfaceArgb
        val contentHighlightArgb = if (hasCustomBg) {
            (0x33 shl 24) or (com.yumao.mdreader.markdown.DerivedReaderColors.accent(contentBgArgb) and 0x00FFFFFF)
        } else highlightArgb
        val contentBgColor = if (hasCustomBg) Color(contentBgArgb and 0x00FFFFFF) else MaterialTheme.colorScheme.surface
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fontScaleState = rememberUpdatedState(settings.readerFontScale)
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(contentBgColor),
            factory = { ctx ->
                ScrollView(ctx).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = false
                    val d = resources.displayMetrics.density
                    val tv = ReadingTextView(ctx)
                    tv.includeFontPadding = false
                    tv.breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                    tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                    tv.setPadding((20 * d).toInt(), (140 * d).toInt(), (20 * d).toInt(), (128 * d).toInt())
                    addView(tv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    textRef.value = tv
                    tv.setOnClickListener { chromeVisible = !chromeVisible }
                    tv.onImageTap = { drawable -> previewBitmap = drawable.toBitmapOrNull() }
                    tv.onPinchFinished = { fraction ->
                        val next = (fontScaleState.value * fraction).coerceIn(0.75f, 2.2f)
                        scope3.launch { vm.settingsRepo.setReaderFontScale(next) }
                    }
                    setOnScrollChangeListener { sv, _, scrollY, _, oldScrollY ->
                        val dy = scrollY - oldScrollY
                        if (dy > 16) {
                            chromeVisible = false
                        } else if (dy < -16) {
                            chromeVisible = true
                        }
                        scrollInfo(tv, sv, toc)?.let {
                            scrollAnchor = it.anchor
                            scrollProgress = it.progress
                            currentHeading = it.heading
                        }
                    }
                    viewReady = true
                }.also { scrollRef.value = it }
            },
            update = { _ ->
                val tv = textRef.value ?: return@AndroidView
                val sv = scrollRef.value
                tv.setTextColor(contentTextArgb)
                sv?.setBackgroundColor(if (hasCustomBg) contentBgArgb else android.graphics.Color.TRANSPARENT)
                tv.highlightColor = contentHighlightArgb
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * settings.readerFontScale)
                tv.setLineSpacing(0f, settings.readerLineHeight)
            },
        )

        if (showRendering) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 40.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }

        if (contentRendered) {
            LinearProgressIndicator(
                progress = { scrollProgress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                drawStopIndicator = {},
            )
        }

        AnimatedVisibility(
            visible = percentVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = buildString {
                        append("${(scrollProgress * 100).toInt()}%")
                        toc.getOrNull(currentHeading)?.title?.take(12)?.let {
                            append("  ·  ").append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    maxLines = 1,
                )
            }
        }

        val scrimSurface = MaterialTheme.colorScheme.surface
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 40.dp)
                .background(
                    Brush.verticalGradient(
                        0f to scrimSurface,
                        0.45f to scrimSurface.copy(alpha = 0.82f),
                        1f to Color.Transparent,
                    )
                )
        )

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            ReaderTopBar(
                title = document.title,
                searching = searchOpen,
                onBack = { vm.closeDocument() },
                onSearch = {
                    if (searchOpen) {
                        val q = searchQuery
                        if (q.isNotBlank()) {
                            dtoScope.launch { vm.settingsRepo.addSearchHistory(q) }
                        }
                        searchOpen = false
                        searchQuery = ""
                        chromeVisible = true
                    } else {
                        searchOpen = true
                    }
                },
                onToc = { showToc = true },
                onFont = { showType = true },
                onShare = { showShareMenu = true },
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 92.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        ) {
            ReaderSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                count = matches.size,
                index = currentMatch,
                matchCase = searchMatchCase,
                onMatchCaseChange = { v -> dtoScope.launch { vm.settingsRepo.setSearchMatchCase(v) } },
                history = if (searchQuery.isEmpty()) searchHistory else emptyList(),
                onHistoryPick = { searchQuery = it },
                onPrev = {
                    if (matches.isNotEmpty()) {
                        currentMatch = (currentMatch - 1 + matches.size) % matches.size
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        currentMatch = (currentMatch + 1) % matches.size
                    }
                },
                onClose = {
                    val q = searchQuery
                    if (q.isNotBlank()) {
                        dtoScope.launch { vm.settingsRepo.addSearchHistory(q) }
                    }
                    searchOpen = false
                    searchQuery = ""
                    chromeVisible = true
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            FloatingActionButton(
                onClick = { vm.go(Screen.EDITOR) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
        }
    }

    if (showToc) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val tocState = rememberLazyListState()
        LaunchedEffect(showToc) {
            if (toc.isNotEmpty()) {
                tocState.scrollToItem(currentHeading.coerceIn(0, toc.lastIndex))
            }
        }
        ModalBottomSheet(onDismissRequest = { showToc = false }, sheetState = sheet) {
            Text(
                "目录",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (toc.isEmpty()) {
                Text(
                    "没有检测到标题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = tocState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(toc) { index, entry ->
                        val selected = index == currentHeading
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    showToc = false
                                    currentHeading = index
                                    scrollRef.value?.let { sv ->
                                        val target = scrollToHeading(textRef.value, entry.offset)
                                        target?.let { sv.smoothScrollTo(0, it) }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (entry.level >= 2) {
                                val inset = (entry.level - 2) * 12
                                Spacer(Modifier.width(inset.dp))
                            }
                            Text(
                                entry.title,
                                style = if (entry.level <= 2) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (entry.level == 1) FontWeight.Bold
                                else if (entry.level == 2) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected || entry.level == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showType) {
        ReadingPrefsSheet(vm = vm, onDismiss = { showType = false })
    }

    previewBitmap?.let { bmp ->
        ImagePreviewDialog(
            bitmap = bmp,
            onDismiss = { previewBitmap = null },
            onSave = { saveImageLauncher.launch("mdreader_image") },
            onShare = { shareImage(bmp) },
        )
    }

    if (showShareMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShareMenu = false },
            title = { Text("分享") },
            text = {
                Column {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showShareMenu = false
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, document.content)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "分享 Markdown 源文")) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("分享 Markdown 源文") }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showShareMenu = false
                            scope3.launch {
                                runCatching {
                                    val html = exportHtml(document.content, document.title)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/html"
                                        putExtra(Intent.EXTRA_TEXT, html)
                                    }
                                    context.startActivity(Intent.createChooser(send, "分享 HTML"))
                                }.onFailure { vm.notify("HTML 导出失败") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("分享渲染后 HTML") }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showShareMenu = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    searching: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToc: () -> Unit,
    onFont: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "查找",
                    tint = if (searching) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "分享源文") }
            IconButton(onClick = onFont) { Icon(Icons.Outlined.FormatSize, contentDescription = "阅读设置") }
            IconButton(onClick = onToc) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录") }
        }
    }
}

@Composable
private fun ReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    count: Int,
    index: Int,
    matchCase: Boolean,
    onMatchCaseChange: (Boolean) -> Unit,
    history: List<String>,
    onHistoryPick: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "在文中查找…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matchCase) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMatchCaseChange(!matchCase) }
                        .background(
                            if (matchCase) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    if (count == 0) "0" else "${index + 1}/$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(onClick = onPrev, enabled = count > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onNext, enabled = count > 0) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭查找")
                }
            }
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    history.forEach { h ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                                .clickable { onHistoryPick(h) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                h,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showActions by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onLongPress = { showActions = true },
                        onTap = { if (scale <= 1.05f) onDismiss() },
                    )
                }
                .transformable(
                    state = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 6f)
                        offset = if (scale <= 1.01f) Offset.Zero else offset + panChange
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
        if (showActions) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showActions = false },
                title = { Text("图片操作") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showActions = false; onSave() }) { Text("保存到文件") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showActions = false; onShare() }) { Text("分享图片") }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingPrefsSheet(vm: MdViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showColorPicker = true }
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("背景颜色", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (settings.readerBgColor == -1) "跟随主题" else "自定义",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (settings.readerBgColor == -1) MaterialTheme.colorScheme.surface
                            else Color(settings.readerBgColor and 0x00FFFFFF)
                        ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("字号", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(16 * settings.readerFontScale).toInt()} sp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = settings.readerFontScale,
                    onValueChange = { v -> scope.launch { vm.settingsRepo.setReaderFontScale(v) } },
                    valueRange = 0.75f..2.2f,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("行距", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.2f".format(settings.readerLineHeight),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = settings.readerLineHeight,
                    onValueChange = { v -> scope.launch { vm.settingsRepo.setReaderLineHeight(v) } },
                    valueRange = 1.0f..2.2f,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("代码块配色", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf(
                    CodeThemeMode.FOLLOW to "跟随主题",
                    CodeThemeMode.LIGHT to "明色",
                    CodeThemeMode.DARK to "暗色",
                ).forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.codeTheme == mode,
                        onClick = { scope.launch { vm.settingsRepo.setCodeTheme(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                    ) { Text(label) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LaTeX 公式渲染", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "关闭后以原文显示 \$...\$",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.latexEnabled,
                    onCheckedChange = { v -> scope.launch { vm.settingsRepo.setLatexEnabled(v) } },
                )
            }
        }
    }
    if (showColorPicker) {
        com.yumao.mdreader.ui.ReaderColorPickerSheet(vm = vm, onDismiss = { showColorPicker = false })
    }
}

private fun collectToc(tv: TextView): List<TocEntry> {
    val text = tv.text as? Spanned ?: return emptyList()
    if (text.isEmpty()) return emptyList()
    val spans = text.getSpans(0, text.length, HeadingSpan::class.java)
    return spans
        .sortedBy { text.getSpanStart(it) }
        .mapNotNull { span ->
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            val title = text.subSequence(s, e).toString().trim()
            if (title.isEmpty()) null else TocEntry(title, levelOf(span), s)
        }
}

private fun levelOf(span: HeadingSpan): Int = try {
    val field = HeadingSpan::class.java.getDeclaredField("level")
    field.isAccessible = true
    field.getInt(span)
} catch (t: Throwable) {
    2
}

private fun scrollToHeading(tv: TextView?, offset: Int): Int? {
    if (tv == null) return null
    val layout = tv.layout ?: return null
    val len = tv.text.length
    if (len == 0) return null
    val line = layout.getLineForOffset(offset.coerceIn(0, len - 1))
    return (layout.getLineTop(line)).coerceAtLeast(0)
}

private fun scrollToOffset(tv: TextView?, sv: ScrollView?, offset: Int) {
    if (tv == null || sv == null) return
    val layout = tv.layout ?: return
    val len = tv.text.length
    if (len == 0) return
    val line = layout.getLineForOffset(offset.coerceIn(0, len - 1))
    val margin = (110 * tv.resources.displayMetrics.density).toInt()
    sv.scrollTo(0, (layout.getLineTop(line) - margin).coerceAtLeast(0))
}

private fun startMarkwonRender(
    seq: Long,
    markwon: Markwon,
    content: String,
    attempt: Int,
    done: AtomicBoolean,
    onDone: (CharSequence?) -> Unit,
) {
    markwonRenderExecutor.execute {
        var out: CharSequence? = null
        try {
            out = markwon.toMarkdown(content)
        } catch (t: Throwable) {
            Log.w("MDReader", "markdown render failed (attempt=$attempt)", t)
        }
        markwonMainHandler.post {
            if (done.compareAndSet(false, true) && markwonRenderSeq.get() == seq) onDone(out)
        }
    }
    markwonMainHandler.postDelayed({
        if (done.get() || markwonRenderSeq.get() != seq) return@postDelayed
        Log.w("MDReader", "render watchdog fired (seq=$seq attempt=$attempt)")
        if (attempt < 3) {
            startMarkwonRender(seq, markwon, content, attempt + 1, done, onDone)
        } else {
            markwonMainHandler.post {
                if (done.compareAndSet(false, true) && markwonRenderSeq.get() == seq) onDone(null)
            }
        }
    }, RENDER_TIMEOUT_MS)
}

private fun restoreScroll(tv: TextView, sv: View?, anchor: Int): Int {
    val y = scrollToHeading(tv, anchor) ?: return 0
    sv?.scrollTo(0, y)
    return y
}

private fun scrollInfo(tv: TextView, sv: View, toc: List<TocEntry>): ScrollInfo? {
    val layout = tv.layout ?: return null
    if (layout.lineCount == 0 || tv.text.isEmpty()) return null
    val y = sv.scrollY
    val line = layout.getLineForVertical(y).coerceIn(0, layout.lineCount - 1)
    val anchor = layout.getLineStart(line)
    val denom = (tv.height - sv.height).coerceAtLeast(1)
    val progress = (y.toFloat() / denom).coerceIn(0f, 1f)
    val heading = toc.indexOfLast { it.offset <= anchor }
    return ScrollInfo(anchor, progress, heading)
}

private class SearchHighlightSpan(color: Int) : BackgroundColorSpan(color)

private class SearchCurrentSpan(color: Int) : BackgroundColorSpan(color)

private fun applySearchHighlights(
    tv: TextView,
    active: Boolean,
    matches: List<IntRange>,
    current: Int,
    allColor: Int,
    currentColor: Int,
) {
    val text = tv.text as? Spannable ?: return
    text.getSpans(0, text.length, BackgroundColorSpan::class.java)
        .filter { it is SearchHighlightSpan || it is SearchCurrentSpan }
        .forEach { text.removeSpan(it) }
    if (active && matches.isNotEmpty()) {
        matches.forEachIndexed { i, range ->
            val span = if (i == current) SearchCurrentSpan(currentColor)
            else SearchHighlightSpan(allColor)
            text.setSpan(span, range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    tv.invalidate()
}

private fun Drawable.toBitmapOrNull(): Bitmap? {
    if (this is BitmapDrawable) return bitmap
    val w = intrinsicWidth
    val h = intrinsicHeight
    if (w <= 0 || h <= 0) return null
    return runCatching {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            setBounds(0, 0, w, h)
            draw(canvas)
        }
    }.getOrNull()
}

/**
 * Reading column with comfortable gutters: fixed 20dp side padding on phones,
 * widening asymmetrically on large screens so text stays within a 680dp measure.
 */
private class ReadingTextView(context: Context) : TextView(context) {

    private val density = resources.displayMetrics.density
    private val basePad = (20 * density).toInt()
    private val maxContent = (680 * density).toInt()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    var onImageTap: ((Drawable) -> Unit)? = null
    /** Fired once when a pinch gesture ends, with cumulative scale factor (finalSize / startSize). */
    var onPinchFinished: ((Float) -> Unit)? = null

    private val scaleDetector = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startPx = 0f
        override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
            startPx = textSize
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, startPx * detector.scaleFactor)
            return true
        }
        override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
            val fraction = textSize / startPx.coerceAtLeast(1f)
            onPinchFinished?.invoke(fraction)
        }
    })

    private val longPressRunnable = Runnable {
        if (!isTextSelectable) setTextIsSelectable(true)
        performLongClick()
    }
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val extra = ((w - maxContent) / 2).coerceAtLeast(0)
        val pad = basePad + extra
        if (paddingLeft != pad || paddingRight != pad) {
            setPadding(pad, paddingTop, pad, paddingBottom)
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            scaleDetector.onTouchEvent(event)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!moved) {
                    val result = imageSpanAt(event.x, event.y)?.let { span ->
                        runCatching {
                            span.drawable.takeIf { it.hasResult() }?.result
                        }.getOrNull()
                    }
                    val listener = onImageTap
                    if (result != null && listener != null) {
                        isPressed = false
                        listener(result)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressRunnable)
        }
        return super.onTouchEvent(event)
    }

    private fun imageSpanAt(x: Float, y: Float): AsyncDrawableSpan? {
        val sp = text as? Spanned ?: return null
        if (sp.isEmpty()) return null
        val offset = getOffsetForPosition(x, y)
        val spans = sp.getSpans(0, sp.length, AsyncDrawableSpan::class.java)
        for (span in spans) {
            val s = sp.getSpanStart(span)
            val e = sp.getSpanEnd(span)
            if (offset in s until e || (offset - 1) in s until e) return span
        }
        return null
    }
}
