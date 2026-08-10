package com.ishireader.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.AnnotationsRepository
import com.ishireader.app.data.repository.BookMigrationRepository
import com.ishireader.app.data.repository.CompletedReadsRepository
import com.ishireader.app.data.repository.LibraryRepository
import com.ishireader.app.data.repository.ListeningTimeRepository
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.PositionRepository
import com.ishireader.app.data.repository.ReadingTimerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MigrateStep { SOURCE, DEST, CONFIRM, DONE }

/** Only the counts the migration dialog's stat chips need -- deliberately not the full
 *  BookDetailUiState shape (no wpm/secondsLeft, this isn't a live reading-progress display),
 *  fetched fresh on every book selection rather than cached across the dialog's lifetime. */
data class BookSummary(
    val percent: Double?,
    val isAudiobook: Boolean,
    val notes: Int = 0,
    val highlights: Int = 0,
    val bookmarks: Int = 0,
    val completedReads: Int = 0,
    val readingSeconds: Double? = null,
    val listeningSeconds: Double? = null,
    val completedListens: Int = 0
)

data class MigrateBookDataUiState(
    val isLoadingBooks: Boolean = true,
    val books: List<Book> = emptyList(),
    val step: MigrateStep = MigrateStep.SOURCE,
    val sourceBook: Book? = null,
    val sourceSummary: BookSummary? = null,
    val isLoadingSourceSummary: Boolean = false,
    val destBook: Book? = null,
    val destSummary: BookSummary? = null,
    val isLoadingDestSummary: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null
)

/**
 * Backs the Migrate Book Data dialog (user menu -> "Migrate Book Data"): source book picker, then
 * destination book picker (exact manifestUrl match with the source is blocked), then an explicit
 * overwrite confirmation. Exists for the scenario where a metadata edit rewrites a book's file --
 * changing its content hash (see the server's resolveBookIdentity) -- and it lands in the library
 * as a second entry with none of the original's progress/annotations, since those are still filed
 * under the old hash.
 */
class MigrateBookDataViewModel(
    private val libraryRepository: LibraryRepository,
    private val positionRepository: PositionRepository,
    private val notesRepository: NotesRepository,
    private val annotationsRepository: AnnotationsRepository,
    private val completedReadsRepository: CompletedReadsRepository,
    private val readingTimerRepository: ReadingTimerRepository,
    private val listeningTimeRepository: ListeningTimeRepository,
    private val bookMigrationRepository: BookMigrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrateBookDataUiState())
    val uiState: StateFlow<MigrateBookDataUiState> = _uiState.asStateFlow()

    /** Called each time the dialog opens -- resets any leftover state from a previous run and
     *  refetches the library (a book added/removed since the dialog was last open should show up). */
    fun start() {
        _uiState.value = MigrateBookDataUiState()
        viewModelScope.launch {
            when (val result = libraryRepository.fetchBooks()) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoadingBooks = false, books = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingBooks = false, books = emptyList()) }
            }
        }
    }

    fun pickSource(book: Book) {
        _uiState.update { it.copy(sourceBook = book, sourceSummary = null, isLoadingSourceSummary = true) }
        viewModelScope.launch {
            val summary = loadSummary(book)
            _uiState.update { it.copy(sourceSummary = summary, isLoadingSourceSummary = false) }
        }
    }

    fun changeSource() {
        _uiState.update { it.copy(sourceBook = null, sourceSummary = null) }
    }

    fun goToDestStep() {
        _uiState.update { it.copy(step = MigrateStep.DEST) }
    }

    fun pickDest(book: Book) {
        _uiState.update { it.copy(destBook = book, destSummary = null, isLoadingDestSummary = true) }
        viewModelScope.launch {
            val summary = loadSummary(book)
            _uiState.update { it.copy(destSummary = summary, isLoadingDestSummary = false) }
        }
    }

    fun changeDest() {
        _uiState.update { it.copy(destBook = null, destSummary = null) }
    }

    fun goToConfirmStep() {
        _uiState.update { it.copy(step = MigrateStep.CONFIRM) }
    }

    fun backToDestStep() {
        _uiState.update { it.copy(step = MigrateStep.DEST, submitError = null) }
    }

    fun confirmMigration() {
        val state = _uiState.value
        val source = state.sourceBook ?: return
        val dest = state.destBook ?: return

        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = bookMigrationRepository.migrateBookData(source.manifestUrl(), dest.manifestUrl())) {
                is ApiResult.Success -> _uiState.update { it.copy(isSubmitting = false, step = MigrateStep.DONE) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, submitError = result.message) }
            }
        }
    }

    private suspend fun loadSummary(book: Book): BookSummary = coroutineScope {
        val manifestUrl = book.manifestUrl()
        val percentDeferred = async { percentFromLocator(positionRepository.getPosition(manifestUrl)) }

        if (book.isAudiobook) {
            val listeningDeferred = async { listeningTimeRepository.getListeningTime(manifestUrl).dataOrNull() }
            val completedListensDeferred = async { listeningTimeRepository.getCompletedListens(manifestUrl).dataOrNull().orEmpty() }

            BookSummary(
                percent = percentDeferred.await(),
                isAudiobook = true,
                listeningSeconds = listeningDeferred.await()?.accumulatedSeconds,
                completedListens = completedListensDeferred.await().size
            )
        } else {
            val notesDeferred = async { notesRepository.getNotes(manifestUrl).dataOrNull().orEmpty() }
            val highlightsDeferred = async { annotationsRepository.getHighlights(manifestUrl).dataOrNull().orEmpty() }
            val bookmarksDeferred = async { annotationsRepository.getBookmarks(manifestUrl).dataOrNull().orEmpty() }
            val completedReadsDeferred = async { completedReadsRepository.getCompletedReadTimes(manifestUrl).dataOrNull().orEmpty() }
            val readingSecondsDeferred = async { readingTimerRepository.getReadingTimeSeconds(manifestUrl).dataOrNull() }

            BookSummary(
                percent = percentDeferred.await(),
                isAudiobook = false,
                notes = notesDeferred.await().size,
                highlights = highlightsDeferred.await().size,
                bookmarks = bookmarksDeferred.await().size,
                completedReads = completedReadsDeferred.await().size,
                readingSeconds = readingSecondsDeferred.await()
            )
        }
    }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val positionRepository: PositionRepository,
        private val notesRepository: NotesRepository,
        private val annotationsRepository: AnnotationsRepository,
        private val completedReadsRepository: CompletedReadsRepository,
        private val readingTimerRepository: ReadingTimerRepository,
        private val listeningTimeRepository: ListeningTimeRepository,
        private val bookMigrationRepository: BookMigrationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MigrateBookDataViewModel(
                libraryRepository,
                positionRepository,
                notesRepository,
                annotationsRepository,
                completedReadsRepository,
                readingTimerRepository,
                listeningTimeRepository,
                bookMigrationRepository
            ) as T
    }
}
