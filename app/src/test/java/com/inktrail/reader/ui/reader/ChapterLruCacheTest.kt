package com.inktrail.reader.ui.reader

import com.inktrail.reader.domain.model.PageSlice
import com.inktrail.reader.engine.cleaner.TextCleanerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterLruCacheTest {

    @Test
    fun `evicts least recently accessed chapter when exceeding max capacity of 3`() {
        val cache = ChapterLruCache(maxCapacity = 3)
        cache.put(entry(0))
        cache.put(entry(1))
        cache.put(entry(2))

        // Touch chapter 0 so chapter 1 becomes the least recently used entry
        cache.get(0)

        cache.put(entry(3))

        assertEquals(3, cache.size())
        assertNull(cache.peek(1))
        assertEquals(setOf(0, 2, 3), cache.cachedIndices())
    }

    private fun entry(index: Int): CachedChapter = CachedChapter(
        chapterIndex = index,
        title = "第${index + 1}章",
        rawText = "raw-$index",
        cleanedText = "clean-$index",
        cleanerOptions = TextCleanerOptions(),
        paginationSpec = null,
        pages = listOf(PageSlice(0, 0, 5, "clean"))
    )
}
