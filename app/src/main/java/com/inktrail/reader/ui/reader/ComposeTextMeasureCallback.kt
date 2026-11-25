package com.inktrail.reader.ui.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.inktrail.reader.engine.paginator.MeasureCallback
import kotlin.math.roundToInt

/**
 * A single paragraph slice inside a rendered [com.inktrail.reader.domain.model.PageSlice],
 * preserving exact character offsets relative to the page slice text.
 */
data class PageParagraph(
    val startOffsetInSlice: Int,
    val endOffsetInSlice: Int,
    val text: String,
    val isBlankLine: Boolean
)

/**
 * Jetpack Compose [TextMeasurer] implementation of [MeasureCallback] that calculates
 * paragraph-aware page boundaries matching [splitPageParagraphs] pixel-for-pixel.
 */
class ComposeTextMeasureCallback(
    private val textMeasurer: TextMeasurer,
    private val bodyTextStyle: TextStyle,
    private val contentWidthPx: Int,
    private val contentHeightPx: Int,
    private val paragraphSpacingPx: Float,
    private val blankLineHeightPx: Float
) : MeasureCallback {

    override fun measurePageEnd(text: String, startOffset: Int): Int {
        if (startOffset >= text.length) return text.length
        if (contentWidthPx <= 0 || contentHeightPx <= 0) {
            return minOf(text.length, startOffset + FALLBACK_CHARS)
        }

        val maxHeight = contentHeightPx.toFloat()
        var usedHeight = 0f
        var pos = startOffset

        while (pos < text.length) {
            val newlineIdx = text.indexOf('\n', startIndex = pos)
            val paraEnd = if (newlineIdx >= 0) newlineIdx else text.length
            val nextPosAfterNewline = if (newlineIdx >= 0) newlineIdx + 1 else text.length

            if (pos == paraEnd) {
                // Empty line at current offset: skip top-of-page blank gap without losing offsets.
                if (usedHeight > 0f) {
                    if (usedHeight + blankLineHeightPx > maxHeight) {
                        return pos
                    }
                    usedHeight += blankLineHeightPx
                }
                pos = nextPosAfterNewline
                continue
            }

            val windowEnd = minOf(paraEnd, pos + MAX_PARAGRAPH_WINDOW_CHARS)
            val segment = text.substring(pos, windowEnd)
            val layout = textMeasurer.measure(
                text = segment,
                style = bodyTextStyle,
                constraints = Constraints(maxWidth = contentWidthPx)
            )

            for (lineIdx in 0 until layout.lineCount) {
                val lineHeight = layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)
                if (usedHeight > 0f && usedHeight + lineHeight > maxHeight) {
                    val fittedLocalEnd = if (lineIdx == 0) 0 else layout.getLineEnd(lineIdx - 1, visibleEnd = false)
                    val candidateEnd = pos + fittedLocalEnd
                    return if (candidateEnd > startOffset) {
                        candidateEnd
                    } else {
                        (pos + layout.getLineEnd(0, visibleEnd = false))
                            .coerceAtLeast(startOffset + 1)
                            .coerceAtMost(text.length)
                    }
                }
                usedHeight += lineHeight
            }

            if (windowEnd < paraEnd) {
                pos = windowEnd
                continue
            }

            pos = nextPosAfterNewline
            if (pos < text.length) {
                usedHeight += paragraphSpacingPx
            }
        }

        return pos.coerceAtLeast(startOffset + 1).coerceAtMost(text.length)
    }

    companion object {
        private const val MAX_PARAGRAPH_WINDOW_CHARS = 3500
        private const val FALLBACK_CHARS = 400

        fun computeBlankLineHeightPx(fontSizePx: Float, lineHeightMultiplier: Float, paragraphSpacingPx: Float): Float {
            val singleLinePx = fontSizePx * lineHeightMultiplier
            return maxOf(paragraphSpacingPx * 0.65f, singleLinePx * 0.45f).roundToInt().toFloat()
        }

        /**
         * Splits a [PageSlice.text] along '\n' boundaries into display paragraphs while
         * tracking exact slice-relative character offsets and skipping leading empty gaps.
         */
        fun splitPageParagraphs(sliceText: String): List<PageParagraph> {
            if (sliceText.isEmpty()) return emptyList()

            val result = ArrayList<PageParagraph>()
            var pos = 0
            var hasSeenContent = false

            while (pos < sliceText.length) {
                val newlineIdx = sliceText.indexOf('\n', startIndex = pos)
                val paraEnd = if (newlineIdx >= 0) newlineIdx else sliceText.length
                val nextPos = if (newlineIdx >= 0) newlineIdx + 1 else sliceText.length

                if (pos == paraEnd) {
                    if (hasSeenContent && nextPos < sliceText.length) {
                        result += PageParagraph(
                            startOffsetInSlice = pos,
                            endOffsetInSlice = pos,
                            text = "",
                            isBlankLine = true
                        )
                    }
                } else {
                    hasSeenContent = true
                    result += PageParagraph(
                        startOffsetInSlice = pos,
                        endOffsetInSlice = paraEnd,
                        text = sliceText.substring(pos, paraEnd),
                        isBlankLine = false
                    )
                }
                pos = nextPos
            }
            return result
        }
    }
}
