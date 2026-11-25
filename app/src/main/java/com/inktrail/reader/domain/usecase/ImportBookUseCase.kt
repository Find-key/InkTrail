package com.inktrail.reader.domain.usecase

import com.inktrail.reader.data.local.storage.BookFileStorage
import com.inktrail.reader.data.local.storage.CopiedBookFile
import com.inktrail.reader.data.local.storage.EmptyBookException
import com.inktrail.reader.di.IoDispatcher
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.ImportProgress
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.engine.encoding.CharsetDetector
import com.inktrail.reader.engine.splitter.ChapterSplitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject

/**
 * Orchestrates the local TXT import pipeline:
 * 1. Copy SAF content stream to app-private storage (`filesDir/books/{bookId}.txt`)
 * 2. Auto-detect encoding from the first 64KB
 * 3. Build byte-offset chapter index via streaming [ChapterSplitter]
 * 4. Persist book and chapters atomically into Room
 */
class ImportBookUseCase @Inject constructor(
    private val bookFileStorage: BookFileStorage,
    private val bookRepository: BookRepository,
    private val charsetDetector: CharsetDetector,
    private val chapterSplitter: ChapterSplitter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(sourceUri: String): Flow<ImportProgress> = channelFlow {
        val bookId = UUID.randomUUID().toString()
        var copiedFile: CopiedBookFile? = null
        var lastReportedBytes = -COPY_PROGRESS_STEP_BYTES

        try {
            send(ImportProgress.Copying(bytesCopied = 0L, totalBytes = null))
            val copied = bookFileStorage.copyFromUri(
                uriString = sourceUri,
                bookId = bookId
            ) { bytesCopied, totalBytes ->
                if (bytesCopied - lastReportedBytes >= COPY_PROGRESS_STEP_BYTES ||
                    (totalBytes != null && bytesCopied >= totalBytes)
                ) {
                    lastReportedBytes = bytesCopied
                    trySend(ImportProgress.Copying(bytesCopied, totalBytes))
                }
            }
            copiedFile = copied

            ensureActive()
            send(ImportProgress.DetectingEncoding)
            val detection = bookFileStorage.openInputStream(copied.filePath).use { stream ->
                charsetDetector.detect(stream)
            }

            ensureActive()
            send(ImportProgress.IndexingChapters)
            val slices = bookFileStorage.openInputStream(copied.filePath).use { stream ->
                chapterSplitter.split(
                    inputStream = stream,
                    charsetName = detection.charsetName,
                    bomLength = detection.bomLength
                )
            }
            if (slices.isEmpty()) {
                throw EmptyBookException("文本文件为空或不包含有效章节内容")
            }

            ensureActive()
            send(ImportProgress.SavingToDatabase)
            val now = System.currentTimeMillis()
            val book = Book(
                id = bookId,
                title = copied.title,
                filePath = copied.filePath,
                fileSizeBytes = copied.fileSizeBytes,
                charsetName = detection.charsetName,
                totalChapters = slices.size,
                currentChapterIndex = 0,
                currentCharOffset = 0,
                lastReadAt = now,
                createdAt = now
            )
            val chapters = slices.map { slice ->
                Chapter(
                    bookId = bookId,
                    chapterIndex = slice.chapterIndex,
                    title = slice.title,
                    startByteOffset = slice.startByteOffset,
                    endByteOffset = slice.endByteOffset
                )
            }
            bookRepository.saveImportedBook(book, chapters)

            send(ImportProgress.Completed(book))
        } catch (ce: CancellationException) {
            copiedFile?.let { bookFileStorage.deleteBookFile(it.filePath) }
            throw ce
        } catch (t: Throwable) {
            copiedFile?.let { bookFileStorage.deleteBookFile(it.filePath) }
            send(
                ImportProgress.Failed(
                    message = t.message ?: "导入书籍失败，请确认文件格式或存储权限",
                    cause = t
                )
            )
        }
    }.flowOn(ioDispatcher)

    companion object {
        private const val COPY_PROGRESS_STEP_BYTES = 64 * 1024L
    }
}
