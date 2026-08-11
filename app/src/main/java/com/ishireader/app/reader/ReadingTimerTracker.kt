package com.ishireader.app.reader

import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.ReadingSpeedSample
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.model.computeCurrentWpm
import com.ishireader.app.data.model.computeSecondsLeft
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.CompletedReadsRepository
import com.ishireader.app.data.repository.ReadingTimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.use
import java.time.LocalDate
import java.util.UUID

data class ReadingTimerUiState(
    val loading: Boolean = true,
    val accumulatedSeconds: Double = 0.0,
    val running: Boolean = false,
    val wpm: Int? = null,
    val secondsLeft: Double? = null,
    val completedReads: List<StoredCompletedReadTime> = emptyList()
)

/**
 * Ports the website's useReadingTimer/useReadingSpeedSampler (see ReadingTimer/hooks in the
 * Ishi-Read repo) to a plain lifecycle-driven controller: [onResumed]/[onPaused] stand in for the
 * website's Page Visibility API gating (tab hidden == Activity not resumed), a 1-second ticker
 * accumulates active seconds only while resumed, and a 30-second cadence flushes to the server --
 * same PERSIST_INTERVAL_MS the website uses. Network-first, no local Room buffering (see
 * ReadingTimerRepository's doc comment) -- an unflushed partial 30s window can be lost if the
 * process dies, same fidelity as the website losing an unsaved beforeunload race.
 */
