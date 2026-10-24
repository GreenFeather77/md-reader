package com.yumao.mdreader.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumao.mdreader.MdViewModel
import com.yumao.mdreader.markdown.DerivedReaderColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class BgPreset(val name: String, val color: Int)

internal val PaperPresets = listOf(
    BgPreset("米白", 0xFFFAF6EE.toInt()),
    BgPreset("纸黄", 0xFFF5E9D4.toInt()),
    BgPreset("豆沙绿", 0xFFE3EDDC.toInt()),
    BgPreset("薄雾蓝", 0xFFE3ECF2.toInt()),
    BgPreset("藕紫", 0xFFEAE4EF.toInt()),
    BgPreset("樱粉", 0xFFF4E6E8.toInt()),
    BgPreset("月白", 0xFFEFF3F6.toInt()),
    BgPreset("青瓷", 0xFFDFE8E4.toInt()),
    BgPreset("浅驼", 0xFFEFE3D3.toInt()),
    BgPreset("嫩芽", 0xFFEDF3D9.toInt()),
    BgPreset("天青", 0xFFDDEAF3.toInt()),
    BgPreset("云粉", 0xFFF6ECEF.toInt()),
)

internal val NightPresets = listOf(
    BgPreset("纯黑", 0xFF000000.toInt()),
    BgPreset("墨灰", 0xFF1B1D20.toInt()),
    BgPreset("炭黑", 0xFF232629.toInt()),
    BgPreset("夜蓝", 0xFF14243A.toInt()),
    BgPreset("墨绿", 0xFF12281B.toInt()),
    BgPreset("深棕", 0xFF2A1E12.toInt()),
    BgPreset("酒红", 0xFF2E161C.toInt()),
    BgPreset("藏蓝", 0xFF1A2C4E.toInt()),
    BgPreset("紫夜", 0xFF241736.toInt()),
    BgPreset("橄榄", 0xFF262A12.toInt()),
    BgPreset("石墨", 0xFF2E3438.toInt()),
    BgPreset("蓝灰", 0xFF1F2D3D.toInt()),
)

/** Hand-picked accent candidates; first six for light paper, last four for dark. */
internal val AccentPresets = listOf(
    BgPreset("湖青", 0xFF2FA8C9.toInt()),
    BgPreset("天蓝", 0xFF3B6FD4.toInt()),
    BgPreset("松绿", 0xFF1F6E43.toInt()),
    BgPreset("赭金", 0xFF955F28.toInt()),
    BgPreset("绛紫", 0xFF7C4DA0.toInt()),
    BgPreset("绯红", 0xFFC03A2B.toInt()),
    BgPreset("亮青", 0xFF5BC8E8.toInt()),
    BgPreset("亮蓝", 0xFF7DA9FF.toInt()),
    BgPreset("薄荷", 0xFF6FD3A0.toInt()),
    BgPreset("琥珀", 0xFFE0A64B.toInt()),
)

private fun hsvOf(argb: Int, out: FloatArray): FloatArray {
    AColor.colorToHSV(argb and 0x00FFFFFF, out)
    return out
}

/** 全角字符转半角,丢弃 #/0x 前缀与一切非十六进制字符;恰好 6 位时返回 ARGB,否则 null。 */
private fun normalizeHexInput(raw: String): String {
    val sb = StringBuilder()
    for (ch in raw) {
        val c = ch.code
        when (c) {
            0xFF10 - 0xFF10 + '#'.code -> sb.append('#')
            in 0xFF10..0xFF19 -> sb.append((c - 0xFF10 + 0x30).toChar())   // ０-９
            in 0xFF41..0xFF5A -> sb.append((c - 0xFF41 + 0x61).toChar())   // ａ-ｚ
            in 0xFF21..0xFF3A -> sb.append((c - 0xFF21 + 0x41).toChar())   // Ａ-Ｚ
            else -> sb.append(ch)
        }
    }
    return sb.toString().filter { it.isLetterOrDigit() || it == '#' }.take(9)
}

