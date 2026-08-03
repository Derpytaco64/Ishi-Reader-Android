package com.ishireader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.SortMode
import com.ishireader.app.data.model.sortedBy
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab { BOOKS, AUDIOBOOKS }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val tab: LibraryTab = LibraryTab.BOOKS,
    val sortMode: SortMode = SortMode.TITLE_ASC,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Ishi-Read's web library has no "Books"/"Audiobooks" toggle inside this view -- they're
 * separate nav destinations that both mount the same flat-grid component. This ViewModel
 * folds them into one screen with a tab switch until the nav-drawer phase lands; the
 * underlying filter/sort behavior matches StatefulMyLibraryView exactly (default sort is
 * titleAsc here, deliberately different from shelf views' addedNewest default).
 */
class LibraryViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

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
                    _uiState.value = _uiState.value.copy(isLoading = false, books = visibleBooks())
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(error = result.message, isLoading = false)
            }
        }
    }

    fun onTabSelected(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(tab = tab, books = visibleBooks(tab = tab))
    }

    fun onSortModeChange(mode: SortMode) {
        _uiState.value = _uiState.value.copy(sortMode = mode, books = visibleBooks(sortMode = mode))
    }

    private fun visibleBooks(
        tab: LibraryTab = _uiState.value.tab,
        sortMode: SortMode = _uiState.value.sortMode
    ): List<Book> {
        val filtered = allBooks.filter { it.isAudiobook == (tab == LibraryTab.AUDIOBOOKS) }
        return filtered.sortedBy(sortMode)
    }

    class Factory(private val libraryRepository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(libraryRepository) as T
    }
}
