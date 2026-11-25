package com.inktrail.reader.data.repository

import com.inktrail.reader.data.local.db.AnnotationDao
import com.inktrail.reader.data.local.db.AnnotationEntity
import com.inktrail.reader.engine.annotation.HighlightIntervalManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationRepositoryImplTest {

    @Test
    fun `merges overlapping highlights and preserves reconstructed selected text`() = runTest {
        val existing = listOf(
            AnnotationEntity(
                id = 1L,
                bookId = "book1",
                chapterIndex = 0,
                startOffset = 0,
                endOffset = 6,
                selectedText = "春风吹过江南",
                noteContent = "笔记1",
                colorHex = 0xFFFFEB3BL,
                isBookmark = false,
                createdAt = 100L
            )
        )
        val dao = mockk<AnnotationDao>(relaxed = true)
        coEvery { dao.getHighlightsForChapter("book1", 0) } returns existing
        val persistedSlot = slot<List<AnnotationEntity>>()
        coEvery { dao.replaceChapterHighlights("book1", 0, capture(persistedSlot)) } just runs

        val repo = AnnotationRepositoryImpl(
            annotationDao = dao,
            intervalManager = HighlightIntervalManager(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        repo.addOrMergeHighlight(
            bookId = "book1",
            chapterIndex = 0,
            startOffset = 4,
            endOffset = 10,
            selectedText = "江南烟雨画舫",
            colorHex = 0xFFFFEB3BL,
            noteContent = null
        )

        assertEquals(1, persistedSlot.captured.size)
        val merged = persistedSlot.captured.single()
        assertEquals(0, merged.startOffset)
        assertEquals(10, merged.endOffset)
        assertEquals("春风吹过江南烟雨画舫", merged.selectedText)
        assertEquals("笔记1", merged.noteContent)
    }

    @Test
    fun `erasing middle span splits entity and slices selected text`() = runTest {
        val existing = listOf(
            AnnotationEntity(
                id = 7L,
                bookId = "book1",
                chapterIndex = 2,
                startOffset = 0,
                endOffset = 10,
                selectedText = "0123456789",
                noteContent = "保留笔记",
                colorHex = 0xFF81C784L,
                isBookmark = false,
                createdAt = 200L
            )
        )
        val dao = mockk<AnnotationDao>(relaxed = true)
        coEvery { dao.getHighlightsForChapter("book1", 2) } returns existing
        val persistedSlot = slot<List<AnnotationEntity>>()
        coEvery { dao.replaceChapterHighlights("book1", 2, capture(persistedSlot)) } just runs

        val repo = AnnotationRepositoryImpl(
            annotationDao = dao,
            intervalManager = HighlightIntervalManager(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        repo.eraseHighlightRange("book1", 2, eraseStart = 3, eraseEnd = 7)

        assertEquals(2, persistedSlot.captured.size)
        assertEquals("012", persistedSlot.captured[0].selectedText)
        assertEquals("789", persistedSlot.captured[1].selectedText)
    }

    @Test
    fun `toggleBookmark creates when absent and deletes when present`() = runTest {
        val dao = mockk<AnnotationDao>(relaxed = true)
        coEvery { dao.getBookmarkForChapter("book1", 3) } returnsMany listOf(
            null,
            AnnotationEntity(
                id = 99L,
                bookId = "book1",
                chapterIndex = 3,
                startOffset = 42,
                endOffset = 42,
                selectedText = "第四章",
                noteContent = null,
                colorHex = 0xFF3E6899L,
                isBookmark = true,
                createdAt = 500L
            )
        )
        coEvery { dao.insertAnnotation(any()) } returns 99L

        val repo = AnnotationRepositoryImpl(
            annotationDao = dao,
            intervalManager = HighlightIntervalManager(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val added = repo.toggleBookmark("book1", 3, 42, "第四章")
        assertTrue(added)
        coVerify(exactly = 1) { dao.insertAnnotation(match { it.isBookmark && it.chapterIndex == 3 }) }

        val removed = repo.toggleBookmark("book1", 3, 42, "第四章")
        assertFalse(removed)
        coVerify(exactly = 1) { dao.deleteAnnotationById(99L) }
    }
}
