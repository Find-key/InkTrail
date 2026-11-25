package com.inktrail.reader.engine.splitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class ChapterSplitterTest {

    private val splitter = ChapterSplitter()

    @Test
    fun `splits chinese chapters with strictly contiguous byte offsets`() {
        val text = buildString {
            append("作品简介：山河万里，墨迹留痕。\n\n")
            append("序章 初雪\n雪花纷纷扬扬落下。\n")
            append("第一章 风起云涌\n少年背起行囊踏出山门。\n")
            append("第2回 客栈夜话\n烛影摇红，江湖客齐聚一堂。\n")
            append("尾声\n故事暂告一段落。\n")
            append("番外 一念之间\n多年以后，故人重逢。")
        }
        val bytes = text.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8")

        assertEquals(6, chapters.size)
        assertEquals(
            listOf("前言", "序章 初雪", "第一章 风起云涌", "第2回 客栈夜话", "尾声", "番外 一念之间"),
            chapters.map { it.title }
        )
        assertContiguousCoverage(chapters, bytes.size.toLong())
        assertEquals(text, decodeAllSlices(bytes, chapters, Charsets.UTF_8))
    }

    @Test
    fun `splits english chapters case insensitively`() {
        val text = buildString {
            append("Chapter 1: Awakening\nIt was a bright cold day in April.\n")
            append("CHAPTER 2 THE JOURNEY\nThey crossed the misty bridge at dawn.\n")
            append("chapter 10 Epilogue\nAnd the ink remained on the page.")
        }
        val bytes = text.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8")

        assertEquals(3, chapters.size)
        assertEquals(
            listOf("Chapter 1: Awakening", "CHAPTER 2 THE JOURNEY", "chapter 10 Epilogue"),
            chapters.map { it.title }
        )
        assertContiguousCoverage(chapters, bytes.size.toLong())
        assertEquals(text, decodeAllSlices(bytes, chapters, Charsets.UTF_8))
    }

    @Test
    fun `fallback splits long chapterless utf8 text into 24-32kb slices without breaking multibyte chars`() {
        val line = "春风吹过江南岸，烟雨朦胧画舫船，少年执伞行于青石长街之上，听得远处钟声悠扬。\n"
        val sb = StringBuilder()
        while (sb.toString().toByteArray(Charsets.UTF_8).size < 110 * 1024) {
            sb.append(line)
        }
        val text = sb.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8")

        assertTrue("Expected at least 3 slices for 110KB text", chapters.size >= 3)
        assertContiguousCoverage(chapters, bytes.size.toLong())
        for (slice in chapters) {
            val size = slice.endByteOffset - slice.startByteOffset
            assertTrue("Slice size $size exceeded 32KB", size <= 32L * 1024L)
        }
        for (i in 0 until chapters.lastIndex) {
            val size = chapters[i].endByteOffset - chapters[i].startByteOffset
            assertTrue("Non-terminal slice size $size should be in [24KB, 32KB]", size in (24L * 1024L)..(32L * 1024L))
        }
        assertEquals(text, decodeAllSlicesStrictly(bytes, chapters, Charsets.UTF_8))
    }

    @Test
    fun `fallback splits giant unbroken utf8 single line without corrupting multibyte characters`() {
        val unit = "墨迹流转A中B文"
        val sb = StringBuilder()
        while (sb.toString().toByteArray(Charsets.UTF_8).size < 90 * 1024) {
            sb.append(unit)
        }
        val text = sb.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8")

        assertTrue(chapters.size >= 3)
        assertContiguousCoverage(chapters, bytes.size.toLong())
        for (slice in chapters) {
            assertTrue((slice.endByteOffset - slice.startByteOffset) <= 32L * 1024L)
        }
        assertEquals(text, decodeAllSlicesStrictly(bytes, chapters, Charsets.UTF_8))
    }

    @Test
    fun `fallback splits giant unbroken gbk single line without splitting two-byte gbk characters`() {
        val gbk = Charset.forName("GBK")
        val unit = "墨辙阅读器测试汉字"
        val sb = StringBuilder()
        while (sb.toString().toByteArray(gbk).size < 90 * 1024) {
            sb.append(unit)
        }
        val text = sb.toString()
        val bytes = text.toByteArray(gbk)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "GBK")

        assertTrue(chapters.size >= 3)
        assertContiguousCoverage(chapters, bytes.size.toLong())
        for (slice in chapters) {
            assertTrue((slice.endByteOffset - slice.startByteOffset) <= 32L * 1024L)
        }
        assertEquals(text, decodeAllSlicesStrictly(bytes, chapters, gbk))
    }

    @Test
    fun `splits oversized single chapter while keeping normal chapters intact`() {
        val smallChapter = "第一章 短章\n这是很短的一章内容。\n"
        val bigLine = "大章节正文段落，记录了漫长的旅途与无数细节描写。\n"
        val bigBody = buildString {
            append("第二章 巨章\n")
            while (toString().toByteArray(Charsets.UTF_8).size < 75 * 1024) {
                append(bigLine)
            }
        }
        val tailChapter = "第三章 尾章\n风平浪静。\n"
        val fullText = smallChapter + bigBody + tailChapter
        val bytes = fullText.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8")

        assertEquals("第一章 短章", chapters.first().title)
        assertEquals("第三章 尾章", chapters.last().title)
        assertTrue(chapters.size >= 5)
        assertTrue(chapters[1].title.startsWith("第二章 巨章 ("))
        assertContiguousCoverage(chapters, bytes.size.toLong())
        assertEquals(fullText, decodeAllSlicesStrictly(bytes, chapters, Charsets.UTF_8))
    }

    @Test
    fun `handles utf8 bom before first chapter heading without splitting bom`() {
        val content = "第一章 启程\n踏上新的征途。\n第二章 相遇\n林间小道。"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + content.toByteArray(Charsets.UTF_8)

        val chapters = splitter.split(ByteArrayInputStream(bytes), "UTF-8", bomLength = 3)

        assertEquals(2, chapters.size)
        assertEquals("第一章 启程", chapters[0].title)
        assertEquals("第二章 相遇", chapters[1].title)
        assertContiguousCoverage(chapters, bytes.size.toLong())
    }

    @Test
    fun `empty stream produces empty chapter list`() {
        val chapters = splitter.split(ByteArrayInputStream(ByteArray(0)), "UTF-8")
        assertTrue(chapters.isEmpty())
    }

    private fun assertContiguousCoverage(chapters: List<ChapterSlice>, totalBytes: Long) {
        assertFalse(chapters.isEmpty())
        assertEquals(0L, chapters.first().startByteOffset)
        assertEquals(totalBytes, chapters.last().endByteOffset)
        for (i in chapters.indices) {
            assertEquals(i, chapters[i].chapterIndex)
            assertTrue(chapters[i].startByteOffset < chapters[i].endByteOffset)
            if (i + 1 < chapters.size) {
                assertEquals(chapters[i].endByteOffset, chapters[i + 1].startByteOffset)
            }
        }
    }

    private fun decodeAllSlices(bytes: ByteArray, chapters: List<ChapterSlice>, charset: Charset): String {
        return chapters.joinToString("") { slice ->
            String(bytes, slice.startByteOffset.toInt(), (slice.endByteOffset - slice.startByteOffset).toInt(), charset)
        }
    }

    private fun decodeAllSlicesStrictly(bytes: ByteArray, chapters: List<ChapterSlice>, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return chapters.joinToString("") { slice ->
            decoder.reset()
            val buf = ByteBuffer.wrap(
                bytes,
                slice.startByteOffset.toInt(),
                (slice.endByteOffset - slice.startByteOffset).toInt()
            )
            decoder.decode(buf).toString()
        }
    }
}
