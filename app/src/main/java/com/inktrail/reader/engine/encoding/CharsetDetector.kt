package com.inktrail.reader.engine.encoding

import org.mozilla.universalchardet.UniversalDetector
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Result of inspecting raw file header bytes for character encoding.
 */
data class CharsetDetectionResult(
    val charsetName: String,
    val hasBom: Boolean,
    val bomLength: Int,
    val isCertain: Boolean
)

/**
 * Pure JVM/Kotlin character set detector backed by BOM inspection, juniversalchardet,
 * and deterministic UTF-8 / GB18030 validation fallbacks.
 */
class CharsetDetector {

    fun detect(
        inputStream: InputStream,
        sampleLimitBytes: Int = DEFAULT_SAMPLE_SIZE_BYTES
    ): CharsetDetectionResult {
        require(sampleLimitBytes > 0) { "sampleLimitBytes must be positive" }
        val buffer = ByteArray(sampleLimitBytes)
        var totalRead = 0
        var reachedEof = false
        while (totalRead < sampleLimitBytes) {
            val read = inputStream.read(buffer, totalRead, sampleLimitBytes - totalRead)
            if (read == -1) {
                reachedEof = true
                break
            }
            totalRead += read
        }
        val sample = if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
        return detect(sample, reachedEof = reachedEof)
    }

    fun detect(
        bytes: ByteArray,
        reachedEof: Boolean = true
    ): CharsetDetectionResult {
        if (bytes.isEmpty()) {
            return CharsetDetectionResult(
                charsetName = DEFAULT_FALLBACK_CHARSET,
                hasBom = false,
                bomLength = 0,
                isCertain = false
            )
        }

        detectBom(bytes)?.let { return it }

        val detector = UniversalDetector(null)
        try {
            var offset = 0
            while (offset < bytes.size && !detector.isDone) {
                val len = minOf(DETECTOR_CHUNK_SIZE, bytes.size - offset)
                detector.handleData(bytes, offset, len)
                offset += len
            }
            detector.dataEnd()
            val rawDetected = detector.detectedCharset
            val normalized = rawDetected?.let(::normalizeCharsetName)
            if (normalized != null && isSupportedCharset(normalized)) {
                val shouldCheckCjkOverride =
                    !detector.isDone &&
                        bytes.size < SHORT_SAMPLE_OVERRIDE_LIMIT &&
                        normalized in AMBIGUOUS_SINGLE_BYTE_CHARSETS
                if (shouldCheckCjkOverride) {
                    if (!isPureAscii(bytes) && isValidUtf8(bytes, allowTruncatedTail = !reachedEof)) {
                        return CharsetDetectionResult(
                            charsetName = "UTF-8",
                            hasBom = false,
                            bomLength = 0,
                            isCertain = true
                        )
                    }
                    if (looksLikeStrictGbkPairs(bytes)) {
                        return CharsetDetectionResult(
                            charsetName = CJK_FALLBACK_CHARSET,
                            hasBom = false,
                            bomLength = 0,
                            isCertain = false
                        )
                    }
                }
                return CharsetDetectionResult(
                    charsetName = normalized,
                    hasBom = false,
                    bomLength = 0,
                    isCertain = true
                )
            }
        } finally {
            detector.reset()
        }

        return detectHeuristicFallback(bytes, allowTruncatedTail = !reachedEof)
    }

    fun detectCharsetName(
        inputStream: InputStream,
        sampleLimitBytes: Int = DEFAULT_SAMPLE_SIZE_BYTES
    ): String = detect(inputStream, sampleLimitBytes).charsetName

    fun detectCharsetName(bytes: ByteArray): String = detect(bytes).charsetName

