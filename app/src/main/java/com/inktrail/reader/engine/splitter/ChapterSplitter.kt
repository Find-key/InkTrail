package com.inktrail.reader.engine.splitter

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Byte-range metadata for a chapter or fallback logical slice within a book file.
 * Offsets form a half-open interval [startByteOffset, endByteOffset).
 */
data class ChapterSlice(
    val chapterIndex: Int,
    val title: String,
    val startByteOffset: Long,
    val endByteOffset: Long
)

data class ChapterSplitterConfig(
    val maxChapterBytes: Long = 64L * 1024L,
    val minFallbackChunkBytes: Long = 24L * 1024L,
    val targetFallbackChunkBytes: Long = 28L * 1024L,
    val maxFallbackChunkBytes: Long = 32L * 1024L
) {
    init {
        require(minFallbackChunkBytes > 0L)
        require(minFallbackChunkBytes <= targetFallbackChunkBytes)
        require(targetFallbackChunkBytes <= maxFallbackChunkBytes)
        require(maxFallbackChunkBytes <= maxChapterBytes)
    }
}

/**
 * Pure streaming chapter index builder.
 * Scans an [InputStream] once in constant memory to emit contiguous [ChapterSlice] entries
 * covering [0, totalBytes). Chapters larger than [ChapterSplitterConfig.maxChapterBytes]
 * or books without detectable headings are split into 24KB-32KB chunks on newline or
 * valid multi-byte character boundaries.
 */
