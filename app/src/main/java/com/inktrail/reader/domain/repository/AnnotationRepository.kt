package com.inktrail.reader.domain.repository

import com.inktrail.reader.domain.model.HighlightRange
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun observeAllAnnotations(bookId: String): Flow<List<HighlightRange>>

    fun observeChapterHighlights(bookId: String, chapterIndex: Int): Flow<List<HighlightRange>>

    fun observeBookmarks(bookId: String): Flow<List<HighlightRange>>

    suspend fun addOrMergeHighlight(
        bookId: String,
        chapterIndex: Int,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        colorHex: Long,
        noteContent: String? = null
    )

    suspend fun eraseHighlightRange(
        bookId: String,
        chapterIndex: Int,
        eraseStart: Int,
        eraseEnd: Int
    )

    suspend fun updateNote(annotationId: Long, noteContent: String?)

    suspend fun deleteAnnotation(annotationId: Long)

    suspend fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        previewText: String
    ): Boolean
}
