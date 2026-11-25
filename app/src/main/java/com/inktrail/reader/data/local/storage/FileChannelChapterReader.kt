package com.inktrail.reader.data.local.storage

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import javax.inject.Singleton

class BookFileMissingException(val filePath: String) :
    IOException("书籍本地缓存文件不存在或已被移除：$filePath")

class ChapterReadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

interface ChapterByteReader {
    fun readChapterText(
        filePath: String,
        charsetName: String,
        startByteOffset: Long,
        endByteOffset: Long,
        stripLeadingBom: Boolean = false
    ): String
}

/**
 * Random-access chapter decoder powered by [FileChannel].
 * Reads only [startByteOffset, endByteOffset) bytes from disk so memory stays bounded
 * regardless of total book size.
 */
@Singleton
class FileChannelChapterReader @Inject constructor() : ChapterByteReader {

    override fun readChapterText(
        filePath: String,
        charsetName: String,
        startByteOffset: Long,
        endByteOffset: Long,
        stripLeadingBom: Boolean
    ): String {
        if (startByteOffset < 0L || endByteOffset < startByteOffset) {
            throw ChapterReadException("非法章节字节区间：[$startByteOffset, $endByteOffset)")
        }
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw BookFileMissingException(filePath)
        }

        val charset = try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            Charsets.UTF_8
        }

        return FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            val fileSize = channel.size()
            if (startByteOffset >= fileSize) {
                return@use ""
            }
            val safeEnd = endByteOffset.coerceAtMost(fileSize)
            val byteLengthLong = safeEnd - startByteOffset
            if (byteLengthLong <= 0L) {
                return@use ""
            }
            if (byteLengthLong > MAX_CHAPTER_READ_BYTES) {
                throw ChapterReadException("单章字节大小($byteLengthLong)超出安全读取上限")
            }

            val length = byteLengthLong.toInt()
            val buffer = ByteBuffer.allocate(length)
            var position = startByteOffset
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, position)
                if (read == -1) break
                position += read
            }
            buffer.flip()

            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val decoded = decoder.decode(buffer).toString()
            if (stripLeadingBom) decoded.removePrefix("\uFEFF") else decoded
        }
    }

    companion object {
        private const val MAX_CHAPTER_READ_BYTES = 4L * 1024L * 1024L
    }
}
