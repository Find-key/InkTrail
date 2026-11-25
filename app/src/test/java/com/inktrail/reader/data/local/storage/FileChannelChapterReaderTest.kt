package com.inktrail.reader.data.local.storage

import com.inktrail.reader.engine.encoding.CharsetDetector
import com.inktrail.reader.engine.splitter.ChapterSplitter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

class FileChannelChapterReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val reader = FileChannelChapterReader()
    private val detector = CharsetDetector()
    private val splitter = ChapterSplitter()

    @Test
    fun `reads exact gbk chapter byte slices from disk without loading full file`() {
        val text = "第一章 江湖夜雨\n细雨敲打青瓦。\n第二章 剑影\n寒光一闪。"
        val gbk = Charset.forName("GBK")
        val file = tempFolder.newFile("book_gbk.txt").apply {
            writeBytes(text.toByteArray(gbk))
        }

        val detected = file.inputStream().use { detector.detect(it) }
        val slices = file.inputStream().use { splitter.split(it, detected.charsetName, detected.bomLength) }

        assertEquals(2, slices.size)
        val ch0 = reader.readChapterText(
            filePath = file.absolutePath,
            charsetName = detected.charsetName,
            startByteOffset = slices[0].startByteOffset,
            endByteOffset = slices[0].endByteOffset,
            stripLeadingBom = true
        )
        val ch1 = reader.readChapterText(
            filePath = file.absolutePath,
            charsetName = detected.charsetName,
            startByteOffset = slices[1].startByteOffset,
            endByteOffset = slices[1].endByteOffset,
            stripLeadingBom = false
        )

        assertEquals("第一章 江湖夜雨\n细雨敲打青瓦。\n", ch0)
        assertEquals("第二章 剑影\n寒光一闪。", ch1)
    }

    @Test
    fun `strips utf8 bom on first chapter when requested`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "序章\nInkTrail 墨辙"
        val bytes = bom + content.toByteArray(Charsets.UTF_8)
        val file = tempFolder.newFile("bom.txt").apply { writeBytes(bytes) }

        val read = reader.readChapterText(
            filePath = file.absolutePath,
            charsetName = "UTF-8",
            startByteOffset = 0L,
            endByteOffset = bytes.size.toLong(),
            stripLeadingBom = true
        )

        assertEquals(content, read)
    }

    @Test(expected = BookFileMissingException::class)
    fun `throws BookFileMissingException when file does not exist`() {
        val missing = File(tempFolder.root, "does_not_exist.txt")
        reader.readChapterText(missing.absolutePath, "UTF-8", 0L, 10L)
    }

    @Test(expected = ChapterReadException::class)
    fun `throws ChapterReadException on invalid byte interval`() {
        val file = tempFolder.newFile("valid.txt").apply { writeText("hello") }
        reader.readChapterText(file.absolutePath, "UTF-8", 5L, 2L)
    }
}
