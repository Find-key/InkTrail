package com.inktrail.reader.engine.paginator

/**
 * Represents a single rendered page slice within a chapter.
 * Character offsets form a half-open interval [startCharOffset, endCharOffset) in the chapter text.
 */
data class PageSlice(
    val pageIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val text: String
)

/**
 * UI-decoupled measurement callback. Implementations (e.g., Compose TextMeasurer in the UI layer
 * or mock/fake implementations in unit tests) determine how many characters fit on one page
 * starting from [startOffset].
 */
fun interface MeasureCallback {
    /**
     * Returns the exclusive end character offset in [text] (> [startOffset], <= [text].length)
     * that fits inside the available page viewport when rendering from [startOffset].
     */
    fun measurePageEnd(text: String, startOffset: Int): Int
}

/**
 * Pure Kotlin logical paginator that splits chapter text into contiguous [PageSlice] items
 * and resolves reading anchor offsets back to page indices across layout reflows.
 */
class Paginator {

    fun paginate(
        text: String,
        measureCallback: MeasureCallback
    ): List<PageSlice> {
        if (text.isEmpty()) return emptyList()

        val pages = ArrayList<PageSlice>()
        var start = 0
        while (start < text.length) {
            var end = measureCallback.measurePageEnd(text, start).coerceAtMost(text.length)

            if (end <= start) {
                end = advanceAtLeastOneCodePoint(text, start)
            } else if (end < text.length && isSplittingSurrogatePair(text, end)) {
                end = if (end - 1 > start) end - 1 else advanceAtLeastOneCodePoint(text, start)
            }

            pages += PageSlice(
                pageIndex = pages.size,
                startCharOffset = start,
                endCharOffset = end,
                text = text.substring(start, end)
            )
            start = end
        }
        return pages
    }

    /**
     * Locates the page index containing [charOffset] via binary search so reflowing text
     * (e.g., changing font size or line spacing) keeps the reader anchored on the same sentence.
     */
    fun findPageIndexForOffset(
        pages: List<PageSlice>,
        charOffset: Int
    ): Int {
        if (pages.isEmpty()) return 0
        if (charOffset <= pages.first().startCharOffset) return 0
        if (charOffset >= pages.last().endCharOffset) return pages.lastIndex

        var low = 0
        var high = pages.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val slice = pages[mid]
            when {
                charOffset < slice.startCharOffset -> high = mid - 1
                charOffset >= slice.endCharOffset -> low = mid + 1
                else -> return mid
            }
        }
        return low.coerceIn(0, pages.lastIndex)
    }

    private fun advanceAtLeastOneCodePoint(text: String, start: Int): Int {
        if (start + 1 < text.length && Character.isHighSurrogate(text[start]) && Character.isLowSurrogate(text[start + 1])) {
            return start + 2
        }
        return (start + 1).coerceAtMost(text.length)
    }

    private fun isSplittingSurrogatePair(text: String, splitIndex: Int): Boolean {
        if (splitIndex <= 0 || splitIndex >= text.length) return false
        return Character.isHighSurrogate(text[splitIndex - 1]) && Character.isLowSurrogate(text[splitIndex])
    }
}
