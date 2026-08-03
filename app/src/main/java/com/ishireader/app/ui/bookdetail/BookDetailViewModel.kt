package com.ishireader.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.PositionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookDetailUiState(
    /** 0..100, one decimal place to match the website's rounding; null = no progress yet (dial hidden). */
    val percentRead: Double? = null
)

/**
 * Reads the saved Locator's `locations.totalProgression` to derive a percent-read figure,
 * matching StatefulBookSheet.tsx's ReadProgressDial calculation exactly. Reading-timer,
 * annotations, and completed-read sections aren't ported yet -- they depend on API clients
 * (readingTime/wordCount/highlights/notes) this app doesn't have.
 */
class BookDetailViewModel(
    private val book: Book,
    private val positionRepository: PositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val locator = when (val result = positionRepository.getPosition(book.manifestUrl())) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> null
            }
            _uiState.value = BookDetailUiState(percentRead = percentFromLocator(locator))
        }
    }

    class Factory(
        private val book: Book,
        private val positionRepository: PositionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(book, positionRepository) as T
    }
}
