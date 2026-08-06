package com.ishireader.app.reader

import com.ishireader.app.data.model.StoredBookmark
import com.ishireader.app.data.model.StoredHighlight
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.AnnotationsRepository
import com.ishireader.app.data.repository.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.shared.publication.Locator
import java.util.UUID

/** The website's fixed 5-swatch highlight palette (HIGHLIGHT_COLORS in highlightColors.ts) --
 *  colors are chosen from this set, not a free picker. */
enum class HighlightColor(val id: String, val hex: String) {
    YELLOW("yellow", "#FFE066"),
    GREEN("green", "#A0E6A0"),
    BLUE("blue", "#9CC9FF"),
    PINK("pink", "#FFB3D1"),
    PURPLE("purple", "#D3B3FF");

    companion object {
        val DEFAULT = YELLOW
        fun fromId(id: String?): HighlightColor = entries.find { it.id == id } ?: DEFAULT
    }
}

const val ANNOTATIONS_GROUP_HIGHLIGHTS = "highlights"
const val ANNOTATIONS_GROUP_NOTES = "notes"

/** Fixed neutral tint for note decorations -- NOTE_DECORATION_HEX in the website, distinct from
 *  and not part of the highlight palette (notes aren't user-colorable). */
private const val NOTE_TINT_HEX = "#B0B0B0"

data class AnnotationsUiState(
    val loading: Boolean = true,
    val highlights: List<StoredHighlight> = emptyList(),
    val bookmarks: List<StoredBookmark> = emptyList(),
    val notes: List<StoredNote> = emptyList()
)

/**
 * Owns highlight/bookmark/note CRUD and keeps the navigator's "highlights"/"notes" Decoration
 * groups in sync -- ports the website's annotation model onto Readium Kotlin's DecorableNavigator
 * API. Bookmarks have no visual decoration (matches the website: a bookmark marks a position, it
 * doesn't render inline in the text). Decoration ids are the annotation's own id, scoped per group
 * -- DecorableNavigator.OnActivatedEvent carries the group name too, so callers don't need a
 * prefix to tell a tapped highlight apart from a tapped note.
 */
class AnnotationsController(
    private val scope: CoroutineScope,
    private val annotationsRepository: AnnotationsRepository,
    private val notesRepository: NotesRepository
) {
    private val _state = MutableStateFlow(AnnotationsUiState())
    val state: StateFlow<AnnotationsUiState> = _state.asStateFlow()

    private lateinit var manifestUrl: String
    private var navigator: DecorableNavigator? = null

    suspend fun start(manifestUrl: String, navigator: DecorableNavigator) {
        this.manifestUrl = manifestUrl
        this.navigator = navigator
        _state.value = _state.value.copy(loading = true)
        refresh()
    }

    private suspend fun refresh() {
        val highlights = annotationsRepository.getHighlights(manifestUrl).dataOrNull() ?: emptyList()
        val bookmarks = annotationsRepository.getBookmarks(manifestUrl).dataOrNull() ?: emptyList()
        val notes = notesRepository.getNotes(manifestUrl).dataOrNull() ?: emptyList()
        _state.value = AnnotationsUiState(loading = false, highlights = highlights, bookmarks = bookmarks, notes = notes)
        applyDecorations()
    }

    private suspend fun applyDecorations() {
        val nav = navigator ?: return

        val highlightDecorations = _state.value.highlights.mapNotNull { h ->
            val locator = h.locator?.let(::parseLocator) ?: return@mapNotNull null
            Decoration(
                id = h.id,
                locator = locator,
                style = Decoration.Style.Highlight(
                    tint = android.graphics.Color.parseColor(HighlightColor.fromId(h.color).hex),
                    isActive = true
                )
            )
        }
        nav.applyDecorations(highlightDecorations, ANNOTATIONS_GROUP_HIGHLIGHTS)

        val noteDecorations = _state.value.notes.mapNotNull { n ->
            val locator = n.locator?.let(::parseLocator) ?: return@mapNotNull null
            Decoration(
                id = n.id,
                locator = locator,
                style = Decoration.Style.Highlight(
                    tint = android.graphics.Color.parseColor(NOTE_TINT_HEX),
                    isActive = true
                )
            )
        }
        nav.applyDecorations(noteDecorations, ANNOTATIONS_GROUP_NOTES)
    }

    fun highlightById(id: String): StoredHighlight? = _state.value.highlights.find { it.id == id }
    fun noteById(id: String): StoredNote? = _state.value.notes.find { it.id == id }

    fun addHighlight(locator: Locator, color: HighlightColor = HighlightColor.DEFAULT) {
        scope.launch {
            val item = StoredHighlight(
                id = UUID.randomUUID().toString(),
                locator = locatorToJson(locator),
                color = color.id,
                createdAt = System.currentTimeMillis().toDouble(),
                chapterTitle = locator.title
            )
            annotationsRepository.saveHighlight(manifestUrl, item)
            refresh()
        }
    }

    fun updateHighlightColor(id: String, color: HighlightColor) {
        val existing = highlightById(id) ?: return
        scope.launch {
            annotationsRepository.saveHighlight(manifestUrl, existing.copy(color = color.id))
            refresh()
        }
    }

    fun deleteHighlight(id: String) {
        scope.launch {
            annotationsRepository.deleteHighlight(manifestUrl, id)
            refresh()
        }
    }

    fun addBookmark(locator: Locator) {
        scope.launch {
            val item = StoredBookmark(
                id = UUID.randomUUID().toString(),
                locator = locatorToJson(locator),
                createdAt = System.currentTimeMillis().toDouble(),
                chapterTitle = locator.title
            )
            annotationsRepository.saveBookmark(manifestUrl, item)
            refresh()
        }
    }

    fun deleteBookmark(id: String) {
        scope.launch {
            annotationsRepository.deleteBookmark(manifestUrl, id)
            refresh()
        }
    }

    fun addNote(locator: Locator, text: String) {
        scope.launch {
            val item = StoredNote(
                id = UUID.randomUUID().toString(),
                locator = locatorToJson(locator),
                text = text,
                createdAt = System.currentTimeMillis().toDouble(),
                chapterTitle = locator.title
            )
            notesRepository.saveNote(manifestUrl, item)
            refresh()
        }
    }

    /** Same upsert route as create -- editing is just re-saving with a fresh updatedAt. */
    fun updateNoteText(id: String, text: String) {
        val existing = noteById(id) ?: return
        scope.launch {
            notesRepository.saveNote(manifestUrl, existing.copy(text = text, updatedAt = System.currentTimeMillis().toDouble()))
            refresh()
        }
    }

    fun deleteNote(id: String) {
        scope.launch {
            notesRepository.deleteNote(manifestUrl, id)
            refresh()
        }
    }

    private fun locatorToJson(locator: Locator): JsonElement = Json.parseToJsonElement(locator.toJSON().toString())

    private fun parseLocator(json: JsonElement): Locator? =
        runCatching { Locator.fromJSON(JSONObject(json.toString())) }.getOrNull()
}