class ChapterSplitter(
    private val config: ChapterSplitterConfig = ChapterSplitterConfig()
) {

    fun split(
        inputStream: InputStream,
        charsetName: String,
        bomLength: Int = 0
    ): List<ChapterSlice> {
        val charset = resolveCharset(charsetName)
        val unitSize = codeUnitByteWidth(charset)
        return when (unitSize) {
            2 -> splitFixedWidthStream(inputStream, charset, unitWidth = 2, isLittleEndian = isLittleEndian(charset), bomLength = bomLength)
            4 -> splitFixedWidthStream(inputStream, charset, unitWidth = 4, isLittleEndian = isLittleEndian(charset), bomLength = bomLength)
            else -> splitByteStream(inputStream, charset, bomLength = bomLength)
        }
    }

    private fun splitByteStream(
        inputStream: InputStream,
        charset: Charset,
        bomLength: Int
    ): List<ChapterSlice> {
        val readBuffer = ByteArray(STREAM_BUFFER_SIZE)
        val boundaryTracker = VariableWidthBoundaryTracker(charset)
        val lineCapture = LinePreviewCapture(MAX_TITLE_CAPTURE_BYTES)

        val assembler = ChapterAssembler(config)
        var globalOffset = 0L
        var currentLineStart = 0L
        var lineHasNonWs = false
        var chapterHasNonWs = false
        var prevByteWasCr = false

        while (true) {
            val read = inputStream.read(readBuffer)
            if (read == -1) break

            for (i in 0 until read) {
                val rawByte = readBuffer[i]
                val b = rawByte.toInt() and 0xFF
                val nextOffset = globalOffset + 1L

                // Skip BOM prefix bytes from character-boundary state machine & preview text.
                if (globalOffset < bomLength) {
                    globalOffset = nextOffset
                    boundaryTracker.reset()
                    if (globalOffset == bomLength.toLong()) {
                        currentLineStart = 0L
                    }
                    continue
                }

                // Handle CRLF second byte cleanly so line endings aren't split mid-CRLF.
                if (prevByteWasCr) {
                    prevByteWasCr = false
                    if (b == 0x0A) {
                        boundaryTracker.reset()
                        assembler.onSafeBoundary(nextOffset, isLineEnd = true)
                        lineCapture.reset()
                        currentLineStart = nextOffset
                        lineHasNonWs = false
                        globalOffset = nextOffset
                        continue
                    } else {
                        // Lone CR ended the previous line right at globalOffset.
                        assembler.onSafeBoundary(globalOffset, isLineEnd = true)
                        lineCapture.reset()
                        currentLineStart = globalOffset
                        lineHasNonWs = false
                    }
                }

                if (b == 0x0A) {
                    lineCapture.append(rawByte)
                    val title = matchHeadingIfLineComplete(lineCapture, charset)
                    if (title != null) {
                        assembler.onHeadingLine(
                            lineStartOffset = currentLineStart,
                            headingTitle = title,
                            priorContentHasNonWs = chapterHasNonWs
                        )
                        chapterHasNonWs = true
                    } else if (lineHasNonWs) {
                        chapterHasNonWs = true
                    }
                    boundaryTracker.reset()
                    assembler.onSafeBoundary(nextOffset, isLineEnd = true)
                    lineCapture.reset()
                    currentLineStart = nextOffset
                    lineHasNonWs = false
                    globalOffset = nextOffset
                    continue
                }

                if (b == 0x0D) {
                    val title = matchHeadingIfLineComplete(lineCapture, charset)
                    if (title != null) {
                        assembler.onHeadingLine(
                            lineStartOffset = currentLineStart,
                            headingTitle = title,
                            priorContentHasNonWs = chapterHasNonWs
                        )
                        chapterHasNonWs = true
                    } else if (lineHasNonWs) {
                        chapterHasNonWs = true
                    }
                    boundaryTracker.reset()
                    prevByteWasCr = true
                    globalOffset = nextOffset
                    continue
                }

                lineCapture.append(rawByte)
                if (!isAsciiWhitespace(b)) {
                    lineHasNonWs = true
                }
                val isCharEnd = boundaryTracker.feed(b)
                if (isCharEnd) {
                    assembler.onSafeBoundary(nextOffset, isLineEnd = false)
                }
                globalOffset = nextOffset
            }
        }

        if (globalOffset == 0L) return emptyList()

        // Flush any trailing line that did not end with a newline.
        if (prevByteWasCr) {
            assembler.onSafeBoundary(globalOffset, isLineEnd = true)
        } else if (currentLineStart < globalOffset) {
            val title = matchHeadingIfLineComplete(lineCapture, charset)
            if (title != null) {
                assembler.onHeadingLine(
                    lineStartOffset = currentLineStart,
                    headingTitle = title,
                    priorContentHasNonWs = chapterHasNonWs
                )
            }
            assembler.onSafeBoundary(globalOffset, isLineEnd = true)
        }

        return assembler.finish(totalBytes = globalOffset)
    }

    private fun splitFixedWidthStream(
        inputStream: InputStream,
        charset: Charset,
        unitWidth: Int,
        isLittleEndian: Boolean,
        bomLength: Int
    ): List<ChapterSlice> {
        val readBuffer = ByteArray(STREAM_BUFFER_SIZE)
        val unitBytes = ByteArray(unitWidth)
        var unitFill = 0
        val lineCapture = LinePreviewCapture(MAX_TITLE_CAPTURE_BYTES)
        val assembler = ChapterAssembler(config)

        var globalOffset = 0L
        var currentLineStart = 0L
        var lineHasNonWs = false
        var chapterHasNonWs = false
        var prevUnitWasCr = false
        var pendingHighSurrogate = false

        while (true) {
            val read = inputStream.read(readBuffer)
            if (read == -1) break

            for (i in 0 until read) {
                val rawByte = readBuffer[i]
                val nextOffset = globalOffset + 1L

                if (globalOffset < bomLength) {
                    globalOffset = nextOffset
                    continue
                }

                unitBytes[unitFill++] = rawByte
                globalOffset = nextOffset

                if (unitFill < unitWidth) continue
                unitFill = 0

                val codePoint = decodeUnit(unitBytes, unitWidth, isLittleEndian)

                if (prevUnitWasCr) {
                    prevUnitWasCr = false
                    if (codePoint == 0x0A) {
                        assembler.onSafeBoundary(globalOffset, isLineEnd = true)
                        lineCapture.reset()
                        currentLineStart = globalOffset
                        lineHasNonWs = false
                        pendingHighSurrogate = false
                        continue
                    } else {
                        val lineBoundary = globalOffset - unitWidth
                        assembler.onSafeBoundary(lineBoundary, isLineEnd = true)
                        lineCapture.reset()
                        currentLineStart = lineBoundary
                        lineHasNonWs = false
                    }
                }

                if (codePoint == 0x0A) {
                    val title = matchHeadingIfLineComplete(lineCapture, charset)
                    if (title != null) {
                        assembler.onHeadingLine(
                            lineStartOffset = currentLineStart,
                            headingTitle = title,
                            priorContentHasNonWs = chapterHasNonWs
                        )
                        chapterHasNonWs = true
                    } else if (lineHasNonWs) {
                        chapterHasNonWs = true
                    }
                    assembler.onSafeBoundary(globalOffset, isLineEnd = true)
                    lineCapture.reset()
                    currentLineStart = globalOffset
                    lineHasNonWs = false
                    pendingHighSurrogate = false
                    continue
                }

                if (codePoint == 0x0D) {
                    val title = matchHeadingIfLineComplete(lineCapture, charset)
                    if (title != null) {
                        assembler.onHeadingLine(
                            lineStartOffset = currentLineStart,
                            headingTitle = title,
                            priorContentHasNonWs = chapterHasNonWs
                        )
                        chapterHasNonWs = true
                    } else if (lineHasNonWs) {
                        chapterHasNonWs = true
                    }
                    prevUnitWasCr = true
                    pendingHighSurrogate = false
                    continue
                }

                for (u in 0 until unitWidth) {
                    lineCapture.append(unitBytes[u])
                }
                if (codePoint > 0x20 || (codePoint != 0x20 && codePoint != 0x09)) {
                    lineHasNonWs = true
                }

                if (unitWidth == 2) {
                    val isHighSurrogate = codePoint in 0xD800..0xDBFF
                    if (isHighSurrogate) {
                        pendingHighSurrogate = true
                    } else {
                        pendingHighSurrogate = false
                        assembler.onSafeBoundary(globalOffset, isLineEnd = false)
                    }
                } else {
                    assembler.onSafeBoundary(globalOffset, isLineEnd = false)
                }
            }
        }

        if (globalOffset == 0L) return emptyList()

        if (prevUnitWasCr) {
            assembler.onSafeBoundary(globalOffset, isLineEnd = true)
        } else if (currentLineStart < globalOffset) {
            val title = matchHeadingIfLineComplete(lineCapture, charset)
            if (title != null) {
                assembler.onHeadingLine(
                    lineStartOffset = currentLineStart,
                    headingTitle = title,
                    priorContentHasNonWs = chapterHasNonWs
                )
            }
            assembler.onSafeBoundary(globalOffset, isLineEnd = true)
        }

        return assembler.finish(totalBytes = globalOffset)
    }

    private fun matchHeadingIfLineComplete(
        lineCapture: LinePreviewCapture,
        charset: Charset
    ): String? {
        if (lineCapture.isOverflowed || lineCapture.size == 0) return null
        val raw = lineCapture.decode(charset)
        return matchChapterTitle(raw)
    }

    internal fun matchChapterTitle(rawLine: String): String? {
        val cleaned = rawLine
            .removePrefix("\uFEFF")
            .trim()
        if (cleaned.isEmpty() || cleaned.length > MAX_HEADING_CHARS) return null
        return if (CHAPTER_TITLE_REGEX.matches(cleaned)) cleaned else null
    }

    private fun resolveCharset(charsetName: String): Charset =
        try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            Charsets.UTF_8
        }

    private fun codeUnitByteWidth(charset: Charset): Int {
        val name = charset.name().uppercase()
        return when {
            name.startsWith("UTF-16") || name == "UNICODE" -> 2
            name.startsWith("UTF-32") -> 4
            else -> 1
        }
    }

    private fun isLittleEndian(charset: Charset): Boolean {
        val name = charset.name().uppercase()
        return name.endsWith("LE")
    }

    private fun decodeUnit(bytes: ByteArray, width: Int, littleEndian: Boolean): Int {
        return if (width == 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
        } else {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }

    private fun isAsciiWhitespace(b: Int): Boolean =
        b == 0x20 || b == 0x09 || b == 0x0D || b == 0x0A

    /**
     * Buffers only the current open chapter's pending fallback slices until it becomes clear
     * whether the chapter exceeds [ChapterSplitterConfig.maxChapterBytes].
     */
    private class ChapterAssembler(
        private val config: ChapterSplitterConfig
    ) {
        private val result = ArrayList<ChapterSlice>()

        private var hasExplicitTitle = false
        private var currentTitle = DEFAULT_BODY_TITLE
        private var chapterStart = 0L
        private var chapterSplitStarted = false
        private var partIndex = 1

        private var activeSliceStart = 0L
        private val pendingChunks = ArrayList<Pair<Long, Long>>(4)

        private var bestNewlineInRange: Long? = null
        private var latestNewlineBeforeMax: Long? = null
        private var bestCharBoundary: Long? = null

        fun onHeadingLine(
            lineStartOffset: Long,
            headingTitle: String,
            priorContentHasNonWs: Boolean
        ) {
            if (lineStartOffset == chapterStart) {
                // First line of file (or right after BOM/empty start) is already a chapter heading.
                currentTitle = headingTitle
                hasExplicitTitle = true
                return
            }

            if (!hasExplicitTitle && !priorContentHasNonWs && result.isEmpty() && !chapterSplitStarted) {
                // Leading whitespace/BOM only before Chapter 1: include into Chapter 1 so offsets start at 0.
                currentTitle = headingTitle
                hasExplicitTitle = true
                return
            }

            closeCurrentChapter(endOffset = lineStartOffset, fallbackTitle = PREFACE_TITLE)

            chapterStart = lineStartOffset
            activeSliceStart = lineStartOffset
            currentTitle = headingTitle
            hasExplicitTitle = true
            chapterSplitStarted = false
            partIndex = 1
            pendingChunks.clear()
            resetCutCandidates()
        }

        fun onSafeBoundary(offset: Long, isLineEnd: Boolean) {
            if (offset <= activeSliceStart) return

            // If a chapter has already passed maxChapterBytes, flush any deferred 24-32KB chunks now.
            if (!chapterSplitStarted && (offset - chapterStart) > config.maxChapterBytes && pendingChunks.isNotEmpty()) {
                flushPendingChunks()
            }

            val sliceLen = offset - activeSliceStart
            if (sliceLen <= config.maxFallbackChunkBytes) {
                bestCharBoundary = offset
                if (isLineEnd) {
                    latestNewlineBeforeMax = offset
                    if (sliceLen >= config.minFallbackChunkBytes) {
                        val prev = bestNewlineInRange
                        if (prev == null || isCloserToTarget(offset, prev)) {
                            bestNewlineInRange = offset
                        }
                    }
                }
                return
            }

            // Exceeded maxFallbackChunkBytes for activeSliceStart; commit a slice at the best safe boundary.
            val cutOffset = chooseCutOffset() ?: offset
            recordOrEmitChunk(activeSliceStart, cutOffset)
            activeSliceStart = cutOffset
            resetCutCandidates()

            if (!chapterSplitStarted && (offset - chapterStart) > config.maxChapterBytes && pendingChunks.isNotEmpty()) {
                flushPendingChunks()
            }

            // Re-evaluate the remaining tail [activeSliceStart, offset]
            val remainingLen = offset - activeSliceStart
            if (remainingLen in 1L..config.maxFallbackChunkBytes) {
                bestCharBoundary = offset
                if (isLineEnd) {
                    latestNewlineBeforeMax = offset
                    if (remainingLen >= config.minFallbackChunkBytes) {
                        bestNewlineInRange = offset
                    }
                }
            } else if (remainingLen > config.maxFallbackChunkBytes) {
                // Occurs only if a single codepoint or CRLF jumped past maxFallbackChunkBytes
                recordOrEmitChunk(activeSliceStart, offset)
                activeSliceStart = offset
            }
        }

        fun finish(totalBytes: Long): List<ChapterSlice> {
            if (totalBytes <= 0L) return emptyList()
            closeCurrentChapter(endOffset = totalBytes, fallbackTitle = DEFAULT_BODY_TITLE)
            return result
        }

        private fun closeCurrentChapter(endOffset: Long, fallbackTitle: String) {
            if (endOffset <= chapterStart) return
            val chapterBytes = endOffset - chapterStart
            if (!chapterSplitStarted && chapterBytes <= config.maxChapterBytes) {
                val title = if (hasExplicitTitle) currentTitle else fallbackTitle
                result += ChapterSlice(
                    chapterIndex = result.size,
                    title = title,
                    startByteOffset = chapterStart,
                    endByteOffset = endOffset
                )
            } else {
                if (!chapterSplitStarted) {
                    flushPendingChunks()
                }
                if (activeSliceStart < endOffset) {
                    emitSplitSlice(activeSliceStart, endOffset)
                }
            }
            pendingChunks.clear()
            resetCutCandidates()
        }

        private fun recordOrEmitChunk(start: Long, end: Long) {
            if (end <= start) return
            if (chapterSplitStarted) {
                emitSplitSlice(start, end)
            } else {
                pendingChunks += (start to end)
            }
        }

        private fun flushPendingChunks() {
            chapterSplitStarted = true
            for ((start, end) in pendingChunks) {
                emitSplitSlice(start, end)
            }
            pendingChunks.clear()
        }

        private fun emitSplitSlice(start: Long, end: Long) {
            val title = if (hasExplicitTitle) {
                "$currentTitle ($partIndex)"
            } else {
                "第 $partIndex 节"
            }
            partIndex++
            result += ChapterSlice(
                chapterIndex = result.size,
                title = title,
                startByteOffset = start,
                endByteOffset = end
            )
        }

        private fun chooseCutOffset(): Long? {
            bestNewlineInRange?.let { return it }
            latestNewlineBeforeMax?.let { nl ->
                if (nl - activeSliceStart >= config.minFallbackChunkBytes / 2) {
                    return nl
                }
            }
            return bestCharBoundary
        }

        private fun isCloserToTarget(candidate: Long, currentBest: Long): Boolean {
            val candDist = kotlin.math.abs((candidate - activeSliceStart) - config.targetFallbackChunkBytes)
            val bestDist = kotlin.math.abs((currentBest - activeSliceStart) - config.targetFallbackChunkBytes)
            return candDist <= bestDist
        }

        private fun resetCutCandidates() {
            bestNewlineInRange = null
            latestNewlineBeforeMax = null
            bestCharBoundary = null
        }
    }

    /**
     * Tracks exact multi-byte character completion points in streaming byte order
     * for UTF-8, GBK/GB18030, Big5, and single-byte encodings without buffering lines.
     */
    private class VariableWidthBoundaryTracker(charset: Charset) {
        private enum class Mode { UTF8, GB18030, BIG5, SINGLE_BYTE }

        private val mode: Mode = when (charset.name().uppercase()) {
            "UTF-8" -> Mode.UTF8
            "GBK", "GB18030", "X-GBK", "GB2312", "EUC-CN" -> Mode.GB18030
            "BIG5", "BIG5-HKSCS", "X-BIG5-HKSCS-2001", "X-BIG5-SOLARIS" -> Mode.BIG5
            else -> Mode.SINGLE_BYTE
        }

        private var utf8Remaining = 0
        private var gbState = 0 // 0=ground, 1=afterLead1, 2=afterDigit1(4-byte), 3=afterLead2
        private var big5InTrail = false

        fun reset() {
            utf8Remaining = 0
            gbState = 0
            big5InTrail = false
        }

        fun feed(b: Int): Boolean {
            return when (mode) {
                Mode.SINGLE_BYTE -> true
                Mode.UTF8 -> feedUtf8(b)
                Mode.GB18030 -> feedGb18030(b)
                Mode.BIG5 -> feedBig5(b)
            }
        }

        private fun feedUtf8(b: Int): Boolean {
            if (utf8Remaining > 0) {
                return if ((b and 0xC0) == 0x80) {
                    utf8Remaining--
                    utf8Remaining == 0
                } else {
                    // Malformed stream recovery: treat current byte as a fresh starter.
                    utf8Remaining = 0
                    feedUtf8Starter(b)
                }
            }
            return feedUtf8Starter(b)
        }

        private fun feedUtf8Starter(b: Int): Boolean {
            return when {
                (b and 0x80) == 0 -> true
                (b and 0xE0) == 0xC0 -> {
                    utf8Remaining = 1
                    false
                }
                (b and 0xF0) == 0xE0 -> {
                    utf8Remaining = 2
                    false
                }
                (b and 0xF8) == 0xF0 -> {
                    utf8Remaining = 3
                    false
                }
                else -> true
            }
        }

        private fun feedGb18030(b: Int): Boolean {
            return when (gbState) {
                0 -> {
                    if (b in 0x81..0xFE) {
                        gbState = 1
                        false
                    } else {
                        true
                    }
                }
                1 -> {
                    when (b) {
                        in 0x40..0x7E, in 0x80..0xFE -> {
                            gbState = 0
                            true
                        }
                        in 0x30..0x39 -> {
                            gbState = 2
                            false
                        }
                        else -> {
                            gbState = 0
                            true
                        }
                    }
                }
                2 -> {
                    if (b in 0x81..0xFE) {
                        gbState = 3
                        false
                    } else {
                        gbState = 0
                        true
                    }
                }
                else -> {
                    gbState = 0
                    true
                }
            }
        }

        private fun feedBig5(b: Int): Boolean {
            return if (big5InTrail) {
                big5InTrail = false
                true
            } else if (b in 0x81..0xFE) {
                big5InTrail = true
                false
            } else {
                true
            }
        }
    }

    private class LinePreviewCapture(private val maxBytes: Int) {
        private val buffer = ByteArrayOutputStream(256)
        var isOverflowed: Boolean = false
            private set

        val size: Int
            get() = buffer.size()

        fun append(b: Byte) {
            if (isOverflowed) return
            if (buffer.size() >= maxBytes) {
                isOverflowed = true
                buffer.reset()
            } else {
                buffer.write(b.toInt() and 0xFF)
            }
        }

        fun reset() {
            buffer.reset()
            isOverflowed = false
        }

        fun decode(charset: Charset): String {
            val bytes = buffer.toByteArray()
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 8 * 1024
        private const val MAX_TITLE_CAPTURE_BYTES = 512
        private const val MAX_HEADING_CHARS = 80
        private const val DEFAULT_BODY_TITLE = "正文"
        private const val PREFACE_TITLE = "前言"

        val CHAPTER_TITLE_REGEX = Regex(
            """^\s*(?:第[零一二三四五六七八九十百千万两0-9]+[章节回卷集部篇].*|Chapter\s+\d+.*|(?:序[章言]?|楔子|尾声|引子|后记)(?:\s+.*|[：:].*)?|番外.*)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