    private fun detectBom(bytes: ByteArray): CharsetDetectionResult? {
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                return CharsetDetectionResult("UTF-32BE", hasBom = true, bomLength = 4, isCertain = true)
            }
            if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                return CharsetDetectionResult("UTF-32LE", hasBom = true, bomLength = 4, isCertain = true)
            }
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return CharsetDetectionResult("UTF-8", hasBom = true, bomLength = 3, isCertain = true)
            }
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFE && b1 == 0xFF) {
                return CharsetDetectionResult("UTF-16BE", hasBom = true, bomLength = 2, isCertain = true)
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return CharsetDetectionResult("UTF-16LE", hasBom = true, bomLength = 2, isCertain = true)
            }
        }
        return null
    }

    private fun detectHeuristicFallback(
        bytes: ByteArray,
        allowTruncatedTail: Boolean
    ): CharsetDetectionResult {
        detectUtf16WithoutBom(bytes)?.let { charset ->
            return CharsetDetectionResult(charset, hasBom = false, bomLength = 0, isCertain = false)
        }

        if (isPureAscii(bytes)) {
            return CharsetDetectionResult("UTF-8", hasBom = false, bomLength = 0, isCertain = false)
        }

        if (isValidUtf8(bytes, allowTruncatedTail = allowTruncatedTail)) {
            return CharsetDetectionResult("UTF-8", hasBom = false, bomLength = 0, isCertain = true)
        }

        return CharsetDetectionResult(
            charsetName = CJK_FALLBACK_CHARSET,
            hasBom = false,
            bomLength = 0,
            isCertain = false
        )
    }

    private fun detectUtf16WithoutBom(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        val inspectLen = minOf(bytes.size, 1024) and 0x7FFFFFFE
        if (inspectLen < 4) return null
        var evenNulls = 0
        var oddNulls = 0
        val pairs = inspectLen / 2
        for (i in 0 until inspectLen step 2) {
            if (bytes[i] == 0.toByte()) evenNulls++
            if (bytes[i + 1] == 0.toByte()) oddNulls++
        }
        return when {
            oddNulls > pairs / 3 && evenNulls < pairs / 10 -> "UTF-16LE"
            evenNulls > pairs / 3 && oddNulls < pairs / 10 -> "UTF-16BE"
            else -> null
        }
    }

    private fun isPureAscii(bytes: ByteArray): Boolean {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0 || v > 0x7F) return false
        }
        return true
    }

    private fun isValidUtf8(bytes: ByteArray, allowTruncatedTail: Boolean): Boolean {
        val trimEnd = if (allowTruncatedTail) trimTrailingIncompleteUtf8(bytes) else bytes.size
        if (trimEnd <= 0) return false
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, trimEnd))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * When a fixed 64KB sample cuts right across a multi-byte UTF-8 sequence,
     * exclude the trailing incomplete lead + continuation bytes before validating.
     */
    private fun trimTrailingIncompleteUtf8(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        var contCount = 0
        var idx = bytes.size - 1
        while (idx >= 0 && contCount < 3 && (bytes[idx].toInt() and 0xC0) == 0x80) {
            contCount++
            idx--
        }
        if (idx < 0) return bytes.size
        val lead = bytes[idx].toInt() and 0xFF
        val expectedTotal = when {
            lead and 0x80 == 0 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            else -> return bytes.size
        }
        val actualLen = bytes.size - idx
        return if (actualLen < expectedTotal) idx else bytes.size
    }

    private fun normalizeCharsetName(rawName: String): String {
        val upper = rawName.trim().uppercase()
        return when (upper) {
            "GB2312", "GBK", "X-GBK", "HZ-GB-2312" -> "GB18030"
            "ASCII", "US-ASCII" -> "UTF-8"
            else -> upper
        }
    }

    private fun looksLikeStrictGbkPairs(bytes: ByteArray): Boolean {
        var i = 0
        var gbkPairCount = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 <= 0x7F) {
                i++
                continue
            }
            if (i + 1 >= bytes.size) return false
            val b1 = bytes[i + 1].toInt() and 0xFF
            val validGb2312OrGbkPair = (b0 in 0xA1..0xF7 && b1 in 0xA1..0xFE) ||
                (b0 in 0x81..0xFE && (b1 in 0x40..0x7E || b1 in 0x80..0xFE))
            if (!validGb2312OrGbkPair) return false
            gbkPairCount++
            i += 2
        }
        return gbkPairCount >= 2
    }

    private fun isSupportedCharset(name: String): Boolean =
        try {
            Charset.isSupported(name)
        } catch (_: Exception) {
            false
        }

    companion object {
        const val DEFAULT_SAMPLE_SIZE_BYTES = 64 * 1024
        const val DEFAULT_FALLBACK_CHARSET = "UTF-8"
        const val CJK_FALLBACK_CHARSET = "GB18030"
        private const val DETECTOR_CHUNK_SIZE = 4096
        private const val SHORT_SAMPLE_OVERRIDE_LIMIT = 512
        private val AMBIGUOUS_SINGLE_BYTE_CHARSETS = setOf(
            "KOI8-R", "WINDOWS-1251", "WINDOWS-1252", "WINDOWS-1253",
            "WINDOWS-1255", "ISO-8859-1", "ISO-8859-2", "ISO-8859-5",
            "ISO-8859-7", "ISO-8859-8", "IBM855", "IBM866", "MACCYRILLIC", "TIS-620"
        )
    }
}
