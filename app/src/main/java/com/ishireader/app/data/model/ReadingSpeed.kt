package com.ishireader.app.data.model

import kotlin.math.abs

/** <5 samples: simple weighted rate. >=5: median/MAD outlier-trimmed weighted rate -- mirrors
 *  computeCurrentWpm in the website's computeReadingSpeed.ts. Shared by the live in-reader
 *  ReadingTimerTracker and BookDetailViewModel's own point-in-time estimate, so both agree on
 *  the same pace for the same sample buffer. */
fun computeCurrentWpm(speedSamples: List<ReadingSpeedSample>): Int? {
    if (speedSamples.isEmpty()) return null
    val survivors = if (speedSamples.size < 5) {
        speedSamples
    } else {
        val rates = speedSamples.map { it.deltaWords / (it.deltaSeconds / 60.0) }
        val median = medianOf(rates.sorted())
        val mad = medianOf(rates.map { abs(it - median) }.sorted())
        if (mad > 0) {
            speedSamples.filterIndexed { i, _ -> abs(rates[i] - median) / mad <= 2.5 }
        } else {
            speedSamples
        }
    }
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
 *  the website's computeReadingSpeed.ts. */
fun computeSecondsLeft(wordCount: Double?, wpm: Int?, totalProgression: Double?): Double? {
    val words = wordCount ?: return null
    if (wpm == null || wpm <= 0) return null
    val progression = totalProgression ?: 0.0
    val wordsRemaining = words * (1.0 - progression)
    return wordsRemaining / wpm * 60.0
}