private fun parseHexColor(input: String): Int? {
    var s = normalizeHexInput(input)
    if (s.startsWith("#")) s = s.substring(1)
    if (s.length >= 2 && (s.startsWith("0x") || s.startsWith("0X"))) s = s.substring(2)
    s = s.uppercase().filter { it.isDigit() || it in 'A'..'F' }
    if (s.length != 6) return null
    return (s.toLong(16).toInt() and 0x00FFFFFF) or 0xFF000000.toInt()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderColorPickerSheet(vm: MdViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Working color; -1 (follow theme) is never edited in place, sliders start from paper white.
    val initial = if (settings.readerBgColor != -1) settings.readerBgColor else 0xFFFAF6EE.toInt()
    var draft by remember { mutableIntStateOf(initial) }
    val hsv = remember { FloatArray(3).also { hsvOf(initial, it) } }

    fun setFromHsv() {
        draft = (0xFF shl 24) or (AColor.HSVToColor(hsv) and 0x00FFFFFF)
    }
    LaunchedEffect(draft) {
        delay(220)
        if (draft != settings.readerBgColor) {
            scope.launch { vm.settingsRepo.setReaderBgColor(draft) }
        }
    }

    var hexInput by remember { mutableStateOf("") }
    var hexError by remember { mutableStateOf(false) }
    val currentBg = settings.readerBgColor
    val followMode = currentBg == -1
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "阅读背景",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            val preview = if (followMode) 0xFFFAF6EE.toInt() else draft
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(preview)),
            ) {
                Text(
                    "示例正文 The quick brown fox 123",
                    color = Color(DerivedReaderColors.foreground(preview)),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .clickable {
                        scope.launch { vm.settingsRepo.setReaderBgColor(-1) }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "跟随主题（App 深浅模式自动配色）",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (followMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                if (followMode) {
                    Text("当前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("浅色纸面", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            SwatchGrid(PaperPresets, draft, onSelect = {
                draft = it
                hsvOf(it, hsv)
            })

            Text("深色夜读", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            SwatchGrid(NightPresets, draft, onSelect = {
                draft = it
                hsvOf(it, hsv)
            })

            Text("自定义", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
            Text("色相", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hsv[0],
                onValueChange = { v ->
                    hsv[0] = v
                    setFromHsv()
                },
                valueRange = 0f..360f,
            )
            Text("饱和度", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hsv[1],
                onValueChange = { v ->
                    hsv[1] = v
                    setFromHsv()
                },
                valueRange = 0f..1f,
            )
            Text("明度", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hsv[2],
                onValueChange = { v ->
                    hsv[2] = v
                    setFromHsv()
                },
                valueRange = 0f..1f,
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = normalizeHexInput(it); hexError = false },
                    singleLine = true,
                    placeholder = { Text("#%06X".format(draft and 0x00FFFFFF)) },
                    isError = hexError,
                    supportingText = if (hexError) ({ Text("需要 6 位十六进制，如 1F6E43") }) else null,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = {
                        val parsed = parseHexColor(hexInput)
                        if (parsed != null) {
                            hexError = false
                            draft = parsed
                            hsvOf(parsed, hsv)
                            hexInput = ""
                        } else hexError = true
                    },
                ) { Text("应用") }
            }

            Text("强调色", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                "链接、列表符号、引用线与搜索高亮的颜色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { scope.launch { vm.settingsRepo.setReaderAccent(0) } },
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "A",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "自动",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settings.readerAccent == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                AccentPresets.forEach { p ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { scope.launch { vm.settingsRepo.setReaderAccent(p.color) } },
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(p.color)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (settings.readerAccent == p.color) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(DerivedReaderColors.foreground(p.color)).copy(alpha = 0.55f)),
                                )
                            }
                        }
                        Text(
                            p.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.readerAccent == p.color) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Text("文字颜色", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SegmentedButton(
                    selected = followMode || !settings.readerTextLockedDark,
                    onClick = { scope.launch { vm.settingsRepo.setReaderTextLockedDark(false) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("自动对比") }
                SegmentedButton(
                    selected = !followMode && settings.readerTextLockedDark,
                    onClick = { scope.launch { vm.settingsRepo.setReaderTextLockedDark(true) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("强制墨色") }
            }
            if (!followMode) {
                val contrastInfo = DerivedReaderColors.contrastInfo(draft, settings.readerTextLockedDark)
                Text(
                    contrastInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwatchGrid(presets: List<BgPreset>, selected: Int, onSelect: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(presets) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(p.color) }) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(p.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == p.color) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(DerivedReaderColors.foreground(p.color)).copy(alpha = 0.55f)),
                        )
                    }
                }
                Text(
                    p.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected == p.color) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
