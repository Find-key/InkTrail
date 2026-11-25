package com.inktrail.reader.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.inktrail.reader.domain.model.HighlightRange

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "chapterIndex"])
    ]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: String,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val noteContent: String?,
    val colorHex: Long,
    val isBookmark: Boolean,
    val createdAt: Long
)

fun AnnotationEntity.toDomain(): HighlightRange = HighlightRange(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    startOffset = startOffset,
    endOffset = endOffset,
    selectedText = selectedText,
    noteContent = noteContent,
    colorHex = colorHex,
    isBookmark = isBookmark,
    createdAt = createdAt
)

fun HighlightRange.toEntity(): AnnotationEntity = AnnotationEntity(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    startOffset = startOffset,
    endOffset = endOffset,
    selectedText = selectedText,
    noteContent = noteContent,
    colorHex = colorHex,
    isBookmark = isBookmark,
    createdAt = createdAt
)
