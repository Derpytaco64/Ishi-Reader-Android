package com.ishireader.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.AniListFuzzyDate
import com.ishireader.app.data.model.AniListLink
import com.ishireader.app.data.model.AniListMedia
import com.ishireader.app.data.model.AniListMediaListEntry
import com.ishireader.app.data.model.AniListSearchResult
import com.ishireader.app.data.model.AniListTitle
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.aniListSeriesKey
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.AniListRepository
import com.ishireader.app.data.repository.LibraryPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val RECONNECT_MESSAGE = "Your AniList connection expired -- reconnect from the AniList item in the account menu."

data class TrackingUiState(
    val seriesKey: String? = null,
    val link: AniListLink? = null,
    val media: AniListMedia? = null,
    val scoreFormat: String? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AniListSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

/**
 * Backs the per-book "Tracking" sheet on [com.ishireader.app.ui.bookdetail.BookDetailScreen] --
 * manga-only, mirrors Tachiyomi's own tracking sheet (status/score/progress/dates/rereads).
 * Deliberately a standalone ViewModel rather than folded into BookDetailViewModel's already-large
 * state, same reasoning as EditUserViewModel/AniListAccountViewModel being separate from
 * TopBarViewModel -- this is its own concern with its own lifecycle (only loaded while the sheet is
 * open), not something every BookDetailScreen visit needs to fetch.
 *
 * The link itself (which AniList media a series maps to) lives in library-prefs' anilistLinks map
 * (see [Book.aniListSeriesKey]/[LibraryPrefsRepository.getAniListLinks]); the actual tracking
 * fields go through [AniListRepository.patchEntry]'s offline-capable outbox.
 */
class TrackingViewModel(
    private val aniListRepository: AniListRepository,
    private val libraryPrefsRepository: LibraryPrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun start(book: Book) {
        val seriesKey = book.aniListSeriesKey()
        _uiState.value = TrackingUiState(seriesKey = seriesKey, isLoading = true)
        viewModelScope.launch {
            val scoreFormat = aniListRepository.getScoreFormat()
            val link = libraryPrefsRepository.getAniListLinks()[seriesKey]
            if (link == null) {
                _uiState.update { it.copy(isLoading = false, scoreFormat = scoreFormat) }
                return@launch
            }
            _uiState.update { it.copy(scoreFormat = scoreFormat) }
            loadMedia(link)
        }
    }

    private suspend fun loadMedia(link: AniListLink) {
        _uiState.update { it.copy(link = link, isLoading = true) }
        when (val result = aniListRepository.getEntry(link.mediaId)) {
            is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, media = result.data, error = null) }
            is ApiResult.Failure -> {
                // Offline (or the server/AniList is unreachable) -- fall back to whatever this
                // device last knew, same "cache is the offline source of truth" pattern used
                // everywhere else in this app. An auth error means the token itself is stale, not
                // just unreachable, so it always surfaces (even over a cached entry) since silently
                // showing old data would hide that new edits aren't actually syncing anymore.
                val cached = aniListRepository.getCachedEntry(link.mediaId)
                val message = when {
                    result.isAuthError -> RECONNECT_MESSAGE
                    cached == null -> result.message
                    else -> null
                }
                _uiState.update { it.copy(isLoading = false, media = cached, error = message) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            when (val result = aniListRepository.search(query)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSearching = false, searchResults = result.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSearching = false, error = if (result.isAuthError) RECONNECT_MESSAGE else result.message)
                }
            }
        }
    }

    fun link(result: AniListSearchResult) {
        val seriesKey = _uiState.value.seriesKey ?: return
        viewModelScope.launch {
            val link = AniListLink(mediaId = result.id, syncEnabled = true)
            libraryPrefsRepository.setAniListLink(seriesKey, link)
            _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "") }
            loadMedia(link)
        }
    }

    /** Unlinks the series entirely -- does not touch anything already saved on the AniList side,
     *  only forgets the mapping locally/server-side in library-prefs. */
    fun unlink() {
        val seriesKey = _uiState.value.seriesKey ?: return
        viewModelScope.launch {
            libraryPrefsRepository.setAniListLink(seriesKey, null)
            _uiState.value = TrackingUiState(seriesKey = seriesKey)
        }
    }

    fun toggleSync(enabled: Boolean) {
        val seriesKey = _uiState.value.seriesKey ?: return
        val link = _uiState.value.link ?: return
        val updated = link.copy(syncEnabled = enabled)
        _uiState.update { it.copy(link = updated) }
        viewModelScope.launch { libraryPrefsRepository.setAniListLink(seriesKey, updated) }
    }

    fun setStatus(status: String) {
        updateLocalEntry { it.copy(status = status) }
        pushPatch(JsonObject(mapOf("status" to JsonPrimitive(status))))
    }

    fun setScore(score: Double) {
        updateLocalEntry { it.copy(score = score) }
        pushPatch(JsonObject(mapOf("score" to JsonPrimitive(score))))
    }

    fun setProgress(progress: Int) {
        updateLocalEntry { it.copy(progress = progress) }
        pushPatch(JsonObject(mapOf("progress" to JsonPrimitive(progress))))
    }

    fun setRepeat(repeat: Int) {
        updateLocalEntry { it.copy(repeat = repeat) }
        pushPatch(JsonObject(mapOf("repeat" to JsonPrimitive(repeat))))
    }

    fun setStartedAt(date: AniListFuzzyDate?) {
        updateLocalEntry { it.copy(startedAt = date) }
        pushPatch(JsonObject(mapOf("startedAt" to dateToJson(date))))
    }

    fun setCompletedAt(date: AniListFuzzyDate?) {
        updateLocalEntry { it.copy(completedAt = date) }
        pushPatch(JsonObject(mapOf("completedAt" to dateToJson(date))))
    }

    private fun dateToJson(date: AniListFuzzyDate?): JsonElement =
        if (date == null) JsonNull else Json.encodeToJsonElement(AniListFuzzyDate.serializer(), date)

    private fun updateLocalEntry(transform: (AniListMediaListEntry) -> AniListMediaListEntry) {
        _uiState.update { state ->
            val currentEntry = state.media?.mediaListEntry ?: AniListMediaListEntry(id = 0, status = "PLANNING")
            val baseMedia = state.media ?: AniListMedia(id = state.link?.mediaId ?: 0, chapters = null, title = AniListTitle())
            state.copy(media = baseMedia.copy(mediaListEntry = transform(currentEntry)))
        }
    }

    private fun pushPatch(fields: JsonObject) {
        val mediaId = _uiState.value.link?.mediaId ?: return
        viewModelScope.launch { aniListRepository.patchEntry(mediaId, fields) }
    }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val libraryPrefsRepository: LibraryPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrackingViewModel(aniListRepository, libraryPrefsRepository) as T
    }
}
