package com.ishireader.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.StoredBookmark
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.model.StoredHighlight
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.model.computeCurrentWpm
import com.ishireader.app.data.model.computeSecondsLeft
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.data.model.progressionFromLocator
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.AnnotationsRepository
import com.ishireader.app.data.repository.CompletedReadsRepository
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.PositionRepository
import com.ishireader.app.data.repository.ReadingTimerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookDetailUiState(
    /** 0..100, one decimal place to match the website's rounding; null = no progress yet (dial hidden). */
    val percentRead: Double? = null,
    val notes: List<StoredNote> = emptyList(),
    val highlights: List<StoredHighlight> = emptyList(),
    val bookmarks: List<StoredBookmark> = emptyList(),
    /** Most recent entry from the reader's own Completed tab -- mirrors StatefulBookSheet.tsx's
     *  lastCompletedRead (max by completedAt, not array order). */
    val lastCompletedRead: StoredCompletedReadTime? = null,
    /** Full history, most-recent-first -- unlike [lastCompletedRead] (the single-card summary this
     *  screen already showed before delete/reset support was added), this backs the management
     *  sheet's Completed tab, same list shape the reader's own ReadingTimerSheet shows. */
    val completedReads: List<StoredCompletedReadTime> = emptyList(),
    /** This book's own "Reading Timer" figures -- mirrors StatefulBookSheet.tsx's ReadingStats
     *  (totalSeconds/wpm/secondsLeft), a point-in-time snapshot rather than the reader's own live
     *  ticking counter (this screen isn't open while actually reading). Null fields hide their row. */
    val totalReadingSeconds: Double? = null,
    val wpm: Int? = null,
    val secondsLeft: Double? = null,
    /** The current (not-yet-completed) read's own day-by-day breakdown -- same buckets a "Save"
     *  reset would archive onto a new StoredCompletedReadTime's dailyHistory (see
     *  [resetCurrentRead]), shown live here so the day-by-day view doesn't require resetting the
     *  timer first. Mirrors the reader's own ReadingTimerSheet "Timer" tab dailyReadingHistory. */
    val currentDailyHistory: List<DailyReadingBucket> = emptyList(),
    /** The original Thorium Reader's 1024-characters-per-page estimate, computed server-side on
     *  demand -- mirrors StatefulBookSheet.tsx's own pageCount. Fetched in its own coroutine (see
     *  [BookDetailViewModel.refresh]) rather than alongside everything else above, since a cache
     *  miss can trigger an expensive first-time server-side computation; null just hides the chip
     *  while it's in flight instead of holding up the rest of the screen. */
    val pageCount: Int? = null
)

/**
 * Reads the saved Locator's `locations.totalProgression` to derive a percent-read figure, the
 * book's highlights/bookmarks/notes, its most recent completed-read run, and a point-in-time
 * "Reading Timer" snapshot (time read so far / current pace / estimated time left) -- matching
 * StatefulBookSheet.tsx's ReadProgressDial + annotations list + "Reading Timer" section +
 * "Completed Read" section.
 */
