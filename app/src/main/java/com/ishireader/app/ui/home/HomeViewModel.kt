package com.ishireader.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.LibraryPrefsRepository
import com.ishireader.app.data.repository.LibraryRepository
import com.ishireader.app.data.repository.PositionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class ContinueReadingItem(val book: Book, val percent: Double?)

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val continueReading: List<ContinueReadingItem> = emptyList(),
    val lastSeriesRead: List<Book> = emptyList(),
    val recentlyAdded: List<Book> = emptyList(),
    val myLibrary: List<Book> = emptyList()
)

/**
 * Reimplements the 4 derived Home shelves from src/app/page.tsx, which computes all of this
 * client-side over the same /api/books response rather than a dedicated aggregation endpoint:
 *
 * - Continue Reading: books with a lastReadAt, not finished (percent < 100), not dismissed
 *   (or re-read since being dismissed), newest lastReadAt first, capped to 5.
 * - Last Series Read: every book in whichever series' most-recently-read book is the most
 *   recent across all series (not just that one book), ordered by series position.
 * - Recently Added: newest addedAt first, capped to 20.
 * - My Library: the whole library, alphabetical by title.
 *
 * Shelf visibility/order and carousel-vs-grid styling are decided by the screen, not here.
 */
class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val positionRepository: PositionRepository,
    private val libraryPrefsRepository: LibraryPrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val collator: Collator = Collator.getInstance()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = libraryRepository.fetchBooks()) {
                is ApiResult.Success -> buildShelves(result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    /** Removes a book from Continue Reading until it's read again past this point. */
    fun dismissFromContinueReading(book: Book) {
        val updated = _uiState.value.continueReading.filterNot { it.book.url == book.url }
        _uiState.value = _uiState.value.copy(continueReading = updated)

        viewModelScope.launch {
            // book.lastReadAt alone isn't enough: a book that only got read locally-offline so far
            // has no server lastReadAt yet, but it can still be showing in Continue Reading (see
            // effectiveLastReadAt) -- without checking the local timestamp too, dismissing it here
            // would silently do nothing beyond the optimistic UI removal above.
            val localTimestamps = positionRepository.localLastReadTimestamps()
            val lastReadAt = effectiveLastReadAt(book, localTimestamps) ?: return@launch

            val dismissed = libraryPrefsRepository.getContinueReadingDismissed().toMutableMap()
            dismissed[book.url] = lastReadAt
            libraryPrefsRepository.setContinueReadingDismissed(dismissed)
        }
    }

    private suspend fun buildShelves(allBooks: List<Book>) {
        val dismissed = libraryPrefsRepository.getContinueReadingDismissed()
        // Book.lastReadAt is server-truth as of the last successful /api/books fetch; a book read
        // on this device since then (including while offline) has advanced the local Position
        // table without touching that snapshot, so Continue Reading/Last Series Read would
        // otherwise look frozen until the next sync -- see effectiveLastReadAt below.
        val localTimestamps = positionRepository.localLastReadTimestamps()

        _uiState.value = HomeUiState(
            isLoading = false,
            continueReading = computeContinueReading(allBooks, dismissed, localTimestamps),
            lastSeriesRead = computeLastSeriesRead(allBooks, localTimestamps),
            recentlyAdded = allBooks.sortedByDescending { it.addedAt ?: 0.0 }.take(20),
            myLibrary = allBooks.sortedWith(compareBy(collator) { it.title })
        )
    }

    /** The later of the server's lastReadAt and this device's own local position timestamp for
     *  [book], or null if neither has ever been set (never read anywhere). */
    private fun effectiveLastReadAt(book: Book, localTimestamps: Map<String, Long>): Double? {
        val local = localTimestamps[book.manifestUrl()]?.toDouble() ?: 0.0
        return maxOf(book.lastReadAt ?: 0.0, local).takeIf { it > 0.0 }
    }

    private suspend fun computeContinueReading(
        allBooks: List<Book>,
        dismissed: Map<String, Double>,
        localTimestamps: Map<String, Long>
    ): List<ContinueReadingItem> = coroutineScope {
        val candidates = allBooks.mapNotNull { book ->
            effectiveLastReadAt(book, localTimestamps)?.let { book to it }
        }

        candidates.map { (book, lastReadAt) ->
            async {
                val locator = positionRepository.getPosition(book.manifestUrl())
                Triple(book, lastReadAt, percentFromLocator(locator))
            }
        }.awaitAll()
            .filter { (book, lastReadAt, percent) ->
                val notFinished = percent == null || percent < 100.0
                val dismissedAt = dismissed[book.url]
                val notDismissed = dismissedAt == null || dismissedAt < lastReadAt
                notFinished && notDismissed
            }
            .sortedByDescending { it.second }
            .take(5)
            .map { (book, _, percent) -> ContinueReadingItem(book, percent) }
    }

    private fun computeLastSeriesRead(allBooks: List<Book>, localTimestamps: Map<String, Long>): List<Book> {
        val groups = allBooks.filter { it.series != null }
            .groupBy { "${it.series!!.name}|${it.isAudiobook}" }

        val bestGroupKey = groups.entries
            .mapNotNull { (key, books) ->
                books.mapNotNull { effectiveLastReadAt(it, localTimestamps) }.maxOrNull()?.let { key to it }
            }
            .maxByOrNull { it.second }
            ?.first
            ?: return emptyList()

        return groups.getValue(bestGroupKey).sortedBy { it.series?.position ?: 0.0 }
    }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val positionRepository: PositionRepository,
        private val libraryPrefsRepository: LibraryPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(libraryRepository, positionRepository, libraryPrefsRepository) as T
    }
}
