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
    val completedReads: List<StoredCompletedReadTime> = emptyList(),
    /** Whether the current book is a comic (CBZ/Divina) -- wordCount/wpm/secondsLeft above are all
     *  meaningless for it (see [start]'s isComic gate), so the UI shows a page-rate time-left
     *  instead of hiding behind a misleading "0 wpm"/empty pace block. */
    val isComic: Boolean = false
)

/**
 * Ports the website's useReadingTimer/useReadingSpeedSampler (see ReadingTimer/hooks in the
 * Ishi-Read repo) to a plain lifecycle-driven controller: [onResumed]/[onPaused] stand in for the
 * website's Page Visibility API gating (tab hidden == Activity not resumed), a 1-second ticker
 * accumulates active seconds only while resumed, and a 30-second cadence flushes to the server --
 * same PERSIST_INTERVAL_MS the website uses. [flush] hands the repository only what's changed
 * since the last flush ([pendingBucketDeltas]/[pendingNewSpeedSamples]) rather than a whole
 * snapshot -- ReadingTimerRepository is local-first and durably queues/merges every write (see
 * its doc comment), so an unflushed partial 30s window survives a process death and a session that
 * starts offline can no longer clobber real history once connectivity returns.
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
    private var isComic: Boolean = false
    private var lastTotalProgression: Double? = null
    private var lastSampleSeconds: Double = 0.0
    private val speedSamples = mutableListOf<ReadingSpeedSample>()
    private val dailyBuckets = linkedMapOf<String, DailyReadingBucket>()

    /** Increments not yet handed to the repository -- cleared after each [flush]/[reset], separate
     *  from [dailyBuckets] (the full local view) and [speedSamples] (the full local WPM buffer) so
     *  only what's actually new goes over the wire. The repository does its own GET-merge-POST
     *  against the server before ever writing (see ReadingTimerReconciler), so sending a delta
     *  instead of the whole snapshot is what stops a session that started offline -- and so seeded
     *  these from an empty/zeroed read -- from clobbering real history once a flush finally
     *  reaches the server. */
    private val pendingBucketDeltas = linkedMapOf<String, DailyReadingBucket>()
    private val pendingNewSpeedSamples = mutableListOf<ReadingSpeedSample>()

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

        // CLAUDE-ADDED: A comic's readingOrder is raw bitmap bytes -- computeWordCount's UTF-8
        // decode-and-tokenize would produce a huge, meaningless "word count" from binary image data
        // (the same class of bug PAGE_COUNT_ALGORITHM_VERSION=2 fixed server-side for page counts,
        // which were coming back as raw-byte character counts for CBZ). A comic has no words, so
        // wordCount is left null rather than computed/cached -- computeSecondsLeft already returns
        // null whenever wordCount is null, and onLocatorChanged's `words != null` gate already keeps
        // a null wordCount from ever adding a sample to the global wpm buffer, so no separate isComic
        // check is needed in either place.
        isComic = publication.metadata.conformsTo.contains(Publication.Profile.DIVINA)
        wordCount = if (isComic) null else repository.getWordCount(manifestUrl).dataOrNull()
        if (!isComic && wordCount == null) {
            val computed = withContext(Dispatchers.IO) { computeWordCount(publication) }
            wordCount = computed
            repository.setWordCount(manifestUrl, computed)
        }

        val completedReads = completedReadsRepository.getCompletedReadTimes(manifestUrl).dataOrNull() ?: emptyList()

        val wpm = computeCurrentWpm(speedSamples, source = "start")
        _state.value = _state.value.copy(
            loading = false,
            accumulatedSeconds = seconds,
            wpm = wpm,
            secondsLeft = computeSecondsLeft(wordCount, wpm, lastTotalProgression),
            completedReads = completedReads.sortedByDescending { it.completedAt },
            isComic = isComic
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
        creditDanglingSecondsToToday()
        scope.launch { flush() }
    }

    /** Credits whatever's ticked up in [accumulatedSeconds][ReadingTimerUiState.accumulatedSeconds]
     *  since the last [onLocatorChanged] sample to today's daily bucket, so "Time read" and the sum
     *  of daily-history/completed-read seconds stay in agreement. Without this, any reading between
     *  the *last* locator change of a session and backing out/closing the app -- there being no
     *  guaranteed locator change after it to carry the credit, unlike every other interval -- ticks
     *  up accumulatedSeconds (every second, unconditionally, see [tick]) but is never folded into a
     *  bucket (only [onLocatorChanged] does that), permanently leaving the daily-history sum short
     *  of the lifetime total. Only touches the bucket's seconds, not words/progressionDelta -- there
     *  is no locator delta to attribute here, just elapsed time with the book open, same as a
     *  rejected sample's seconds still count in [onLocatorChanged].
     *
     *  Also clears [lastTotalProgression], forcing the *next* [onLocatorChanged] to re-seed instead
     *  of computing a sample -- this pause just spent the dangling seconds as zero-word bucket time,
     *  so diffing the next locator against the pre-pause position would credit that same span's words
     *  a second time against only the post-resume elapsed seconds (word count intact, denominator cut
     *  short), producing an inflated wpm sample. Same "discard and re-seed" the website's sampler
     *  already applies to a rejected backward-jump/big-jump, just triggered by a pause boundary
     *  instead of a bad delta. */
    private fun creditDanglingSecondsToToday() {
        val nowSeconds = _state.value.accumulatedSeconds
        val danglingSeconds = nowSeconds - lastSampleSeconds
        if (danglingSeconds <= 0) return
        lastSampleSeconds = nowSeconds
        lastTotalProgression = null

        creditBucket(LocalDate.now().toString(), seconds = danglingSeconds)
    }

    /** Applies one increment to both [dailyBuckets] (the full local view) and [pendingBucketDeltas]
     *  (what's still owed to the repository) in lockstep, so the two can never drift apart. */
    private fun creditBucket(dateKey: String, seconds: Double = 0.0, words: Double = 0.0, progressionDelta: Double = 0.0) {
        val existing = dailyBuckets[dateKey] ?: DailyReadingBucket(date = dateKey)
        dailyBuckets[dateKey] = existing.copy(
            seconds = existing.seconds + seconds,
            words = existing.words + words,
            progressionDelta = existing.progressionDelta + progressionDelta
        )

        val pending = pendingBucketDeltas[dateKey] ?: DailyReadingBucket(date = dateKey)
        pendingBucketDeltas[dateKey] = pending.copy(
            seconds = pending.seconds + seconds,
            words = pending.words + words,
            progressionDelta = pending.progressionDelta + progressionDelta
        )
    }

    private fun tick() {
        val next = _state.value.accumulatedSeconds + 1.0
        _state.value = _state.value.copy(
            accumulatedSeconds = next,
            secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples, source = "tick"), lastTotalProgression)
        )
        tickCount++
        if (tickCount % PERSIST_INTERVAL_TICKS == 0) {
            scope.launch { flush() }
        }
    }

    private suspend fun flush() {
        if (!::manifestUrl.isInitialized) return
        repository.syncReadingTimeSeconds(manifestUrl, _state.value.accumulatedSeconds)

        if (pendingBucketDeltas.isNotEmpty()) {
            repository.addDailyReadingHistoryDelta(manifestUrl, pendingBucketDeltas.values.toList())
            pendingBucketDeltas.clear()
        }
        if (pendingNewSpeedSamples.isNotEmpty()) {
            repository.addReadingSpeedSamples(pendingNewSpeedSamples.toList())
            pendingNewSpeedSamples.clear()
        }
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
     *  fix in the website's useReadingSpeedSampler.ts.
     *
     *  [exactProgression], when supplied, is a real layout-aware page/total fraction (see
     *  ReaderActivity's exactPageFraction) used in place of `locator.locations.totalProgression`
     *  for every progression/wpm computation below. Readium's totalProgression is chunk-weighted
     *  off Publication.positions, not actual rendered pages -- it can disagree with real page
     *  density enough per chapter that a wholly plausible-looking, well within every ceiling here,
     *  sample's implied wpm is wrong by multiples even though nothing about it looks rejectable.
     *  Falls back to totalProgression when null (scroll mode, or the per-resource sweep hasn't
     *  finished sweeping this book's pages yet). */
    fun onLocatorChanged(locator: Locator, exactProgression: Double? = null) {
        if (!::manifestUrl.isInitialized) return
        val totalProgression = exactProgression ?: locator.locations.totalProgression
        val previous = lastTotalProgression
        if (totalProgression != null) lastTotalProgression = totalProgression
        if (previous == null || totalProgression == null) {
            _state.value = _state.value.copy(
                secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples, source = "onLocatorChanged.reseed"), lastTotalProgression)
            )
            return
        }

        val deltaProgression = totalProgression - previous
        val nowSeconds = _state.value.accumulatedSeconds
        val deltaSeconds = nowSeconds - lastSampleSeconds
        lastSampleSeconds = nowSeconds

        if (deltaSeconds <= 0) {
            val wpm = computeCurrentWpm(speedSamples, source = "onLocatorChanged.zeroDelta")
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
            val sample = ReadingSpeedSample(deltaWords, deltaSeconds, System.currentTimeMillis().toDouble())
            speedSamples.add(sample)
            pendingNewSpeedSamples.add(sample)
            while (speedSamples.size > MAX_SPEED_SAMPLES) speedSamples.removeAt(0)
        }

        creditBucket(
            LocalDate.now().toString(),
            seconds = deltaSeconds,
            words = deltaWords,
            progressionDelta = if (acceptedSample) deltaProgression else 0.0
        )

        val wpm = computeCurrentWpm(speedSamples, source = "onLocatorChanged.sample")
        _state.value = _state.value.copy(wpm = wpm, secondsLeft = computeSecondsLeft(wordCount, wpm, lastTotalProgression))
    }

    /** Discard: zero the live counters without archiving. Save: archive the current run as a
     *  StoredCompletedReadTime first, then zero -- mirrors the website's single reset-confirm
     *  dialog (Discard vs Save), not two independent actions. */
    suspend fun reset(save: Boolean) {
        if (save && _state.value.accumulatedSeconds > 0) {
            // Same gap [onPaused] closes: reset can be triggered from the timer sheet mid-session,
            // with no pause in between to have already credited the dangling seconds -- without
            // this, item.seconds could exceed the sum of item.dailyHistory by whatever's ticked up
            // since the last locator change.
            creditDanglingSecondsToToday()
            val item = StoredCompletedReadTime(
                id = UUID.randomUUID().toString(),
                seconds = _state.value.accumulatedSeconds,
                completedAt = System.currentTimeMillis().toDouble(),
                dailyHistory = dailyBuckets.values.toList().takeIf { it.isNotEmpty() }
            )
            completedReadsRepository.saveCompletedReadTime(manifestUrl, item)
        }

        dailyBuckets.clear()
        pendingBucketDeltas.clear()
        lastSampleSeconds = 0.0
        _state.value = _state.value.copy(
            accumulatedSeconds = 0.0,
            secondsLeft = computeSecondsLeft(wordCount, computeCurrentWpm(speedSamples, source = "reset"), lastTotalProgression)
        )
        repository.resetReadingTimeSeconds(manifestUrl)
        repository.resetDailyReadingHistory(manifestUrl)

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
