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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.ui.theme.ReaderPalette

private enum class AnnotationTab(val label: String) {
    ALL("全部"),
    HIGHLIGHTS("划线笔记"),
    BOOKMARKS("书签")
}

@Composable
fun AnnotationsListSheet(
    visible: Boolean,
    annotations: List<HighlightRange>,
    chapters: List<Chapter>,
    spoilerFreeMode: Boolean,
    palette: ReaderPalette,
    onToggleSpoilerFree: (Boolean) -> Unit,
    onJumpToAnnotation: (HighlightRange) -> Unit,
    onEditAnnotation: (HighlightRange) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AnnotationTab.ALL) }
    val filteredList = remember(annotations, selectedTab) {
        when (selectedTab) {
            AnnotationTab.ALL -> annotations
            AnnotationTab.HIGHLIGHTS -> annotations.filter { !it.isBookmark }
            AnnotationTab.BOOKMARKS -> annotations.filter { it.isBookmark }
        }
    }

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
                    .fillMaxHeight(0.86f)
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
                            text = "书签与批注管理",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryText
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭列表",
                                tint = palette.primaryText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnnotationTab.entries.forEach { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                label = { Text(tab.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                                    selectedLabelColor = palette.accent,
                                    labelColor = palette.secondaryText
                                )
                            )
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "防剧透过滤（隐藏后续章节笔记/书签）",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.primaryText,
                                modifier = Modifier.weight(1f)
                            )
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

                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无符合条件的书签或划线批注；长按正文即可选段划线并记录笔记。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.secondaryText
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredList, key = { it.id }) { item ->
                                val chapterTitle = chapters.getOrNull(item.chapterIndex)?.title
                                    ?: "第 ${item.chapterIndex + 1} 章"
                                AnnotationEntryCard(
                                    item = item,
                                    chapterTitle = chapterTitle,
                                    palette = palette,
                                    onClick = { onJumpToAnnotation(item) },
                                    onEdit = { onEditAnnotation(item) },
                                    onDelete = { onDeleteAnnotation(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationEntryCard(
    item: HighlightRange,
    chapterTitle: String,
    palette: ReaderPalette,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isBookmark) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(item.colorHex))
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    tint = palette.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = palette.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (item.selectedText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.selectedText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primaryText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!item.noteContent.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "批注：${item.noteContent}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
