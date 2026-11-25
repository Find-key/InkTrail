package com.inktrail.reader.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktrail.reader.domain.model.ReaderPreferences
import com.inktrail.reader.domain.model.ReaderTheme
import com.inktrail.reader.ui.theme.ReaderPalette
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TypographySettingsSheet(
    visible: Boolean,
    preferences: ReaderPreferences,
    palette: ReaderPalette,
    onUpdateFontSize: (Float) -> Unit,
    onUpdateLineHeight: (Float) -> Unit,
    onUpdateHorizontalMargin: (Int) -> Unit,
    onUpdateParagraphSpacing: (Int) -> Unit,
    onUpdateTheme: (ReaderTheme) -> Unit,
    onUpdateCleanerEnabled: (Boolean) -> Unit,
    onUpdateMergeBlankLines: (Boolean) -> Unit,
    onUpdateFixHardLineBreaks: (Boolean) -> Unit,
    onUpdateAddCjkSpacing: (Boolean) -> Unit,
    onUpdateNormalizeParagraphIndent: (Boolean) -> Unit,
    onUpdateFocusLineMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "排版与阅读设置",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryText
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭设置",
                                tint = palette.primaryText
                            )
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ThemeSelectorSection(
                            activeTheme = preferences.theme,
                            palette = palette,
                            onSelectTheme = onUpdateTheme
                        )

                        HorizontalDivider(color = palette.divider)

                        FontSizeRow(
                            fontSizeSp = preferences.fontSizeSp,
                            palette = palette,
                            onUpdateFontSize = onUpdateFontSize
                        )

                        LabeledSliderRow(
                            label = "行距倍数",
                            valueText = String.format(Locale.getDefault(), "%.1fx", preferences.lineHeightMultiplier),
                            value = preferences.lineHeightMultiplier,
                            valueRange = ReaderPreferences.MIN_LINE_HEIGHT_MULTIPLIER..ReaderPreferences.MAX_LINE_HEIGHT_MULTIPLIER,
                            steps = 11,
                            palette = palette,
                            onValueChange = { raw ->
                                val snapped = (raw * 10f).roundToInt() / 10f
                                onUpdateLineHeight(snapped)
                            }
                        )

                        LabeledSliderRow(
                            label = "左右页边距",
                            valueText = "${preferences.horizontalMarginDp} dp",
                            value = preferences.horizontalMarginDp.toFloat(),
                            valueRange = ReaderPreferences.MIN_HORIZONTAL_MARGIN_DP.toFloat()..ReaderPreferences.MAX_HORIZONTAL_MARGIN_DP.toFloat(),
                            steps = 7,
                            palette = palette,
                            onValueChange = { raw ->
                                onUpdateHorizontalMargin(raw.roundToInt())
                            }
                        )

                        LabeledSliderRow(
                            label = "段间距",
                            valueText = "${preferences.paragraphSpacingDp} dp",
                            value = preferences.paragraphSpacingDp.toFloat(),
                            valueRange = ReaderPreferences.MIN_PARAGRAPH_SPACING_DP.toFloat()..ReaderPreferences.MAX_PARAGRAPH_SPACING_DP.toFloat(),
                            steps = 11,
                            palette = palette,
                            onValueChange = { raw ->
                                onUpdateParagraphSpacing(raw.roundToInt())
                            }
                        )

                        HorizontalDivider(color = palette.divider)

                        PreferenceSwitchRow(
                            title = "焦点行高亮（防走神模式）",
                            subtitle = "突出当前阅读行并柔化其余行，支持上下拖动或单步跟读",
                            checked = preferences.focusLineMode,
                            palette = palette,
                            onCheckedChange = onUpdateFocusLineMode
                        )

                        HorizontalDivider(color = palette.divider)

                        PreferenceSwitchRow(
                            title = "智能排版清洗引擎",
                            subtitle = "关闭后将按原始文本格式展示",
                            checked = preferences.cleanerEnabled,
                            palette = palette,
                            onCheckedChange = onUpdateCleanerEnabled
                        )

                        if (preferences.cleanerEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.background.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactSwitchRow(
                                    title = "合并多余空行",
                                    checked = preferences.mergeBlankLines,
                                    palette = palette,
                                    onCheckedChange = onUpdateMergeBlankLines
                                )
                                CompactSwitchRow(
                                    title = "修复异常硬换行",
                                    checked = preferences.fixHardLineBreaks,
                                    palette = palette,
                                    onCheckedChange = onUpdateFixHardLineBreaks
                                )
                                CompactSwitchRow(
                                    title = "中西文与数字混排加空隙",
                                    checked = preferences.addCjkSpacing,
                                    palette = palette,
                                    onCheckedChange = onUpdateAddCjkSpacing
                                )
                                CompactSwitchRow(
                                    title = "正文段首规范缩进",
                                    checked = preferences.normalizeParagraphIndent,
                                    palette = palette,
                                    onCheckedChange = onUpdateNormalizeParagraphIndent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelectorSection(
    activeTheme: ReaderTheme,
    palette: ReaderPalette,
    onSelectTheme: (ReaderTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "护眼主题",
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondaryText
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReaderTheme.entries.forEach { theme ->
                val selected = theme == activeTheme
                val bg = Color(theme.backgroundHex)
                val fg = Color(theme.primaryTextHex)
                val borderColor = if (selected) palette.accent else palette.divider

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectTheme(theme) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = fg
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = theme.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSizeRow(
    fontSizeSp: Float,
    palette: ReaderPalette,
    onUpdateFontSize: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "正文字号",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primaryText
            )
            Text(
                text = "${fontSizeSp.roundToInt()} sp",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val next = (fontSizeSp - 1f).coerceAtLeast(ReaderPreferences.MIN_FONT_SIZE_SP)
                    onUpdateFontSize(next)
                },
                enabled = fontSizeSp > ReaderPreferences.MIN_FONT_SIZE_SP
            ) {
                Text("A-")
            }
            Slider(
                value = fontSizeSp,
                onValueChange = { raw -> onUpdateFontSize(raw.roundToInt().toFloat()) },
                valueRange = ReaderPreferences.MIN_FONT_SIZE_SP..ReaderPreferences.MAX_FONT_SIZE_SP,
                steps = (ReaderPreferences.MAX_FONT_SIZE_SP - ReaderPreferences.MIN_FONT_SIZE_SP).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent
                ),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    val next = (fontSizeSp + 1f).coerceAtMost(ReaderPreferences.MAX_FONT_SIZE_SP)
                    onUpdateFontSize(next)
                },
                enabled = fontSizeSp < ReaderPreferences.MAX_FONT_SIZE_SP
            ) {
                Text("A+")
            }
        }
    }
}

@Composable
private fun LabeledSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    palette: ReaderPalette,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primaryText
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent
            )
        )
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: ReaderPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent
            )
        )
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    palette: ReaderPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.primaryText,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent
            )
        )
    }
}
