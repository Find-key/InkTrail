package com.inktrail.reader.domain.usecase

import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.engine.cleaner.TextCleaner
import com.inktrail.reader.engine.cleaner.TextCleanerOptions
import com.inktrail.reader.engine.search.BookSearchEngine
import com.inktrail.reader.engine.search.ReadingAnchor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchInBookUseCaseTest {

    @Test
    fun `streams search with text cleaner alignment and skips future chapters in spoiler free mode`() = runTest {
        val book = Book(
            id = "b1",
            title = "测试",
            filePath = "/tmp/b1.txt",
            fileSizeBytes = 1000L,
            charsetName = "UTF-8",
            totalChapters = 3,
            currentChapterIndex = 1,
            currentCharOffset = 20,
            lastReadAt = 0L,
            createdAt = 0L
        )
        val ch0 = Chapter(1L, "b1", 0, "第一章", 0L, 100L)
        val ch1 = Chapter(2L, "b1", 1, "第二章", 100L, 200L)
        val ch2 = Chapter(3L, "b1", 2, "第三章", 200L, 300L)

        // Note hard line break inside "墨辙\n密钥" -> TextCleaner joins it into "墨辙密钥"
        val raw0 = "发现墨辙\n密钥。"
        val raw1 = "再次看到墨辙密钥，后面还有墨辙密钥。"
        val raw2 = "第三章最终墨辙密钥。"

        val repo = mockk<BookRepository>()
        coEvery { repo.getChapters("b1") } returns listOf(ch0, ch1, ch2)
        coEvery { repo.readChapterRawText(book, ch0) } returns raw0
        coEvery { repo.readChapterRawText(book, ch1) } returns raw1
        coEvery { repo.readChapterRawText(book, ch2) } returns raw2

        val useCase = SearchInBookUseCase(
            bookRepository = repo,
            textCleaner = TextCleaner(),
            searchEngine = BookSearchEngine(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val cleaned1 = TextCleaner().clean(raw1, TextCleanerOptions())
        val firstMatchEndInCh1 = cleaned1.indexOf("墨辙密钥") + 4
        val anchor = ReadingAnchor(chapterIndex = 1, charOffset = firstMatchEndInCh1)

        val spoilerFreeHits = useCase(
            book = book,
            query = "墨辙密钥",
            anchor = anchor,
            spoilerFreeMode = true,
            cleanerOptions = TextCleanerOptions()
        )

        assertEquals(2, spoilerFreeHits.size)
        assertEquals(0, spoilerFreeHits[0].chapterIndex)
        assertEquals(1, spoilerFreeHits[1].chapterIndex)
        // Chapter 2 must never be read from disk when spoiler-free mode is active
        coVerify(exactly = 0) { repo.readChapterRawText(book, ch2) }

        val fullHits = useCase(
            book = book,
            query = "墨辙密钥",
            anchor = anchor,
            spoilerFreeMode = false,
            cleanerOptions = TextCleanerOptions()
        )
        assertEquals(4, fullHits.size)
    }
}
