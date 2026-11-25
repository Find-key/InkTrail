package com.inktrail.reader.domain.usecase

import com.inktrail.reader.data.local.storage.BookFileStorage
import com.inktrail.reader.data.local.storage.CopiedBookFile
import com.inktrail.reader.data.local.storage.EmptyBookException
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.ImportProgress
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.engine.encoding.CharsetDetector
import com.inktrail.reader.engine.splitter.ChapterSplitter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

@OptIn(ExperimentalCoroutinesApi::class)
class ImportBookUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `emits full progress sequence and persists detected chapters for gbk book`() = runTest {
        val content = "第一章 开篇\n雨夜点灯。\n第二章 远行\n天明出发。"
        val gbkBytes = content.toByteArray(Charset.forName("GBK"))
        val cachedFile = tempFolder.newFile("imported.txt").apply { writeBytes(gbkBytes) }

        val storage = mockk<BookFileStorage>()
        every { storage.copyFromUri(any(), any(), any()) } answers {
            val onProgress = thirdArg<(Long, Long?) -> Unit>()
            onProgress(gbkBytes.size.toLong(), gbkBytes.size.toLong())
            CopiedBookFile(
                title = "测试小说",
                filePath = cachedFile.absolutePath,
                fileSizeBytes = gbkBytes.size.toLong()
            )
        }
        every { storage.openInputStream(cachedFile.absolutePath) } answers {
            cachedFile.inputStream().buffered()
        }
        every { storage.deleteBookFile(any()) } returns true

        val bookSlot = slot<Book>()
        val chaptersSlot = slot<List<Chapter>>()
        val repository = mockk<BookRepository>()
        coEvery { repository.saveImportedBook(capture(bookSlot), capture(chaptersSlot)) } just runs

        val useCase = ImportBookUseCase(
            bookFileStorage = storage,
            bookRepository = repository,
            charsetDetector = CharsetDetector(),
            chapterSplitter = ChapterSplitter(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val events = useCase("content://docs/test.txt").toList()

        assertTrue(events.any { it is ImportProgress.Copying })
        assertTrue(events.any { it is ImportProgress.DetectingEncoding })
        assertTrue(events.any { it is ImportProgress.IndexingChapters })
        assertTrue(events.any { it is ImportProgress.SavingToDatabase })
        val completed = events.last() as ImportProgress.Completed

        assertEquals("测试小说", completed.book.title)
        assertEquals(2, completed.book.totalChapters)
        assertTrue(completed.book.charsetName == "GB18030" || completed.book.charsetName == "GBK")
        assertEquals(2, chaptersSlot.captured.size)
        assertEquals("第一章 开篇", chaptersSlot.captured[0].title)
        assertEquals("第二章 远行", chaptersSlot.captured[1].title)
        assertEquals(0L, chaptersSlot.captured[0].startByteOffset)
        assertEquals(gbkBytes.size.toLong(), chaptersSlot.captured[1].endByteOffset)
    }

    @Test
    fun `cleans up copied file and emits Failed when storage throws EmptyBookException`() = runTest {
        val storage = mockk<BookFileStorage>()
        every { storage.copyFromUri(any(), any(), any()) } throws EmptyBookException("空文件")
        every { storage.deleteBookFile(any()) } returns true
        val repository = mockk<BookRepository>(relaxed = true)

        val useCase = ImportBookUseCase(
            bookFileStorage = storage,
            bookRepository = repository,
            charsetDetector = CharsetDetector(),
            chapterSplitter = ChapterSplitter(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val events = useCase("content://docs/empty.txt").toList()

        val last = events.last()
        assertTrue(last is ImportProgress.Failed)
        assertEquals("空文件", (last as ImportProgress.Failed).message)
        coVerify(exactly = 0) { repository.saveImportedBook(any(), any()) }
    }

    @Test
    fun `deletes copied file on failure during indexing`() = runTest {
        val emptyCached = tempFolder.newFile("empty_after_copy.txt")
        val storage = mockk<BookFileStorage>()
        every { storage.copyFromUri(any(), any(), any()) } returns CopiedBookFile(
            title = "坏文件",
            filePath = emptyCached.absolutePath,
            fileSizeBytes = 0L
        )
        every { storage.openInputStream(emptyCached.absolutePath) } answers {
            emptyCached.inputStream()
        }
        every { storage.deleteBookFile(emptyCached.absolutePath) } returns true
        val repository = mockk<BookRepository>(relaxed = true)

        val useCase = ImportBookUseCase(
            bookFileStorage = storage,
            bookRepository = repository,
            charsetDetector = CharsetDetector(),
            chapterSplitter = ChapterSplitter(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val events = useCase("content://docs/zero.txt").toList()

        assertTrue(events.last() is ImportProgress.Failed)
        verify(exactly = 1) { storage.deleteBookFile(emptyCached.absolutePath) }
        coVerify(exactly = 0) { repository.saveImportedBook(any(), any()) }
    }
}