class ReadingTimerTracker(
    private val scope: CoroutineScope,
    private val repository: ReadingTimerRepository,
    private val completedReadsRepository: CompletedReadsRepository
) {
    private companion object {
        const val PERSIST_INTERVAL_TICKS = 30
        const val JUMP_DISCARD_THRESHOLD = 0.05
        const val RAPID_TURN_WPM_CEILING = 2500.0
        const val MAX_SPEED_SAMPLES = 50
    }

    private val _state = MutableStateFlow(ReadingTimerUiState())
    val state: StateFlow<ReadingTimerUiState> = _state.asStateFlow()

    private lateinit var manifestUrl: String
    private var wordCount: Double? = null
    private var lastTotalProgression: Double? = null
    private var lastSampleSeconds: Double = 0.0
    private val speedSamples = mutableListOf<ReadingSpeedSample>()
    private val dailyBuckets = linkedMapOf<String, DailyReadingBucket>()

    private var tickerJob: Job? = null
    private var tickCount = 0

    /** Loads server-side state for this book (resumes the live counter across devices/sessions,
     *  since readingTime is a single overwritten total, not append-only) and the cross-book WPM
     *  sample buffer. Call once when the navigator is ready, before [onResumed]. */
    suspend fun start(manifestUrl: String, publication: Publication) {
        this.manifestUrl = manifestUrl
        _state.value = _state.value.copy(loading = true)

        val seconds = repository.getReadingTimeSeconds(manifestUrl).dataOrNull() ?: 0.0
        val buckets = repository.getDailyReadingHistory(manifestUrl).dataOrNull() ?: emptyList()
        dailyBuckets.clear()
        buckets.forEach { dailyBuckets[it.date] = it }
        speedSamples.clear()
        speedSamples.addAll(repository.getReadingSpeedSamples().dataOrNull() ?: emptyList())
        lastSampleSeconds = seconds

        wordCount = repository.getWordCount(manifestUrl).dataOrNull()
        if (wordCount == null) {
            val computed = withContext(Dispatchers.IO) { computeWordCount(publication) }
            wordCount = computed
            repository.setWordCount(manifestUrl, computed)
        }

        val completedReads = completedReadsRepository.getCompletedReadTimes(manifestUrl).dataOrNull() ?: emptyList()

        _state.value = _state.value.copy(
            loading = false,
            accumulatedSeconds = seconds,
            wpm = computeCurrentWpm(speedSamples),
            secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples), lastTotalProgression),
            completedReads = completedReads.sortedByDescending { it.completedAt }
        )
    }

    /** Starts the 1s ticker -- call from the host Activity's onResume. */
    fun onResumed() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
        _state.value = _state.value.copy(running = true)
    }

    /** Stops the ticker and flushes -- call from the host Activity's onPause. */
    fun onPaused() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(running = false)
        scope.launch { flush() }
    }

    private fun tick() {
        val next = _state.value.accumulatedSeconds + 1.0
        _state.value = _state.value.copy(
            accumulatedSeconds = next,
            secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples), lastTotalProgression)
        )
        tickCount++
        if (tickCount % PERSIST_INTERVAL_TICKS == 0) {
            scope.launch { flush() }
        }
    }

    private suspend fun flush() {
        if (!::manifestUrl.isInitialized) return
        repository.setReadingTimeSeconds(manifestUrl, _state.value.accumulatedSeconds)
        repository.setDailyReadingHistory(manifestUrl, dailyBuckets.values.toList())
        repository.setReadingSpeedSamples(speedSamples)
    }

    /** Feed every navigator locator change -- mirrors notifyLocatorChanged/computeReadingSpeed.ts.
     *  Two separate ledgers come out of this: the rolling WPM buffer (speedSamples) and today's
     *  daily bucket. Only "organic forward reading" intervals -- not backward jumps, big TOC/search
     *  jumps, or implausibly fast "turns" (mashing next-page) -- count toward the former, since
     *  folding those in would corrupt the WPM estimate. The daily bucket's *seconds* are credited
     *  unconditionally regardless of any of that: a backward flip or a TOC jump is still real time
     *  spent with the book open, and accumulatedSeconds counts it unconditionally too. Gating the
     *  daily-bucket seconds on the same accept/reject check as the WPM sample (as this used to)
     *  silently left the daily-history sum short of the lifetime total by however much reading
     *  happened to involve navigation rather than straight-through page turns -- see the equivalent
     *  fix in the website's useReadingSpeedSampler.ts. */
    fun onLocatorChanged(locator: Locator) {
        if (!::manifestUrl.isInitialized) return
        val totalProgression = locator.locations.totalProgression
        val previous = lastTotalProgression
        if (totalProgression != null) lastTotalProgression = totalProgression
        if (previous == null || totalProgression == null) {
            _state.value = _state.value.copy(
                secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples), lastTotalProgression)
            )
            return
        }

        val deltaProgression = totalProgression - previous
        val nowSeconds = _state.value.accumulatedSeconds
        val deltaSeconds = nowSeconds - lastSampleSeconds
        lastSampleSeconds = nowSeconds

        if (deltaSeconds <= 0) {
            val wpm = computeCurrentWpm(speedSamples)
            _state.value = _state.value.copy(wpm = wpm, secondsLeft = computeSecondsLeft(wordCount, wpm, lastTotalProgression))
            return
        }

        val words = wordCount
        var deltaWords = 0.0
        var acceptedSample = false
        if (words != null && deltaProgression > 0 && deltaProgression <= JUMP_DISCARD_THRESHOLD) {
            val candidateWords = deltaProgression * words
            val impliedWpm = candidateWords / (deltaSeconds / 60.0)
            if (impliedWpm <= RAPID_TURN_WPM_CEILING) {
                deltaWords = candidateWords
                acceptedSample = true
            }
        }

        if (acceptedSample) {
            speedSamples.add(ReadingSpeedSample(deltaWords, deltaSeconds, System.currentTimeMillis().toDouble()))
            while (speedSamples.size > MAX_SPEED_SAMPLES) speedSamples.removeAt(0)
        }

        val dateKey = LocalDate.now().toString()
        val existing = dailyBuckets[dateKey] ?: DailyReadingBucket(date = dateKey)
        dailyBuckets[dateKey] = existing.copy(
            seconds = existing.seconds + deltaSeconds,
            words = existing.words + deltaWords,
            progressionDelta = existing.progressionDelta + (if (acceptedSample) deltaProgression else 0.0)
        )

        val wpm = computeCurrentWpm(speedSamples)
        _state.value = _state.value.copy(wpm = wpm, secondsLeft = computeSecondsLeft(wordCount, wpm, lastTotalProgression))
    }

    /** Discard: zero the live counters without archiving. Save: archive the current run as a
     *  StoredCompletedReadTime first, then zero -- mirrors the website's single reset-confirm
     *  dialog (Discard vs Save), not two independent actions. */
    suspend fun reset(save: Boolean) {
        if (save && _state.value.accumulatedSeconds > 0) {
            val item = StoredCompletedReadTime(
                id = UUID.randomUUID().toString(),
                seconds = _state.value.accumulatedSeconds,
                completedAt = System.currentTimeMillis().toDouble(),
                dailyHistory = dailyBuckets.values.toList().takeIf { it.isNotEmpty() }
            )
            completedReadsRepository.saveCompletedReadTime(manifestUrl, item)
        }

        dailyBuckets.clear()
        lastSampleSeconds = 0.0
        _state.value = _state.value.copy(
            accumulatedSeconds = 0.0,
            secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples), lastTotalProgression)
        )
        repository.setReadingTimeSeconds(manifestUrl, 0.0)
        repository.setDailyReadingHistory(manifestUrl, emptyList())

        val completedReads = completedReadsRepository.getCompletedReadTimes(manifestUrl).dataOrNull() ?: emptyList()
        _state.value = _state.value.copy(completedReads = completedReads.sortedByDescending { it.completedAt })
    }

    suspend fun deleteCompletedRead(id: String) {
        completedReadsRepository.deleteCompletedReadTime(manifestUrl, id)
        _state.value = _state.value.copy(completedReads = _state.value.completedReads.filterNot { it.id == id })
    }

    /** Whitespace-token count of every reading-order resource's text, HTML tags/entities stripped
     *  -- mirrors useBookWordCount.ts. No layout/rendering involved, just raw text extraction. */
    private suspend fun computeWordCount(publication: Publication): Double {
        var total = 0.0
        for (link in publication.readingOrder) {
            val resource = publication.get(link) ?: continue
            val bytes = resource.use { it.read().getOrNull() } ?: continue
            val html = String(bytes, Charsets.UTF_8)
            val text = html
                .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&\\w+;"), " ")
            total += text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        }
        return total
    }
}
