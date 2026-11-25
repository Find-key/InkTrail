package com.inktrail.reader.engine.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerFreeFilterTest {

    private val searchEngine = BookSearchEngine()

    @Test
    fun `enforces strict boundary at current chapter and char offset`() {
        val anchor = ReadingAnchor(chapterIndex = 5, charOffset = 100)

        // Prior chapter: always allowed regardless of offset
        assertTrue(SpoilerFreeFilter.isAllowed(4, 9999, anchor, spoilerFreeMode = true))

        // Same chapter boundary: matchEndOffset == charOffset is allowed, +1 is blocked
        assertTrue(SpoilerFreeFilter.isAllowed(5, 100, anchor, spoilerFreeMode = true))
        assertFalse(SpoilerFreeFilter.isAllowed(5, 101, anchor, spoilerFreeMode = true))

        // Future chapter: strictly blocked
        assertFalse(SpoilerFreeFilter.isAllowed(6, 1, anchor, spoilerFreeMode = true))
        assertFalse(SpoilerFreeFilter.isAllowed(6, 0, anchor, spoilerFreeMode = true))

        // When spoilerFreeMode is off, all matches are allowed
        assertTrue(SpoilerFreeFilter.isAllowed(5, 101, anchor, spoilerFreeMode = false))
        assertTrue(SpoilerFreeFilter.isAllowed(6, 500, anchor, spoilerFreeMode = false))
    }

    @Test
    fun `filters arbitrary item collections by reading anchor`() {
        data class DummyAnnotation(val chapter: Int, val endOffset: Int, val label: String)

        val items = listOf(
            DummyAnnotation(0, 500, "ch0"),
            DummyAnnotation(2, 120, "ch2-exact"),
            DummyAnnotation(2, 121, "ch2-spoiler"),
            DummyAnnotation(3, 10, "ch3-spoiler")
        )
        val anchor = ReadingAnchor(chapterIndex = 2, charOffset = 120)

        val filtered = SpoilerFreeFilter.filter(
            items = items,
            anchor = anchor,
            spoilerFreeMode = true,
            chapterIndexOf = { it.chapter },
            endOffsetOf = { it.endOffset }
        )

        assertEquals(listOf("ch0", "ch2-exact"), filtered.map { it.label })
    }

    @Test
    fun `book search excludes ahead-of-cursor and future-chapter hits without loading future chapters`() {
        val ch0Text = "第一章：秘密刚刚浮现。"
        val ch1Text = "第二章：前方的秘密刚刚揭晓，然而真正的秘密还在后面。"
        // "秘密" occurrences in ch1Text:
        val firstInCh1Start = ch1Text.indexOf("秘密")
        val firstInCh1End = firstInCh1Start + 2
        val secondInCh1Start = ch1Text.indexOf("秘密", startIndex = firstInCh1End)

        // Anchor is placed right after the first "秘密" in ch1, before the second "秘密"
        val anchor = ReadingAnchor(chapterIndex = 1, charOffset = firstInCh1End)

        var futureChapterLoaded = false
        val chapters = sequenceOf(
            ChapterTextSource(0, "第一章") { ch0Text },
            ChapterTextSource(1, "第二章") { ch1Text },
            ChapterTextSource(2, "第三章") {
                futureChapterLoaded = true
                "第三章：终极秘密彻底曝光。"
            }
        )

        val spoilerFreeHits = searchEngine.searchBook(
            chapters = chapters,
            query = "秘密",
            anchor = anchor,
            spoilerFreeMode = true
        )

        assertFalse("Future chapters must not even be read in spoiler-free mode", futureChapterLoaded)
        assertEquals(2, spoilerFreeHits.size)
        assertEquals(0, spoilerFreeHits[0].chapterIndex)
        assertEquals(1, spoilerFreeHits[1].chapterIndex)
        assertEquals(firstInCh1Start, spoilerFreeHits[1].matchStartOffset)
        assertTrue(spoilerFreeHits.none { it.chapterIndex == 1 && it.matchStartOffset >= secondInCh1Start })

        // Verify snippet highlight range maps directly to the matched query
        for (hit in spoilerFreeHits) {
            assertEquals("秘密", hit.snippet.substring(hit.highlightStartInSnippet, hit.highlightEndInSnippet))
        }

        // Turning off spoiler-free mode finds all 4 occurrences across all 3 chapters
        val fullHits = searchEngine.searchBook(
            chapters = chapters,
            query = "秘密",
            anchor = anchor,
            spoilerFreeMode = false
        )
        assertTrue(futureChapterLoaded)
        assertEquals(4, fullHits.size)
    }

    @Test
    fun `blocks match straddling across the reading anchor offset`() {
        val text = "前面内容，关键线索在这里。"
        val matchStart = text.indexOf("关键线索")
        val matchEnd = matchStart + 4
        // Cursor is right inside "关键线索" (endOffset > charOffset) -> must not leak!
        val anchorMidWord = ReadingAnchor(chapterIndex = 0, charOffset = matchEnd - 1)

        val hits = searchEngine.searchInChapter(
            chapterIndex = 0,
            chapterTitle = "第一章",
            chapterText = text,
            query = "关键线索",
            anchor = anchorMidWord,
            spoilerFreeMode = true
        )

        assertTrue(hits.isEmpty())
    }
}
