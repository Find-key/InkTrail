package com.inktrail.reader.engine.cleaner

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanerTest {

    private val cleaner = TextCleaner()

    @Test
    fun `merges consecutive blank lines into at most one separator line`() {
        val raw = "\n\n第一段正文。\n\n\n   \n\n第二段正文。\n\n\n"
        val options = TextCleanerOptions(
            enabled = true,
            mergeBlankLines = true,
            fixHardLineBreaks = false,
            addCjkSpacing = false,
            normalizeParagraphIndent = false
        )

        val cleaned = cleaner.clean(raw, options)

        assertEquals("第一段正文。\n\n第二段正文。", cleaned)
    }

    @Test
    fun `fixes abnormal hard line breaks while keeping real paragraph boundaries`() {
        val raw = buildString {
            append("第一章 初雪\n")
            append("少年推开木窗，只见\n")
            append("远处山峦银装素裹。\n")
            append("　　第二段已经存在缩进。\n")
            append("This is a broken\n")
            append("English sentence.")
        }
        val options = TextCleanerOptions(
            enabled = true,
            mergeBlankLines = false,
            fixHardLineBreaks = true,
            addCjkSpacing = false,
            normalizeParagraphIndent = false
        )

        val cleaned = cleaner.clean(raw, options)

        val expected = buildString {
            append("第一章 初雪\n")
            append("少年推开木窗，只见远处山峦银装素裹。\n")
            append("　　第二段已经存在缩进。\n")
            append("This is a broken English sentence.")
        }
        assertEquals(expected, cleaned)
    }

    @Test
    fun `inserts spaces between cjk and latin or digits without spacing punctuation`() {
        val raw = "我用Kotlin写Android应用，支持50MB大文件！"
        val options = TextCleanerOptions(
            enabled = true,
            mergeBlankLines = false,
            fixHardLineBreaks = false,
            addCjkSpacing = true,
            normalizeParagraphIndent = false
        )

        val cleaned = cleaner.clean(raw, options)

        assertEquals("我用 Kotlin 写 Android 应用，支持 50MB 大文件！", cleaned)
    }

    @Test
    fun `normalizes body paragraph indentation while leaving headings unindented`() {
        val raw = "第一章 启程\n  天亮了。\n　　他背起行囊。"
        val options = TextCleanerOptions(
            enabled = true,
            mergeBlankLines = true,
            fixHardLineBreaks = false,
            addCjkSpacing = false,
            normalizeParagraphIndent = true
        )

        val cleaned = cleaner.clean(raw, options)

        assertEquals("第一章 启程\n　　天亮了。\n　　他背起行囊。", cleaned)
    }

    @Test
    fun `returns exact original text unchanged when cleaner switch is disabled`() {
        val raw = "\r\n\r\n第一行被\r\n截断，用Kotlin测试\n\n\n\n"
        val disabledOptions = TextCleanerOptions(enabled = false)

        val cleaned = cleaner.clean(raw, disabledOptions)

        assertEquals(raw, cleaned)
    }
}
