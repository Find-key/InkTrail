package com.inktrail.reader.domain.model

/**
 * Domain entity representing a highlight span, text note, or bookmark in a book chapter.
 */
data class HighlightRange(
    val id: Long = 0L,
    val bookId: String,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val noteContent: String? = null,
    val colorHex: Long,
    val isBookmark: Boolean = false,
    val createdAt: Long = 0L
)
