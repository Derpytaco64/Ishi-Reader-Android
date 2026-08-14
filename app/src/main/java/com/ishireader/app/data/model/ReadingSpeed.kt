package com.ishireader.app.data.model

import kotlin.math.abs
import timber.log.Timber

/** Hard sanity ceiling on a single sample's implied wpm, applied before the median/MAD trim below.
 *  Median/MAD is a "majority wins" statistic -- it has no way to know which side of a split is the
 *  real reading pace, so if enough inhuman-fast samples (e.g. flipping past a book's cover/title
 *  pages) land in the buffer together, they can outnumber genuine reading and make *that* the
 *  "outlier" that gets trimmed instead. This ceiling removes anything no human reading pace could
 *  produce before the trim ever runs, so it can't be out-voted by a cluster of bad data. */
private const val PLAUSIBLE_WPM_CEILING = 600.0

/** <5 samples: simple weighted rate. >=5: median/MAD outlier-trimmed weighted rate -- mirrors
 *  computeCurrentWpm in the website's computeReadingSpeed.ts. Shared by the live in-reader
 *  ReadingTimerTracker and BookDetailViewModel's own point-in-time estimate, so both agree on
 *  the same pace for the same sample buffer. */
fun computeCurrentWpm(speedSamples: List<ReadingSpeedSample>): Int? {
    if (speedSamples.isEmpty()) return null

    val rates = speedSamples.map { it.deltaWords / (it.deltaSeconds / 60.0) }
    val plausibleIndices = rates.indices.filter { rates[it] in 0.0..PLAUSIBLE_WPM_CEILING }
    // CLAUDE-ADDED: If literally every sample is above the ceiling (a consistently very fast
    // reader), fall back to the full buffer rather than returning nothing -- the ceiling is meant
    // to stop a minority of bad data from out-voting good data, not to cap a real reader's pace.
    val poolIndices = if (plausibleIndices.isNotEmpty()) plausibleIndices else rates.indices.toList()

    Timber.tag("WpmDebug").d(
        "buffer=%d plausible(<=%.0fwpm)=%d rates=[%s]",
        speedSamples.size, PLAUSIBLE_WPM_CEILING, plausibleIndices.size,
        rates.joinToString { "%.0f".format(it) }
    )

    val survivorIndices = if (poolIndices.size < 5) {
        poolIndices
    } else {
        val poolRates = poolIndices.map { rates[it] }
        val median = medianOf(poolRates.sorted())
        val mad = medianOf(poolRates.map { abs(it - median) }.sorted())
        val kept = if (mad > 0) {
            poolIndices.filterIndexed { i, _ -> abs(poolRates[i] - median) / mad <= 2.5 }
        } else {
            poolIndices
        }
        Timber.tag("WpmDebug").d(
            "median=%.0f mad=%.0f survivors=%d/%d", median, mad, kept.size, poolIndices.size
        )
        kept
    }

    val survivors = survivorIndices.map { speedSamples[it] }
    val totalWords = survivors.sumOf { it.deltaWords }
    val totalMinutes = survivors.sumOf { it.deltaSeconds } / 60.0
    return if (totalMinutes > 0) (totalWords / totalMinutes).toInt() else null
}

private fun medianOf(sorted: List<Double>): Double {
    if (sorted.isEmpty()) return 0.0
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/** Estimated seconds remaining in the book at the given pace -- mirrors estimateSecondsLeft in
 *  the website's computeReadingSpeed.ts. [totalProgression] null means "position not known yet"
 *  (e.g. the reader hasn't reported its first locator this session) -- treating that as 0% would
 *  estimate against the *entire* book's word count, wildly overstating time left until a real
 *  locator arrives. The website's own caller guards this the same way (`currentProgression !==
 *  null` in StatefulReadingTimerContainer.tsx) rather than defaulting inside the helper. */
fun computeSecondsLeft(wordCount: Double?, wpm: Int?, totalProgression: Double?): Double? {
    val words = wordCount ?: return null
    if (wpm == null || wpm <= 0) return null
    val progression = totalProgression ?: return null
    val wordsRemaining = words * (1.0 - progression)
    return wordsRemaining / wpm * 60.0
}
