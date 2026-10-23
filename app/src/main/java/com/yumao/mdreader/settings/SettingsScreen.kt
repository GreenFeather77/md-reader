package com.yumao.mdreader.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumao.mdreader.MdViewModel
import com.yumao.mdreader.Screen
import com.yumao.mdreader.data.AppSettings
import com.yumao.mdreader.data.CodeThemeMode
import com.yumao.mdreader.data.ThemeMode
import com.yumao.mdreader.data.ThemePalette
import com.yumao.mdreader.data.ThemeSource
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MdViewModel) {
    BackHandler { vm.go(Screen.HOME) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 56.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.go(Screen.HOME) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // ---------- Appearance ----------
        Section("外观") {
            Label("主题来源")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                listOf(
                    ThemeSource.DYNAMIC to "动态取色",
                    ThemeSource.PALETTE to "Pixel 色板",
                ).forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeSource == mode,
                        onClick = { scope.launch { vm.settingsRepo.setThemeSource(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    ) { Text(label) }
                }
            }
            if (settings.themeSource == ThemeSource.PALETTE) {
                Label("选择色板")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThemePalette.entries.forEach { palette ->
                        val selected = palette == settings.palette
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(palette.seed))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                                .clickable { scope.launch { vm.settingsRepo.setPalette(palette) } },
                            contentAlignment = Alignment.Center,
                        ) {}
                    }
                }
            }
            Label("深浅模式")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                ).forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { vm.settingsRepo.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                    ) { Text(label) }
                }
            }
            SwitchRow("阅读时保持屏幕常亮", settings.keepScreenOn) { v ->
                scope.launch { vm.settingsRepo.setKeepScreenOn(v) }
            }
        }

        // ---------- Reading ----------
        Section("阅读") {
            Label("正文字号")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(16 * settings.readerFontScale).toInt()} sp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = settings.readerFontScale,
                    onValueChange = { scope.launch { vm.settingsRepo.setReaderFontScale(it) } },
                    valueRange = 0.75f..2.2f,
                )
            }
            Label("行距")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.2f".format(settings.readerLineHeight),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = settings.readerLineHeight,
                    onValueChange = { scope.launch { vm.settingsRepo.setReaderLineHeight(it) } },
                    valueRange = 1.0f..2.2f,
                )
            }
            Label("代码块配色")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
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
            SwitchRow("渲染 LaTeX 公式", settings.latexEnabled) { v ->
                scope.launch { vm.settingsRepo.setLatexEnabled(v) }
            }
        }

        // ---------- Editor ----------
        Section("编辑器") {
            Label("编辑字号")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${settings.editorFontSize} dp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = settings.editorFontSize.toFloat(),
                    onValueChange = { scope.launch { vm.settingsRepo.setEditorFontSize(it.toInt()) } },
                    valueRange = 11f..24f,
                )
            }
            SwitchRow("自动换行（软换行）", settings.editorWordWrap) { v ->
                scope.launch { vm.settingsRepo.setEditorWordWrap(v) }
            }
            SwitchRow("显示行号", settings.editorLineNumbers) { v ->
                scope.launch { vm.settingsRepo.setEditorLineNumbers(v) }
            }
            SwitchRow("自动保存草稿（应用内）", settings.autoDraftEnabled) { v ->
                scope.launch {
                    vm.settingsRepo.setAutoDraftEnabled(v)
                    if (!v) vm.clearDrafts()
                }
            }
        }

        // ---------- Others ----------
        Section("其他") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { confirmClear = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "清除最近打开记录",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            InfoRow("版本", versionName)
            InfoRow("渲染引擎", "Markwon 4.6 · JLatexMath · sora-editor")
            InfoRow("许可", "Apache-2.0 / MIT（第三方组件）")
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除最近记录") },
            text = { Text("将删除所有最近打开记录与缓存副本，不影响原文件。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearRecents()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { content() }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