class BookDetailViewModel(
    private val book: Book,
    private val positionRepository: PositionRepository,
    private val notesRepository: NotesRepository,
    private val annotationsRepository: AnnotationsRepository,
    private val completedReadsRepository: CompletedReadsRepository,
    private val readingTimerRepository: ReadingTimerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
        fetchPageCount()
    }

    /** Deliberately its own coroutine, not bundled into [refresh]'s Promise.all-style batch --
     *  unlike everything fetched there (which just reads back something already cached), a page
     *  count cache miss can trigger an expensive server-side computation that walks every
     *  reading-order resource. Fetched once (not re-triggered by resume-refresh, since it's a
     *  fixed property of the book's own text) so the rest of the screen never waits on it; the
     *  chip just pops in once it resolves. */
    private fun fetchPageCount() {
        viewModelScope.launch {
            val pageCount = readingTimerRepository.getPageCount(book.manifestUrl()).dataOrNull()
            _uiState.value = _uiState.value.copy(pageCount = pageCount)
        }
    }

    /** Re-reads position/annotations/completed-reads/reading-timer figures -- called on first load
     *  and again whenever this screen resumes (see BookDetailScreen), since returning from
     *  ReaderActivity (a separate Activity) doesn't otherwise re-trigger anything: it resumes the
     *  same Compose composition rather than navigating back into it. positionRepository.getPosition
     *  already reconciles against the server first, so this picks up whatever was just read. */
    fun refresh() {
        viewModelScope.launch {
            val locatorDeferred = async { positionRepository.getPosition(book.manifestUrl()) }
            val notesDeferred = async { notesRepository.getNotes(book.manifestUrl()) }
            val highlightsDeferred = async { annotationsRepository.getHighlights(book.manifestUrl()) }
            val bookmarksDeferred = async { annotationsRepository.getBookmarks(book.manifestUrl()) }
            val completedReadsDeferred = async { completedReadsRepository.getCompletedReadTimes(book.manifestUrl()) }
            val readingSecondsDeferred = async { readingTimerRepository.getReadingTimeSeconds(book.manifestUrl()) }
            val wordCountDeferred = async { readingTimerRepository.getWordCount(book.manifestUrl()) }
            val speedSamplesDeferred = async { readingTimerRepository.getReadingSpeedSamples() }
            val dailyHistoryDeferred = async { readingTimerRepository.getDailyReadingHistory(book.manifestUrl()) }

            val locator = locatorDeferred.await()
            val notes = when (val result = notesDeferred.await()) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> emptyList()
            }
            val highlights = highlightsDeferred.await().dataOrNull() ?: emptyList()
            val bookmarks = bookmarksDeferred.await().dataOrNull() ?: emptyList()
            // CLAUDE-ADDED: Same most-recent-first logic as the site's lastCompletedRead --
            // upsertCompletedReadTime appends, so completedAt (not array order) decides "the last run".
            val completedReads = when (val result = completedReadsDeferred.await()) {
                is ApiResult.Success -> result.data.sortedByDescending { it.completedAt }
                is ApiResult.Failure -> emptyList()
            }
            val lastCompletedRead = completedReads.firstOrNull()

            // CLAUDE-ADDED: Same pace/time-left math the live in-reader tracker uses (see
            // ReadingSpeed.kt), applied here as a one-off snapshot against the server's saved
            // sample buffer/word count/position rather than a running ticker, since this screen
            // isn't open while the book is actually being read.
            val wordCount = wordCountDeferred.await().dataOrNull()
            val speedSamples = speedSamplesDeferred.await().dataOrNull() ?: emptyList()
            val wpm = computeCurrentWpm(speedSamples)

            _uiState.value = BookDetailUiState(
                percentRead = percentFromLocator(locator),
                notes = notes,
                highlights = highlights,
                bookmarks = bookmarks,
                lastCompletedRead = lastCompletedRead,
                completedReads = completedReads,
                totalReadingSeconds = readingSecondsDeferred.await().dataOrNull(),
                wpm = wpm,
                secondsLeft = computeSecondsLeft(wordCount, wpm, progressionFromLocator(locator)),
                currentDailyHistory = dailyHistoryDeferred.await().dataOrNull() ?: emptyList(),
                pageCount = _uiState.value.pageCount
            )
        }
    }

    /** Same Discard/Save semantics as the reader's own [com.ishireader.app.reader.ReadingTimerTracker.reset]
     *  -- Discard just zeroes the running total, Save archives it as a completed run first. There's
     *  no live in-memory daily-bucket session here (this screen isn't open while actually reading),
     *  so the archived entry's dailyHistory comes from whatever the server already has persisted for
     *  today's/previous sessions instead. */
    fun resetCurrentRead(save: Boolean) {
        viewModelScope.launch {
            val manifestUrl = book.manifestUrl()
            val currentSeconds = _uiState.value.totalReadingSeconds ?: 0.0

            if (save && currentSeconds > 0) {
                val dailyHistory = readingTimerRepository.getDailyReadingHistory(manifestUrl).dataOrNull()
                val item = StoredCompletedReadTime(
                    id = java.util.UUID.randomUUID().toString(),
                    seconds = currentSeconds,
                    completedAt = System.currentTimeMillis().toDouble(),
                    dailyHistory = dailyHistory?.takeIf { it.isNotEmpty() }
                )
                completedReadsRepository.saveCompletedReadTime(manifestUrl, item)
            }

            readingTimerRepository.setReadingTimeSeconds(manifestUrl, 0.0)
            readingTimerRepository.setDailyReadingHistory(manifestUrl, emptyList())
            refresh()
        }
    }

    fun deleteCompletedRead(id: String) {
        val updated = _uiState.value.completedReads.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(
            completedReads = updated,
            lastCompletedRead = updated.firstOrNull()
        )
        viewModelScope.launch { completedReadsRepository.deleteCompletedReadTime(book.manifestUrl(), id) }
    }

    /** Mirrors StatefulBookSheet.tsx's saveEditingNote -- same upsert-by-id save used by the
     *  reader's own annotations panel, just triggered from this screen instead. Optimistic local
     *  update so the edit shows immediately rather than waiting on the round trip. */
    fun updateNoteText(id: String, text: String) {
        val existing = _uiState.value.notes.find { it.id == id } ?: return
        val updated = existing.copy(text = text, updatedAt = System.currentTimeMillis().toDouble())
        _uiState.value = _uiState.value.copy(notes = _uiState.value.notes.map { if (it.id == id) updated else it })
        viewModelScope.launch { notesRepository.saveNote(book.manifestUrl(), updated) }
    }

    fun deleteNote(id: String) {
        _uiState.value = _uiState.value.copy(notes = _uiState.value.notes.filterNot { it.id == id })
        viewModelScope.launch { notesRepository.deleteNote(book.manifestUrl(), id) }
    }

    fun deleteHighlight(id: String) {
        _uiState.value = _uiState.value.copy(highlights = _uiState.value.highlights.filterNot { it.id == id })
        viewModelScope.launch { annotationsRepository.deleteHighlight(book.manifestUrl(), id) }
    }

    fun deleteBookmark(id: String) {
        _uiState.value = _uiState.value.copy(bookmarks = _uiState.value.bookmarks.filterNot { it.id == id })
        viewModelScope.launch { annotationsRepository.deleteBookmark(book.manifestUrl(), id) }
    }

    class Factory(
        private val book: Book,
        private val positionRepository: PositionRepository,
        private val notesRepository: NotesRepository,
        private val annotationsRepository: AnnotationsRepository,
        private val completedReadsRepository: CompletedReadsRepository,
        private val readingTimerRepository: ReadingTimerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(
                book,
                positionRepository,
                notesRepository,
                annotationsRepository,
                completedReadsRepository,
                readingTimerRepository
            ) as T
    }
}
