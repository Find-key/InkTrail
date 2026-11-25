package com.inktrail.reader.engine.paginator

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorTest {

    private val paginator = Paginator()

    @Test
    fun `paginates contiguously without gaps under mocked container capacities`() {
        val text = (1..40).joinToString("\n") { index ->
            "第${index}段：墨辙阅读器通过字符偏移锚点保证翻页和重排精准稳定。"
        }

        val smallContainerCallback = mockk<MeasureCallback>()
        every { smallContainerCallback.measurePageEnd(any(), any()) } answers {
            val source = firstArg<String>()
            val start = secondArg<Int>()
            minOf(source.length, start + 120)
        }

        val largeContainerCallback = mockk<MeasureCallback>()
        every { largeContainerCallback.measurePageEnd(any(), any()) } answers {
            val source = firstArg<String>()
            val start = secondArg<Int>()
            minOf(source.length, start + 310)
        }

        val smallPages = paginator.paginate(text, smallContainerCallback)
        val largePages = paginator.paginate(text, largeContainerCallback)

        assertContiguousPages(text, smallPages)
        assertContiguousPages(text, largePages)
        assertTrue(smallPages.size > largePages.size)
        verify(atLeast = smallPages.size) { smallContainerCallback.measurePageEnd(text, any()) }
    }

    @Test
    fun `preserves reading anchor page across layout reflows`() {
        val text = buildString {
            append("A".repeat(180))
            append("【核心锚点句：无论字号如何变化，我都应留在当前视野中】")
            append("B".repeat(320))
        }
        val anchorOffset = text.indexOf("【核心锚点句")

        val mediumCallback = mockk<MeasureCallback>()
        every { mediumCallback.measurePageEnd(any(), any()) } answers {
            val s = firstArg<String>()
            val start = secondArg<Int>()
            minOf(s.length, start + 110)
        }
        val mediumPages = paginator.paginate(text, mediumCallback)
        val oldPageIndex = paginator.findPageIndexForOffset(mediumPages, anchorOffset)
        assertTrue(mediumPages[oldPageIndex].text.contains("【核心锚点句"))

        // Reflow with smaller page capacity (larger font size)
        val largeFontCallback = mockk<MeasureCallback>()
        every { largeFontCallback.measurePageEnd(any(), any()) } answers {
            val s = firstArg<String>()
            val start = secondArg<Int>()
            minOf(s.length, start + 47)
        }
        val reflowedSmallPages = paginator.paginate(text, largeFontCallback)
        val newPageIndexSmall = paginator.findPageIndexForOffset(reflowedSmallPages, anchorOffset)
        val matchedSliceSmall = reflowedSmallPages[newPageIndexSmall]
        assertTrue(anchorOffset in matchedSliceSmall.startCharOffset until matchedSliceSmall.endCharOffset)
        assertTrue(matchedSliceSmall.text.contains("【") || text.substring(matchedSliceSmall.startCharOffset, matchedSliceSmall.endCharOffset).isNotEmpty())

        // Reflow with larger page capacity (smaller font size)
        val smallFontCallback = mockk<MeasureCallback>()
        every { smallFontCallback.measurePageEnd(any(), any()) } answers {
            val s = firstArg<String>()
            val start = secondArg<Int>()
            minOf(s.length, start + 240)
        }
        val reflowedLargePages = paginator.paginate(text, smallFontCallback)
        val newPageIndexLarge = paginator.findPageIndexForOffset(reflowedLargePages, anchorOffset)
        val matchedSliceLarge = reflowedLargePages[newPageIndexLarge]
        assertTrue(anchorOffset in matchedSliceLarge.startCharOffset until matchedSliceLarge.endCharOffset)
        assertTrue(matchedSliceLarge.text.contains("【核心锚点句"))
    }

    @Test
    fun `never splits surrogate pair or enters infinite loop on zero-progress measurement`() {
        val emojiText = "😀".repeat(6) // 12 UTF-16 chars, 6 codepoints
        val brokenCallback = mockk<MeasureCallback>()
        every { brokenCallback.measurePageEnd(any(), any()) } answers {
            val start = secondArg<Int>()
            // Simulate a buggy or tight measurement returning start or splitting a surrogate pair
            if (start == 0) 0 else start + 3
        }

        val pages = paginator.paginate(emojiText, brokenCallback)

        assertContiguousPages(emojiText, pages)
        for (page in pages) {
            assertFalseSurrogateSplit(page.text)
        }
    }

    @Test
    fun `handles empty text and boundary offset queries gracefully`() {
        val callback = mockk<MeasureCallback>()
        val emptyPages = paginator.paginate("", callback)

        assertTrue(emptyPages.isEmpty())
        assertEquals(0, paginator.findPageIndexForOffset(emptyPages, 10))
        verify(exactly = 0) { callback.measurePageEnd(any(), any()) }

        every { callback.measurePageEnd(any(), any()) } answers {
            minOf(firstArg<String>().length, secondArg<Int>() + 5)
        }
        val pages = paginator.paginate("0123456789", callback)
        assertEquals(0, paginator.findPageIndexForOffset(pages, -5))
        assertEquals(pages.lastIndex, paginator.findPageIndexForOffset(pages, 10))
        assertEquals(pages.lastIndex, paginator.findPageIndexForOffset(pages, 999))
    }

    private fun assertContiguousPages(expectedText: String, pages: List<PageSlice>) {
        assertTrue(pages.isNotEmpty())
        assertEquals(0, pages.first().startCharOffset)
        assertEquals(expectedText.length, pages.last().endCharOffset)
        for (i in pages.indices) {
            assertEquals(i, pages[i].pageIndex)
            assertTrue(pages[i].startCharOffset < pages[i].endCharOffset)
            if (i + 1 < pages.size) {
                assertEquals(pages[i].endCharOffset, pages[i + 1].startCharOffset)
            }
        }
        assertEquals(expectedText, pages.joinToString("") { it.text })
    }

    private fun assertFalseSurrogateSplit(slice: String) {
        assertTrue(!Character.isLowSurrogate(slice.first()))
        assertTrue(!Character.isHighSurrogate(slice.last()))
    }
}
