package com.ishireader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = libraryRepository.fetchBooks()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(books = result.data, isLoading = false)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(error = result.message, isLoading = false)
            }
        }
    }

    class Factory(private val libraryRepository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(libraryRepository) as T
    }
}
