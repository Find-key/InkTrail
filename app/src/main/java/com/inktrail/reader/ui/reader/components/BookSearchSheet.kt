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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktrail.reader.engine.search.SearchHit
import com.inktrail.reader.ui.theme.ReaderPalette

@Composable
fun BookSearchSheet(
    visible: Boolean,
    query: String,
    spoilerFreeMode: Boolean,
    currentChapterNumber: Int,
    isSearching: Boolean,
    hasExecutedSearch: Boolean,
    results: List<SearchHit>,
    palette: ReaderPalette,
    onQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onToggleSpoilerFree: (Boolean) -> Unit,
    onSelectHit: (SearchHit) -> Unit,
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
                    .background(Color.Black.copy(alpha = 0.4f))
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
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全书关键词检索",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryText
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭搜索",
                                tint = palette.primaryText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("输入关键词、人物或情节…") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = palette.secondaryText
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "清空",
                                            tint = palette.secondaryText
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = palette.primaryText,
                                unfocusedTextColor = palette.primaryText,
                                cursorColor = palette.accent,
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.divider,
                                focusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onExecuteSearch,
                            enabled = query.isNotBlank() && !isSearching
                        ) {
                            Text("搜索", color = palette.accent)
                        }
                    }

                    Surface(
                        color = if (spoilerFreeMode) palette.accent.copy(alpha = 0.10f) else palette.background,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onToggleSpoilerFree(!spoilerFreeMode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = if (spoilerFreeMode) palette.accent else palette.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "防剧透模式",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = palette.primaryText
                                )
                                Text(
                                    text = if (spoilerFreeMode) {
                                        "已开启：仅搜索第 $currentChapterNumber 章当前阅读位置及之前内容"
                                    } else {
                                        "已关闭：将搜索全书所有章节（可能包含后续剧情）"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.secondaryText
                                )
                            }
                            Switch(
                                checked = spoilerFreeMode,
                                onCheckedChange = onToggleSpoilerFree,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = palette.accent
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    when {
                        isSearching -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = palette.accent)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "正在流式扫描章节…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.secondaryText
                                    )
                                }
                            }
                        }
                        !hasExecutedSearch -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "输入关键词后点击“搜索”，支持毫秒级流式检索与一键直达对应页码。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.secondaryText
                                )
                            }
                        }
                        results.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (spoilerFreeMode) {
                                        "在当前已读进度内未找到“$query”；可尝试关闭“防剧透模式”检索全书。"
                                    } else {
                                        "全书中未找到包含“$query”的匹配结果。"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.secondaryText
                                )
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "共找到 ${results.size} 处匹配",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.secondaryText
                                )
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(
                                    items = results,
                                    key = { "${it.chapterIndex}:${it.matchStartOffset}" }
                                ) { hit ->
                                    SearchHitCard(
                                        hit = hit,
                                        palette = palette,
                                        onClick = { onSelectHit(hit) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitCard(
    hit: SearchHit,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    val highlightedSnippet = remember(hit.snippet, hit.highlightStartInSnippet, hit.highlightEndInSnippet) {
        buildAnnotatedString {
            append(hit.snippet)
            val start = hit.highlightStartInSnippet.coerceIn(0, hit.snippet.length)
            val end = hit.highlightEndInSnippet.coerceIn(start, hit.snippet.length)
            if (end > start) {
                addStyle(
                    SpanStyle(
                        color = palette.accent,
                        background = palette.accent.copy(alpha = 0.16f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    start,
                    end
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = hit.chapterTitle,
            style = MaterialTheme.typography.labelMedium,
            color = palette.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = highlightedSnippet,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.primaryText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
