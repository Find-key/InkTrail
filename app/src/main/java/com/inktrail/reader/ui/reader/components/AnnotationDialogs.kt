package com.inktrail.reader.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.ui.reader.TextSelectionState
import com.inktrail.reader.ui.theme.HighlightAmber
import com.inktrail.reader.ui.theme.HighlightRose
import com.inktrail.reader.ui.theme.HighlightSage
import com.inktrail.reader.ui.theme.HighlightSky
import com.inktrail.reader.ui.theme.ReaderPalette

val HighlightColorChoices = listOf(
    HighlightAmber to "暖黄",
    HighlightSage to "浅绿",
    HighlightSky to "晴蓝",
    HighlightRose to "绯粉"
)

@Composable
fun TextSelectionActionDialog(
    selection: TextSelectionState,
    hasOverlapWithExisting: Boolean,
    palette: ReaderPalette,
    onSaveHighlight: (colorHex: Long, noteContent: String?) -> Unit,
    onEraseSelection: () -> Unit,
    onExpandToSentence: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(HighlightAmber) }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.primaryText,
        textContentColor = palette.primaryText,
        title = { Text("划线与批注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = palette.background,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${selection.selectedText}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "高亮颜色",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryText
                    )
                    TextButton(onClick = onExpandToSentence) {
                        Text("扩展至整句", color = palette.accent)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightColorChoices.forEach { (hex, label) ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) palette.accent else palette.divider,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = label,
                                    tint = Color(0xFF1D2126),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("添加批注（可选）") },
                    placeholder = { Text("写下想法或人物线索…") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.primaryText,
                        unfocusedTextColor = palette.primaryText,
                        cursorColor = palette.accent,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.divider,
                        focusedLabelColor = palette.accent,
                        unfocusedLabelColor = palette.secondaryText,
                        focusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasOverlapWithExisting) {
                    OutlinedButton(
                        onClick = onEraseSelection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("擦除选中区间高亮")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveHighlight(
                        selectedColor,
                        noteText.trim().takeIf { it.isNotEmpty() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text(if (noteText.isBlank()) "划线高亮" else "保存批注")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = palette.secondaryText)
            }
        }
    )
}

@Composable
fun EditAnnotationDialog(
    annotation: HighlightRange,
    palette: ReaderPalette,
    onSaveNote: (noteContent: String?) -> Unit,
    onDeleteAnnotation: () -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(annotation.id) {
        mutableStateOf(annotation.noteContent.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.primaryText,
        textContentColor = palette.primaryText,
        title = { Text(if (annotation.isBookmark) "编辑书签备注" else "查看 / 编辑批注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = palette.background,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = annotation.selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("批注内容") },
                    placeholder = { Text("输入或清空笔记…") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.primaryText,
                        unfocusedTextColor = palette.primaryText,
                        cursorColor = palette.accent,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.divider,
                        focusedLabelColor = palette.accent,
                        unfocusedLabelColor = palette.secondaryText,
                        focusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = palette.secondaryText.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = onDeleteAnnotation,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除该条标注", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveNote(noteText.trim().takeIf { it.isNotEmpty() }) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = palette.secondaryText)
            }
        }
    )
}
