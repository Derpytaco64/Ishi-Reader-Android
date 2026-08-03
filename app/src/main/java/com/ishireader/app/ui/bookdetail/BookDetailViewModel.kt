package com.ishireader.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.PositionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

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

    private fun percentFromLocator(locator: JsonElement?): Double? {
        val totalProgression = locator?.jsonObject
            ?.get("locations")?.jsonObject
            ?.get("totalProgression")?.jsonPrimitive?.doubleOrNull
            ?: return null
        val percent = round(min(1.0, max(0.0, totalProgression)) * 1000) / 10
        return percent.takeIf { it > 0 }
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
