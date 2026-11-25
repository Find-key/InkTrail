package com.inktrail.reader.domain.usecase

import com.inktrail.reader.di.IoDispatcher
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.engine.cleaner.TextCleaner
import com.inktrail.reader.engine.cleaner.TextCleanerOptions
import com.inktrail.reader.engine.search.BookSearchEngine
import com.inktrail.reader.engine.search.ReadingAnchor
import com.inktrail.reader.engine.search.SearchHit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Streams chapter-by-chapter keyword search across a book without loading the entire file
 * into memory, enforcing [ReadingAnchor] anti-spoiler rules when [spoilerFreeMode] is active.
 */
class SearchInBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val textCleaner: TextCleaner,
    private val searchEngine: BookSearchEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        book: Book,
        query: String,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean,
        cleanerOptions: TextCleanerOptions,
        maxResults: Int = BookSearchEngine.DEFAULT_MAX_RESULTS
    ): List<SearchHit> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || maxResults <= 0) return@withContext emptyList()

        val chapters = bookRepository.getChapters(book.id)
        val hits = ArrayList<SearchHit>()

        for (chapter in chapters) {
            ensureActive()
            if (spoilerFreeMode && chapter.chapterIndex > anchor.chapterIndex) {
                break
            }

            val raw = bookRepository.readChapterRawText(book, chapter)
            if (raw.isEmpty()) continue
            val cleaned = textCleaner.clean(raw, cleanerOptions)
            if (cleaned.isEmpty()) continue

            val chapterHits = searchEngine.searchInChapter(
                chapterIndex = chapter.chapterIndex,
                chapterTitle = chapter.title,
                chapterText = cleaned,
                query = trimmed,
                anchor = anchor,
                spoilerFreeMode = spoilerFreeMode
            )
            for (hit in chapterHits) {
                hits += hit
                if (hits.size >= maxResults) return@withContext hits
            }
        }
        hits
    }
}
