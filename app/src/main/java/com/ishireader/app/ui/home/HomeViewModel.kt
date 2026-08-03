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
        val lastReadAt = book.lastReadAt ?: return
        val updated = _uiState.value.continueReading.filterNot { it.book.url == book.url }
        _uiState.value = _uiState.value.copy(continueReading = updated)

        viewModelScope.launch {
            val dismissed = libraryPrefsRepository.getContinueReadingDismissed().toMutableMap()
            dismissed[book.url] = lastReadAt
            libraryPrefsRepository.setContinueReadingDismissed(dismissed)
        }
    }

    private suspend fun buildShelves(allBooks: List<Book>) {
        val dismissed = libraryPrefsRepository.getContinueReadingDismissed()

        _uiState.value = HomeUiState(
            isLoading = false,
            continueReading = computeContinueReading(allBooks, dismissed),
            lastSeriesRead = computeLastSeriesRead(allBooks),
            recentlyAdded = allBooks.sortedByDescending { it.addedAt ?: 0.0 }.take(20),
            myLibrary = allBooks.sortedWith(compareBy(collator) { it.title })
        )
    }

    private suspend fun computeContinueReading(
        allBooks: List<Book>,
        dismissed: Map<String, Double>
    ): List<ContinueReadingItem> = coroutineScope {
        val candidates = allBooks.filter { it.lastReadAt != null }

        candidates.map { book ->
            async {
                val locator = when (val result = positionRepository.getPosition(book.manifestUrl())) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> null
                }
                ContinueReadingItem(book, percentFromLocator(locator))
            }
        }.awaitAll()
            .filter { item ->
                val lastReadAt = item.book.lastReadAt!!
                val notFinished = item.percent == null || item.percent < 100.0
                val dismissedAt = dismissed[item.book.url]
                val notDismissed = dismissedAt == null || dismissedAt < lastReadAt
                notFinished && notDismissed
            }
            .sortedByDescending { it.book.lastReadAt }
            .take(5)
    }

    private fun computeLastSeriesRead(allBooks: List<Book>): List<Book> {
        val groups = allBooks.filter { it.series != null }
            .groupBy { "${it.series!!.name}|${it.isAudiobook}" }

        val bestGroupKey = groups.entries
            .mapNotNull { (key, books) -> books.mapNotNull { it.lastReadAt }.maxOrNull()?.let { key to it } }
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
