package com.inktrail.reader.engine.search

/**
 * Current reading position used as the anti-spoiler boundary.
 */
data class ReadingAnchor(
    val chapterIndex: Int,
    val charOffset: Int
)

/**
 * Strict progress-aware filter preventing future chapter or ahead-of-cursor text spoilers.
 * Spec rule when [spoilerFreeMode] is true:
 * `(chapterIndex < anchor.chapterIndex) || (chapterIndex == anchor.chapterIndex && matchEndOffset <= anchor.charOffset)`
 */
object SpoilerFreeFilter {

    fun isAllowed(
        chapterIndex: Int,
        matchEndOffset: Int,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean
    ): Boolean {
        if (!spoilerFreeMode) return true
        return (chapterIndex < anchor.chapterIndex) ||
            (chapterIndex == anchor.chapterIndex && matchEndOffset <= anchor.charOffset)
    }

    fun <T> filter(
        items: List<T>,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean,
        chapterIndexOf: (T) -> Int,
        endOffsetOf: (T) -> Int
    ): List<T> {
        if (!spoilerFreeMode) return items
        return items.filter { item ->
            isAllowed(
                chapterIndex = chapterIndexOf(item),
                matchEndOffset = endOffsetOf(item),
                anchor = anchor,
                spoilerFreeMode = true
            )
        }
    }
}

/**
 * A single keyword search match with surrounding context snippet and relative highlight offsets.
 */
data class SearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val matchStartOffset: Int,
    val matchEndOffset: Int,
    val snippet: String,
    val highlightStartInSnippet: Int,
    val highlightEndInSnippet: Int
)

/**
 * Lazy chapter text loader so streaming book search only decodes chapters as needed.
 */
data class ChapterTextSource(
    val chapterIndex: Int,
    val title: String,
    val loadText: () -> String
)

/**
 * Streaming chapter-by-chapter book search engine with context snippet extraction
 * and anti-spoiler boundary enforcement.
 */
class BookSearchEngine {

    fun searchInChapter(
        chapterIndex: Int,
        chapterTitle: String,
        chapterText: String,
        query: String,
        anchor: ReadingAnchor? = null,
        spoilerFreeMode: Boolean = false,
        ignoreCase: Boolean = true,
        contextBefore: Int = DEFAULT_CONTEXT_BEFORE,
        contextAfter: Int = DEFAULT_CONTEXT_AFTER
    ): List<SearchHit> {
        if (query.isEmpty() || chapterText.isEmpty()) return emptyList()
        if (spoilerFreeMode && anchor != null && chapterIndex > anchor.chapterIndex) {
            return emptyList()
        }

        val hits = ArrayList<SearchHit>()
        var searchFrom = 0
        while (searchFrom <= chapterText.length - query.length) {
            val matchStart = chapterText.indexOf(query, startIndex = searchFrom, ignoreCase = ignoreCase)
            if (matchStart < 0) break
            val matchEnd = matchStart + query.length

            if (spoilerFreeMode && anchor != null &&
                !SpoilerFreeFilter.isAllowed(chapterIndex, matchEnd, anchor, spoilerFreeMode = true)
            ) {
                break
            }

            val snippetData = extractSnippet(
                text = chapterText,
                matchStart = matchStart,
                matchEnd = matchEnd,
                contextBefore = contextBefore,
                contextAfter = contextAfter
            )
            hits += SearchHit(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                matchStartOffset = matchStart,
                matchEndOffset = matchEnd,
                snippet = snippetData.snippet,
                highlightStartInSnippet = snippetData.highlightStart,
                highlightEndInSnippet = snippetData.highlightEnd
            )
            searchFrom = matchStart + query.length.coerceAtLeast(1)
        }
        return hits
    }

    fun searchBook(
        chapters: Sequence<ChapterTextSource>,
        query: String,
        anchor: ReadingAnchor,
        spoilerFreeMode: Boolean,
        ignoreCase: Boolean = true,
        chaptersOrderedByIndex: Boolean = true,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        contextBefore: Int = DEFAULT_CONTEXT_BEFORE,
        contextAfter: Int = DEFAULT_CONTEXT_AFTER
    ): List<SearchHit> {
        if (query.isEmpty() || maxResults <= 0) return emptyList()

        val results = ArrayList<SearchHit>()
        for (chapter in chapters) {
            if (spoilerFreeMode && chapter.chapterIndex > anchor.chapterIndex) {
                if (chaptersOrderedByIndex) break else continue
            }

            val chapterText = chapter.loadText()
            if (chapterText.isEmpty()) continue

            val chapterHits = searchInChapter(
                chapterIndex = chapter.chapterIndex,
                chapterTitle = chapter.title,
                chapterText = chapterText,
                query = query,
                anchor = anchor,
                spoilerFreeMode = spoilerFreeMode,
                ignoreCase = ignoreCase,
                contextBefore = contextBefore,
                contextAfter = contextAfter
            )
            for (hit in chapterHits) {
                results += hit
                if (results.size >= maxResults) return results
            }
        }
        return results
    }

    private data class SnippetResult(
        val snippet: String,
        val highlightStart: Int,
        val highlightEnd: Int
    )

    private fun extractSnippet(
        text: String,
        matchStart: Int,
        matchEnd: Int,
        contextBefore: Int,
        contextAfter: Int
    ): SnippetResult {
        val windowStart = (matchStart - contextBefore).coerceAtLeast(0)
        val windowEnd = (matchEnd + contextAfter).coerceAtMost(text.length)

        val sb = StringBuilder((windowEnd - windowStart) + 2)
        if (windowStart > 0) {
            sb.append('…')
        }

        val highlightStart = sb.length + (matchStart - windowStart)
        val highlightEnd = highlightStart + (matchEnd - matchStart)

        for (i in windowStart until windowEnd) {
            val c = text[i]
            sb.append(
                if (c == '\n' || c == '\r' || c == '\t') ' ' else c
            )
        }

        if (windowEnd < text.length) {
            sb.append('…')
        }

        return SnippetResult(
            snippet = sb.toString(),
            highlightStart = highlightStart,
            highlightEnd = highlightEnd
        )
    }

    companion object {
        const val DEFAULT_CONTEXT_BEFORE = 20
        const val DEFAULT_CONTEXT_AFTER = 36
        const val DEFAULT_MAX_RESULTS = 500
    }
}
