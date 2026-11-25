package com.inktrail.reader.domain.usecase

import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.repository.AnnotationRepository
import com.inktrail.reader.engine.search.ReadingAnchor
import com.inktrail.reader.engine.search.SpoilerFreeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Coordinates highlight intervals, notes, bookmarks, and spoiler-free filtering of user notes.
 */
class ManageAnnotationUseCase @Inject constructor(
    private val annotationRepository: AnnotationRepository
) {

    fun observeChapterHighlights(bookId: String, chapterIndex: Int): Flow<List<HighlightRange>> =
        annotationRepository.observeChapterHighlights(bookId, chapterIndex)

    fun observeVisibleAnnotations(
        bookId: String,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean
    ): Flow<List<HighlightRange>> =
        annotationRepository.observeAllAnnotations(bookId).map { items ->
            SpoilerFreeFilter.filter(
                items = items,
                anchor = anchor,
                spoilerFreeMode = spoilerFreeMode,
                chapterIndexOf = { it.chapterIndex },
                endOffsetOf = { it.endOffset }
            )
        }

    fun observeVisibleBookmarks(
        bookId: String,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean
    ): Flow<List<HighlightRange>> =
        annotationRepository.observeBookmarks(bookId).map { items ->
            SpoilerFreeFilter.filter(
                items = items,
                anchor = anchor,
                spoilerFreeMode = spoilerFreeMode,
                chapterIndexOf = { it.chapterIndex },
                endOffsetOf = { it.endOffset }
            )
        }

    suspend fun addOrMergeHighlight(
        bookId: String,
        chapterIndex: Int,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        colorHex: Long,
        noteContent: String? = null
    ) {
        annotationRepository.addOrMergeHighlight(
            bookId = bookId,
            chapterIndex = chapterIndex,
            startOffset = startOffset,
            endOffset = endOffset,
            selectedText = selectedText,
            colorHex = colorHex,
            noteContent = noteContent
        )
    }

    suspend fun eraseHighlightRange(
        bookId: String,
        chapterIndex: Int,
        eraseStart: Int,
        eraseEnd: Int
    ) {
        annotationRepository.eraseHighlightRange(bookId, chapterIndex, eraseStart, eraseEnd)
    }

    suspend fun updateNote(annotationId: Long, noteContent: String?) {
        annotationRepository.updateNote(annotationId, noteContent)
    }

    suspend fun deleteAnnotation(annotationId: Long) {
        annotationRepository.deleteAnnotation(annotationId)
    }

    suspend fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        previewText: String
    ): Boolean = annotationRepository.toggleBookmark(
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        previewText = previewText
    )
}
