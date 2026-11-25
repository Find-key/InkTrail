package com.inktrail.reader.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.ui.theme.ReaderPalette
import kotlinx.coroutines.launch

@Composable
fun ChapterDrawerSheet(
    visible: Boolean,
    bookTitle: String,
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    palette: ReaderPalette,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var reversedOrder by remember { mutableStateOf(false) }
    val displayChapters = remember(chapters, reversedOrder) {
        if (reversedOrder) chapters.asReversed() else chapters
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun scrollToCurrentChapter() {
        val targetListIndex = displayChapters.indexOfFirst { it.chapterIndex == currentChapterIndex }
        if (targetListIndex >= 0) {
            coroutineScope.launch {
                listState.scrollToItem((targetListIndex - 3).coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(visible, currentChapterIndex, reversedOrder) {
        if (visible && displayChapters.isNotEmpty()) {
            val targetListIndex = displayChapters.indexOfFirst { it.chapterIndex == currentChapterIndex }
            if (targetListIndex >= 0) {
                listState.scrollToItem((targetListIndex - 3).coerceAtLeast(0))
            }
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
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                color = palette.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 360.dp)
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
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "共 ${chapters.size} 章 · 当前第 ${currentChapterIndex + 1} 章",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryText
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭目录",
                                tint = palette.primaryText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { scrollToCurrentChapter() }) {
                            Icon(
                                imageVector = Icons.Outlined.MyLocation,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("定位当前章", color = palette.accent)
                        }
                        TextButton(onClick = { reversedOrder = !reversedOrder }) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = null,
                                tint = palette.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (reversedOrder) "正序" else "倒序",
                                color = palette.secondaryText
                            )
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(displayChapters, key = { it.chapterIndex }) { chapter ->
                            val isCurrent = chapter.chapterIndex == currentChapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(chapter.chapterIndex) }
                                    .background(
                                        if (isCurrent) palette.accent.copy(alpha = 0.10f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 18.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(palette.accent)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isCurrent) palette.accent else palette.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.accent
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
