package com.inktrail.reader.data.local.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class CopiedBookFile(
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long
)

class EmptyBookException(message: String = "导入的文本文件为空") : IOException(message)

interface BookFileStorage {
    fun copyFromUri(
        uriString: String,
        bookId: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): CopiedBookFile

    fun openInputStream(filePath: String): InputStream

    fun deleteBookFile(filePath: String): Boolean
}

@Singleton
class AndroidBookFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : BookFileStorage {

    override fun copyFromUri(
        uriString: String,
        bookId: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit
    ): CopiedBookFile {
        val uri = Uri.parse(uriString)
        val booksDir = File(context.filesDir, BOOKS_DIR_NAME).apply { mkdirs() }
        val targetFile = File(booksDir, "$bookId.txt")

        val metadata = queryMetadata(uri)
        val rawTitle = metadata.displayName ?: uri.lastPathSegment ?: DEFAULT_BOOK_TITLE
        val cleanTitle = deriveBookTitle(rawTitle)

        var totalCopied = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开所选文件，可能缺少读取权限")

            input.use { source ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    onProgress(0L, metadata.sizeBytes)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalCopied += read
                        onProgress(totalCopied, metadata.sizeBytes)
                    }
                    output.flush()
                }
            }

            if (totalCopied <= 0L) {
                targetFile.delete()
                throw EmptyBookException()
            }

            return CopiedBookFile(
                title = cleanTitle,
                filePath = targetFile.absolutePath,
                fileSizeBytes = totalCopied
            )
        } catch (t: Throwable) {
            targetFile.delete()
            throw t
        }
    }

    override fun openInputStream(filePath: String): InputStream {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw BookFileMissingException(filePath)
        }
        return BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE)
    }

    override fun deleteBookFile(filePath: String): Boolean {
        val file = File(filePath)
        return if (file.exists()) file.delete() else true
    }

    private data class UriMetadata(
        val displayName: String?,
        val sizeBytes: Long?
    )

    private fun queryMetadata(uri: Uri): UriMetadata {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return UriMetadata(null, null)
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                UriMetadata(name, size?.takeIf { it > 0L })
            } ?: UriMetadata(null, null)
        } catch (_: Exception) {
            UriMetadata(null, null)
        }
    }

    companion object {
        private const val BOOKS_DIR_NAME = "books"
        private const val DEFAULT_BOOK_TITLE = "未命名书籍"
        private const val COPY_BUFFER_SIZE = 16 * 1024
        private const val STREAM_BUFFER_SIZE = 16 * 1024

        internal fun deriveBookTitle(rawName: String): String {
            val trimmed = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
            val stripped = if (trimmed.endsWith(".txt", ignoreCase = true)) {
                trimmed.dropLast(4).trim()
            } else {
                trimmed
            }
            return stripped.ifEmpty { DEFAULT_BOOK_TITLE }
        }
    }
}
