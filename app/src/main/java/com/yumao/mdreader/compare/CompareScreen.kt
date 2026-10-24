package com.yumao.mdreader.compare

import android.graphics.Typeface
import android.text.Layout
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumao.mdreader.MdViewModel
import com.yumao.mdreader.Screen
import com.yumao.mdreader.data.RecentFile
import com.yumao.mdreader.render.MarkwonRenderer
import io.noties.markwon.Markwon
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal side-by-side read-only comparison of two recent documents.
 * Each pane renders independently on the shared executor thread.
 */
@Composable
fun CompareScreen(vm: MdViewModel, markwon: Markwon) {
    BackHandler { vm.go(Screen.HOME) }
    val recents by vm.recents.collectAsStateWithLifecycle()

    var left by remember { mutableStateOf<RecentFile?>(null) }
    var right by remember { mutableStateOf<RecentFile?>(null) }
    var leftText by remember { mutableStateOf<String?>(null) }
    var rightText by remember { mutableStateOf<String?>(null) }
    /** 0 = picking left pane, 1 = right. */
    var pickingSide by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(recents) {
        if (left == null && recents.isNotEmpty()) left = recents[0]
        if (right == null && recents.size > 1) right = recents[1]
    }
    LaunchedEffect(left) {
        leftText = null
        leftText = left?.let { vm.loadRecentForCompare(it)?.second }
    }
    LaunchedEffect(right) {
        rightText = null
        rightText = right?.let { vm.loadRecentForCompare(it)?.second }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.go(Screen.HOME) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "对比阅读",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val l = left
                    left = right
                    right = l
                },
                enabled = left != null && right != null,
            ) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = "左右互换")
            }
        }
        Row(Modifier.weight(1f)) {
            ComparePane(
                title = left?.title ?: "选择文档",
                text = leftText,
                markwon = markwon,
                onPick = { pickingSide = 0 },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            ComparePane(
                title = right?.title ?: "选择文档",
                text = rightText,
                markwon = markwon,
                onPick = { pickingSide = 1 },
                modifier = Modifier.weight(1f),
            )
        }
    }

    pickingSide?.let { side ->
        AlertDialog(
            onDismissRequest = { pickingSide = null },
            title = { Text(if (side == 0) "左侧文档" else "右侧文档") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (recents.isEmpty()) {
                        Text("最近列表为空，先打开一个文档", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    recents.take(20).forEach { rf ->
                        Text(
                            rf.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (side == 0) left = rf else right = rf
                                    pickingSide = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickingSide = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ComparePane(
    title: String,
    text: String?,
    markwon: Markwon,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val renderer = remember { MarkwonRenderer() }
    var renderedFor by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { renderer.cancel() }
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPick) { Text("更换") }
        }
        Box(Modifier.weight(1f)) {
            if (text == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ScrollView(ctx).apply {
                            isFillViewport = true
                            isVerticalScrollBarEnabled = false
                            val tv = TextView(ctx)
                            tv.includeFontPadding = false
                            tv.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                            tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                            val d = resources.displayMetrics.density
                            tv.setPadding((12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt(), (32 * d).toInt())
                            addView(tv, android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                            tvRef.value = tv
                        }
                    },
                    update = { sv ->
                        val tv = (sv as? ScrollView)?.getChildAt(0) as? TextView
                        tv?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        tv?.setTypeface(Typeface.SANS_SERIF)
                    },
                )
                LaunchedEffect(text) {
                    if (renderedFor == text) return@LaunchedEffect
                    renderedFor = text
                    renderer.submit(
                        renderer.nextSeq(), markwon, text, 1, AtomicBoolean(false),
                    ) { out ->
                        val tv = tvRef.value
                        if (tv != null && out != null) {
                            markwon.setParsedMarkdown(tv, out as android.text.Spanned)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
