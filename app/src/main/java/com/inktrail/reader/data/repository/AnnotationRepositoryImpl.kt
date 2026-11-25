package com.inktrail.reader.data.repository

import com.inktrail.reader.data.local.db.AnnotationDao
import com.inktrail.reader.data.local.db.AnnotationEntity
import com.inktrail.reader.data.local.db.toDomain
import com.inktrail.reader.di.IoDispatcher
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.repository.AnnotationRepository
import com.inktrail.reader.engine.annotation.HighlightInterval
import com.inktrail.reader.engine.annotation.HighlightIntervalManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val intervalManager: HighlightIntervalManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AnnotationRepository {

    override fun observeAllAnnotations(bookId: String): Flow<List<HighlightRange>> =
        annotationDao.observeAnnotationsForBook(bookId).map { list -> list.map { it.toDomain() } }

    override fun observeChapterHighlights(
        bookId: String,
        chapterIndex: Int
    ): Flow<List<HighlightRange>> =
        annotationDao.observeHighlightsForChapter(bookId, chapterIndex)
            .map { list -> list.map { it.toDomain() } }

    override fun observeBookmarks(bookId: String): Flow<List<HighlightRange>> =
        annotationDao.observeBookmarksForBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun addOrMergeHighlight(
        bookId: String,
        chapterIndex: Int,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        colorHex: Long,
        noteContent: String?
    ) = withContext(ioDispatcher) {
        if (startOffset >= endOffset) return@withContext
        val existingEntities = annotationDao.getHighlightsForChapter(bookId, chapterIndex)
        val existingIntervals = existingEntities.map { it.toInterval() }
        val textById = existingEntities.associate { it.id to it.selectedText }

        val incoming = HighlightInterval(
            id = 0L,
            startOffset = startOffset,
            endOffset = endOffset,
            colorHex = colorHex,
            noteContent = noteContent,
            createdAt = System.currentTimeMillis()
        )
        val mergedIntervals = intervalManager.addOrMerge(existingIntervals, incoming)

        val entitiesToPersist = mergedIntervals.map { interval ->
            val resolvedText = resolveTextForInterval(
                interval = interval,
                incomingStart = startOffset,
                incomingEnd = endOffset,
                incomingText = selectedText,
                existingEntities = existingEntities,
                textById = textById
            )
            AnnotationEntity(
                id = interval.id,
                bookId = bookId,
                chapterIndex = chapterIndex,
                startOffset = interval.startOffset,
                endOffset = interval.endOffset,
                selectedText = resolvedText,
                noteContent = interval.noteContent,
                colorHex = interval.colorHex,
                isBookmark = false,
                createdAt = interval.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }

        annotationDao.replaceChapterHighlights(bookId, chapterIndex, entitiesToPersist)
    }

    override suspend fun eraseHighlightRange(
        bookId: String,
        chapterIndex: Int,
        eraseStart: Int,
        eraseEnd: Int
    ) = withContext(ioDispatcher) {
        if (eraseStart >= eraseEnd) return@withContext
        val existingEntities = annotationDao.getHighlightsForChapter(bookId, chapterIndex)
        if (existingEntities.isEmpty()) return@withContext

        val existingIntervals = existingEntities.map { it.toInterval() }
        val textById = existingEntities.associate { it.id to it.selectedText }
        val remaining = intervalManager.erase(existingIntervals, eraseStart, eraseEnd)

        val entitiesToPersist = remaining.map { interval ->
            val resolvedText = resolveTextForInterval(
                interval = interval,
                incomingStart = -1,
                incomingEnd = -1,
                incomingText = "",
                existingEntities = existingEntities,
                textById = textById
            )
            AnnotationEntity(
                id = interval.id,
                bookId = bookId,
                chapterIndex = chapterIndex,
                startOffset = interval.startOffset,
                endOffset = interval.endOffset,
                selectedText = resolvedText,
                noteContent = interval.noteContent,
                colorHex = interval.colorHex,
                isBookmark = false,
                createdAt = interval.createdAt
            )
        }

        annotationDao.replaceChapterHighlights(bookId, chapterIndex, entitiesToPersist)
    }

    override suspend fun updateNote(annotationId: Long, noteContent: String?) =
        withContext(ioDispatcher) {
            val cleaned = noteContent?.trim()?.takeIf { it.isNotEmpty() }
            annotationDao.updateNoteContent(annotationId, cleaned)
        }

    override suspend fun deleteAnnotation(annotationId: Long) = withContext(ioDispatcher) {
        annotationDao.deleteAnnotationById(annotationId)
    }

    override suspend fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        previewText: String
    ): Boolean = withContext(ioDispatcher) {
        val existing = annotationDao.getBookmarkForChapter(bookId, chapterIndex)
        if (existing != null) {
            annotationDao.deleteAnnotationById(existing.id)
            false
        } else {
            annotationDao.insertAnnotation(
                AnnotationEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    startOffset = charOffset.coerceAtLeast(0),
                    endOffset = charOffset.coerceAtLeast(0),
                    selectedText = previewText,
                    noteContent = null,
                    colorHex = BOOKMARK_COLOR_HEX,
                    isBookmark = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    private fun AnnotationEntity.toInterval(): HighlightInterval = HighlightInterval(
        id = id,
        startOffset = startOffset,
        endOffset = endOffset,
        colorHex = colorHex,
        noteContent = noteContent,
        createdAt = createdAt
    )

    private fun resolveTextForInterval(
        interval: HighlightInterval,
        incomingStart: Int,
        incomingEnd: Int,
        incomingText: String,
        existingEntities: List<AnnotationEntity>,
        textById: Map<Long, String>
    ): String {
        if (interval.startOffset == incomingStart && interval.endOffset == incomingEnd) {
            return incomingText
        }
        val directMatch = existingEntities.firstOrNull {
            it.startOffset == interval.startOffset && it.endOffset == interval.endOffset
        }
        if (directMatch != null) return directMatch.selectedText

        // Reconstruct text slice accurately from overlapping existing entities and incoming span.
        val length = (interval.endOffset - interval.startOffset).coerceAtLeast(0)
        if (length == 0) return ""
        val chars = CharArray(length) { ' ' }

        for (old in existingEntities) {
            val oldText = textById[old.id] ?: old.selectedText
            copySpanIntoBuffer(
                buffer = chars,
                targetStart = interval.startOffset,
                targetEnd = interval.endOffset,
                sourceStart = old.startOffset,
                sourceText = oldText
            )
        }
        if (incomingStart >= 0 && incomingEnd > incomingStart) {
            copySpanIntoBuffer(
                buffer = chars,
                targetStart = interval.startOffset,
                targetEnd = interval.endOffset,
                sourceStart = incomingStart,
                sourceText = incomingText
            )
        }
        return String(chars).trim()
    }

    private fun copySpanIntoBuffer(
        buffer: CharArray,
        targetStart: Int,
        targetEnd: Int,
        sourceStart: Int,
        sourceText: String
    ) {
        val overlapStart = maxOf(targetStart, sourceStart)
        val overlapEnd = minOf(targetEnd, sourceStart + sourceText.length)
        if (overlapStart >= overlapEnd) return
        for (pos in overlapStart until overlapEnd) {
            buffer[pos - targetStart] = sourceText[pos - sourceStart]
        }
    }

    companion object {
        private const val BOOKMARK_COLOR_HEX = 0xFF3E6899L
    }
}
