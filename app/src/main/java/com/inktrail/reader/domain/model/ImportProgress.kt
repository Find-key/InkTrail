package com.inktrail.reader.domain.model

/**
 * Progress states emitted during local TXT book import.
 */
sealed interface ImportProgress {
    data class Copying(
        val bytesCopied: Long,
        val totalBytes: Long?
    ) : ImportProgress

    data object DetectingEncoding : ImportProgress

    data object IndexingChapters : ImportProgress

    data object SavingToDatabase : ImportProgress

    data class Completed(val book: Book) : ImportProgress

    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : ImportProgress
}
