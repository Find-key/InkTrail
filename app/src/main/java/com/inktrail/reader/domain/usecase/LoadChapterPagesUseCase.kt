package com.inktrail.reader.domain.usecase

import com.inktrail.reader.di.DefaultDispatcher
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.PageSlice
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.engine.cleaner.TextCleaner
import com.inktrail.reader.engine.cleaner.TextCleanerOptions
import com.inktrail.reader.engine.paginator.MeasureCallback
import com.inktrail.reader.engine.paginator.Paginator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoadedChapterContent(
    val chapter: Chapter,
    val rawText: String,
    val cleanedText: String,
    val pages: List<PageSlice>,
    val initialPageIndex: Int
)

/**
 * Loads a chapter slice from disk via byte offsets, applies typography cleaning,
 * and paginates it while keeping the anchor offset on screen.
 */
class LoadChapterPagesUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val textCleaner: TextCleaner,
    private val paginator: Paginator,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    suspend fun loadCleanChapterText(
        book: Book,
        chapter: Chapter,
        cleanerOptions: TextCleanerOptions
    ): Pair<String, String> {
        val rawText = bookRepository.readChapterRawText(book, chapter)
        val cleanedText = withContext(defaultDispatcher) {
            textCleaner.clean(rawText, cleanerOptions)
        }
        return rawText to cleanedText
    }

    fun cleanText(
        rawText: String,
        cleanerOptions: TextCleanerOptions
    ): String = textCleaner.clean(rawText, cleanerOptions)

    fun paginate(
        cleanedText: String,
        anchorCharOffset: Int,
        measureCallback: MeasureCallback
    ): Pair<List<PageSlice>, Int> {
        val pages = paginator.paginate(cleanedText, measureCallback)
        val targetPage = paginator.findPageIndexForOffset(pages, anchorCharOffset)
        return pages to targetPage
    }

    suspend operator fun invoke(
        book: Book,
        chapter: Chapter,
        cleanerOptions: TextCleanerOptions,
        anchorCharOffset: Int,
        measureCallback: MeasureCallback
    ): LoadedChapterContent {
        val (rawText, cleanedText) = loadCleanChapterText(book, chapter, cleanerOptions)
        val (pages, initialPage) = withContext(defaultDispatcher) {
            paginate(cleanedText, anchorCharOffset, measureCallback)
        }
        return LoadedChapterContent(
            chapter = chapter,
            rawText = rawText,
            cleanedText = cleanedText,
            pages = pages,
            initialPageIndex = initialPage
        )
    }
}
