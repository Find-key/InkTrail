package com.inktrail.reader.engine.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class CharsetDetectorTest {

    private val detector = CharsetDetector()

    @Test
    fun `detects utf8 without bom on chinese text`() {
        val sample = "第一章 初入江湖\n夜色如墨，风雪交加。少年推开客栈大门，只见灯火摇曳。".repeat(4)
        val bytes = sample.toByteArray(Charsets.UTF_8)

        val result = detector.detect(ByteArrayInputStream(bytes))

        assertEquals("UTF-8", result.charsetName)
        assertFalse(result.hasBom)
        assertEquals(0, result.bomLength)
        assertEquals(sample, String(bytes, Charset.forName(result.charsetName)))
    }

    @Test
    fun `detects utf8 with bom`() {
        val sample = "序章\n这是一个带有 UTF-8 BOM 的文本文件。"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + sample.toByteArray(Charsets.UTF_8)

        val result = detector.detect(bytes)

        assertEquals("UTF-8", result.charsetName)
        assertTrue(result.hasBom)
        assertEquals(3, result.bomLength)
        assertTrue(result.isCertain)
        val decoded = String(bytes, result.bomLength, bytes.size - result.bomLength, Charset.forName(result.charsetName))
        assertEquals(sample, decoded)
    }

    @Test
    fun `detects gbk sample accurately and decodes without corruption`() {
        val sample = "第一章 烟雨江南\n小桥流水人家，古道西风瘦马。夕阳西下，断肠人在天涯。" +
            "青石板路上行人寥寥，远处茶肆飘来阵阵清香，书生驻足片刻，提笔写下一行墨迹。"
        val gbkCharset = Charset.forName("GBK")
        val bytes = sample.toByteArray(gbkCharset)

        val result = detector.detect(ByteArrayInputStream(bytes))

        assertTrue(
            "Expected GBK/GB18030 but got ${result.charsetName}",
            result.charsetName == "GB18030" || result.charsetName == "GBK"
        )
        assertFalse(result.hasBom)
        assertEquals(sample, String(bytes, Charset.forName(result.charsetName)))
    }

    @Test
    fun `detects short gbk sample via fallback without misreading as utf8`() {
        val sample = "中文短句测试"
        val bytes = sample.toByteArray(Charset.forName("GBK"))

        val result = detector.detect(bytes)

        assertTrue(
            "Expected GB18030/GBK fallback for invalid UTF-8 short sample, got ${result.charsetName}",
            result.charsetName == "GB18030" || result.charsetName == "GBK"
        )
        assertEquals(sample, String(bytes, Charset.forName(result.charsetName)))
    }

    @Test
    fun `detects utf16le with bom`() {
        val sample = "Chapter 1\nHello InkTrail 世界"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + sample.toByteArray(Charsets.UTF_16LE)

        val result = detector.detect(bytes)

        assertEquals("UTF-16LE", result.charsetName)
        assertTrue(result.hasBom)
        assertEquals(2, result.bomLength)
    }

    @Test
    fun `handles utf8 sample truncated mid-character at sample limit`() {
        val fullBytes = "墨辙阅读器".repeat(20).toByteArray(Charsets.UTF_8)
        // Truncate inside a 3-byte Chinese UTF-8 sequence (e.g., 10 bytes is 3 chars + 1 lead byte)
        val result = detector.detect(ByteArrayInputStream(fullBytes), sampleLimitBytes = 10)

        assertEquals("UTF-8", result.charsetName)
    }

    @Test
    fun `returns safe utf8 default on empty input`() {
        val result = detector.detect(ByteArray(0))

        assertEquals("UTF-8", result.charsetName)
        assertFalse(result.hasBom)
    }
}
