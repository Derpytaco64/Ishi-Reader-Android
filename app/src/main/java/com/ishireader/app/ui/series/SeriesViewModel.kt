package com.ishireader.app.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.isComic
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The same name can exist as both an ebook series and an audiobook series -- keying on
 *  name+format keeps those two from merging into one slot, matching seriesKey() in
 *  StatefulSeriesView.tsx. */
fun seriesKey(name: String, isAudiobook: Boolean): String = "$name::${if (isAudiobook) "audiobook" else "ebook"}"

data class SeriesSlot(
    val key: String,
    val name: String,
    val isAudiobook: Boolean,
    val books: List<Book>,
    val center: Book,
    val left: Book?,
    val right: Book?
)

enum class SeriesSortDirection(val label: String) {
    FIRST_TO_LAST("Series Order (First → Last)"),
    LAST_TO_FIRST("Series Order (Last → First)")
}

/** Which formats' series show up in the overview grid -- mirrors Library's BOOKS/AUDIOBOOKS/MANGA
 *  split, plus an ALL option since a series view mixing formats side by side is meaningful in a
 *  way a mixed Library grid isn't. Defaults to ALL. */
enum class SeriesFormatFilter(val label: String) {
    ALL("All"),
    BOOKS("Books"),
    AUDIOBOOKS("Audiobooks"),
    MANGA("Manga")
}

data class SeriesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val slots: List<SeriesSlot> = emptyList(),
    val selectedSeriesKey: String? = null,
    val sortDirection: SeriesSortDirection = SeriesSortDirection.FIRST_TO_LAST,
    val formatFilter: SeriesFormatFilter = SeriesFormatFilter.ALL
) {
    val selectedSlot: SeriesSlot? get() = slots.find { it.key == selectedSeriesKey }

    val selectedBooks: List<Book> get() {
        val slot = selectedSlot ?: return emptyList()
        val sorted = slot.books.sortedBy { it.series?.position ?: 0.0 }
        return if (sortDirection == SeriesSortDirection.LAST_TO_FIRST) sorted.reversed() else sorted
    }
}

/** Reimplements StatefulSeriesView.tsx: an overview grid of one slot per (series name, format)
 *  pair, drilling down into that series' books sorted by position. */
class SeriesViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    private var allBooks: List<Book> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = libraryRepository.fetchBooks()) {
                is ApiResult.Success -> {
                    allBooks = result.data
                    _uiState.value = _uiState.value.copy(isLoading = false, slots = visibleSlots())
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun selectSeries(key: String) {
        _uiState.value = _uiState.value.copy(selectedSeriesKey = key)
    }

    /** Also used to fall back out of a selection whose series has disappeared. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedSeriesKey = null)
    }

    fun onSortDirectionChange(direction: SeriesSortDirection) {
        _uiState.value = _uiState.value.copy(sortDirection = direction)
    }

    fun onFormatFilterChange(filter: SeriesFormatFilter) {
        val slots = visibleSlots(filter = filter)
        // Falls back out of a selection whose series disappeared under the new filter, same as a
        // series vanishing on refresh -- there's nothing valid left to drill into.
        val selectedSeriesKey = _uiState.value.selectedSeriesKey
            ?.takeIf { key -> slots.any { it.key == key } }
        _uiState.value = _uiState.value.copy(formatFilter = filter, slots = slots, selectedSeriesKey = selectedSeriesKey)
    }

    private fun visibleSlots(
        books: List<Book> = allBooks,
        filter: SeriesFormatFilter = _uiState.value.formatFilter
    ): List<SeriesSlot> = buildSlots(filterByFormat(books, filter))

    private fun filterByFormat(books: List<Book>, filter: SeriesFormatFilter): List<Book> = when (filter) {
        SeriesFormatFilter.ALL -> books
        SeriesFormatFilter.BOOKS -> books.filter { !it.isAudiobook && !it.isComic }
        SeriesFormatFilter.AUDIOBOOKS -> books.filter { it.isAudiobook }
        SeriesFormatFilter.MANGA -> books.filter { it.isComic }
    }

    private fun buildSlots(books: List<Book>): List<SeriesSlot> {
        val groups = books.filter { it.series?.name != null }
            .groupBy { seriesKey(it.series!!.name, it.isAudiobook) }

        return groups.entries.map { (key, seriesBooks) ->
            val sortedByPosition = seriesBooks.sortedWith(
                compareBy({ it.series?.position ?: Double.MAX_VALUE }, { it.title })
            )
            val center = sortedByPosition.first()
            val rest = sortedByPosition.drop(1)
            SeriesSlot(
                key = key,
                name = center.series!!.name,
                isAudiobook = center.isAudiobook,
                books = seriesBooks,
                center = center,
                left = rest.getOrNull(0),
                right = rest.getOrNull(1)
            )
        }.sortedBy { it.name }
    }

    class Factory(private val libraryRepository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SeriesViewModel(libraryRepository) as T
    }
}
