package com.ishireader.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.CompletedReadsRepository
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.PositionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookDetailUiState(
    /** 0..100, one decimal place to match the website's rounding; null = no progress yet (dial hidden). */
    val percentRead: Double? = null,
    val notes: List<StoredNote> = emptyList(),
    /** Most recent entry from the reader's own Completed tab -- mirrors StatefulBookSheet.tsx's
     *  lastCompletedRead (max by completedAt, not array order). */
    val lastCompletedRead: StoredCompletedReadTime? = null
)

/**
 * Reads the saved Locator's `locations.totalProgression` to derive a percent-read figure, plus the
 * book's notes and most recent completed-read run, matching StatefulBookSheet.tsx. Highlights/
 * bookmarks and the live reading-timer aren't ported yet -- they depend on API clients this app
 * doesn't have.
 */
class BookDetailViewModel(
    private val book: Book,
    private val positionRepository: PositionRepository,
    private val notesRepository: NotesRepository,
    private val completedReadsRepository: CompletedReadsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads position/notes/completed-reads -- called on first load and again whenever this
     *  screen resumes (see BookDetailScreen), since returning from ReaderActivity (a separate
     *  Activity) doesn't otherwise re-trigger anything: it resumes the same Compose composition
     *  rather than navigating back into it. positionRepository.getPosition already reconciles
     *  against the server first, so this picks up whatever was just read. */
    fun refresh() {
        viewModelScope.launch {
            val locatorDeferred = async { positionRepository.getPosition(book.manifestUrl()) }
            val notesDeferred = async { notesRepository.getNotes(book.manifestUrl()) }
            val completedReadsDeferred = async { completedReadsRepository.getCompletedReadTimes(book.manifestUrl()) }

            val locator = locatorDeferred.await()
            val notes = when (val result = notesDeferred.await()) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> emptyList()
            }
            // CLAUDE-ADDED: Same most-recent-first logic as the site's lastCompletedRead --
            // upsertCompletedReadTime appends, so completedAt (not array order) decides "the last run".
            val lastCompletedRead = when (val result = completedReadsDeferred.await()) {
                is ApiResult.Success -> result.data.maxByOrNull { it.completedAt }
                is ApiResult.Failure -> null
            }

            _uiState.value = BookDetailUiState(
                percentRead = percentFromLocator(locator),
                notes = notes,
                lastCompletedRead = lastCompletedRead
            )
        }
    }

    class Factory(
        private val book: Book,
        private val positionRepository: PositionRepository,
        private val notesRepository: NotesRepository,
        private val completedReadsRepository: CompletedReadsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(book, positionRepository, notesRepository, completedReadsRepository) as T
    }
}
