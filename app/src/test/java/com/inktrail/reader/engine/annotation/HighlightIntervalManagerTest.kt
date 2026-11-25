package com.inktrail.reader.engine.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightIntervalManagerTest {

    private val manager = HighlightIntervalManager()
    private val yellow = 0xFFFFEB3BL
    private val green = 0xFF81C784L

    @Test
    fun `merges overlapping same-color intervals`() {
        val existing = listOf(
            HighlightInterval(id = 1L, startOffset = 10, endOffset = 25, colorHex = yellow, noteContent = "第一条笔记")
        )
        val incoming = HighlightInterval(startOffset = 20, endOffset = 35, colorHex = yellow)

        val result = manager.addOrMerge(existing, incoming)

        assertEquals(1, result.size)
        assertEquals(10, result[0].startOffset)
        assertEquals(35, result[0].endOffset)
        assertEquals(yellow, result[0].colorHex)
        assertEquals("第一条笔记", result[0].noteContent)
    }

    @Test
    fun `merges adjacent same-color intervals and bridges gap`() {
        val existing = listOf(
            HighlightInterval(id = 1L, startOffset = 10, endOffset = 20, colorHex = yellow),
            HighlightInterval(id = 2L, startOffset = 30, endOffset = 40, colorHex = yellow)
        )
        val bridge = HighlightInterval(startOffset = 20, endOffset = 30, colorHex = yellow, noteContent = "桥接批注")

        val result = manager.addOrMerge(existing, bridge)

        assertEquals(1, result.size)
        assertEquals(10, result[0].startOffset)
        assertEquals(40, result[0].endOffset)
        assertEquals("桥接批注", result[0].noteContent)
    }

    @Test
    fun `completely covers smaller intervals of same or different color`() {
        val existing = listOf(
            HighlightInterval(id = 1L, startOffset = 12, endOffset = 18, colorHex = yellow),
            HighlightInterval(id = 2L, startOffset = 20, endOffset = 28, colorHex = green)
        )
        val covering = HighlightInterval(startOffset = 10, endOffset = 35, colorHex = green)

        val result = manager.addOrMerge(existing, covering)

        assertEquals(1, result.size)
        assertEquals(10, result[0].startOffset)
        assertEquals(35, result[0].endOffset)
        assertEquals(green, result[0].colorHex)
    }

    @Test
    fun `splits interval cleanly when erasing a middle hole`() {
        val existing = listOf(
            HighlightInterval(id = 10L, startOffset = 10, endOffset = 30, colorHex = yellow, noteContent = "批注内容")
        )

        val result = manager.erase(existing, eraseStart = 16, eraseEnd = 22)

        assertEquals(2, result.size)
        assertEquals(10, result[0].startOffset)
        assertEquals(16, result[0].endOffset)
        assertEquals(10L, result[0].id)
        assertEquals("批注内容", result[0].noteContent)

        assertEquals(22, result[1].startOffset)
        assertEquals(30, result[1].endOffset)
        assertEquals(0L, result[1].id)
        assertEquals("批注内容", result[1].noteContent)
    }

    @Test
    fun `trims partial overlap and removes fully erased intervals`() {
        val existing = listOf(
            HighlightInterval(startOffset = 5, endOffset = 15, colorHex = yellow),
            HighlightInterval(startOffset = 20, endOffset = 30, colorHex = green),
            HighlightInterval(startOffset = 35, endOffset = 50, colorHex = yellow)
        )

        val result = manager.erase(existing, eraseStart = 12, eraseEnd = 40)

        assertEquals(2, result.size)
        assertEquals(5 to 12, result[0].startOffset to result[0].endOffset)
        assertEquals(40 to 50, result[1].startOffset to result[1].endOffset)
    }

    @Test
    fun `different color overrides middle portion and keeps adjacent colors separate`() {
        val existing = listOf(
            HighlightInterval(id = 1L, startOffset = 10, endOffset = 30, colorHex = yellow, noteContent = "旧批注")
        )
        val differentColor = HighlightInterval(startOffset = 15, endOffset = 22, colorHex = green, noteContent = "新批注")

        val result = manager.addOrMerge(existing, differentColor)

        assertEquals(3, result.size)
        assertEquals(Triple(10, 15, yellow), Triple(result[0].startOffset, result[0].endOffset, result[0].colorHex))
        assertEquals(Triple(15, 22, green), Triple(result[1].startOffset, result[1].endOffset, result[1].colorHex))
        assertEquals(Triple(22, 30, yellow), Triple(result[2].startOffset, result[2].endOffset, result[2].colorHex))
    }

    @Test
    fun `adjacent different color intervals do not merge`() {
        val existing = listOf(
            HighlightInterval(startOffset = 10, endOffset = 20, colorHex = yellow)
        )
        val adjacent = HighlightInterval(startOffset = 20, endOffset = 30, colorHex = green)

        val result = manager.addOrMerge(existing, adjacent)

        assertEquals(2, result.size)
        assertEquals(yellow, result[0].colorHex)
        assertEquals(green, result[1].colorHex)
    }

    @Test
    fun `ignores invalid empty intervals and finds overlapping spans`() {
        val existing = listOf(
            HighlightInterval(startOffset = 10, endOffset = 20, colorHex = yellow)
        )
        val invalid = HighlightInterval(startOffset = 25, endOffset = 25, colorHex = green)

        val afterAdd = manager.addOrMerge(existing, invalid)
        assertEquals(1, afterAdd.size)
        assertNull(afterAdd[0].noteContent)

        assertEquals(1, manager.findOverlapping(afterAdd, 19, 25).size)
        assertTrue(manager.findOverlapping(afterAdd, 20, 25).isEmpty())
    }
}
