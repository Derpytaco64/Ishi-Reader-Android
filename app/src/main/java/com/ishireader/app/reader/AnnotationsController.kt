package com.ishireader.app.reader

import android.util.Log
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

/** Grep target for the decoration diagnostics -- pairs with Readium's own "Can't locate DOM range
 *  for decoration" log, which is what a decoration that reached the navigator but couldn't be
 *  anchored in the page reports instead. */
private const val LOG_TAG = "IshiAnnotations"

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

    private suspend fun applyDecorations(force: Boolean = false) {
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

        // CLAUDE-ADDED: Anything dropped here never reaches the navigator, so it can't decorate no
        // matter what the rendering side does -- log it, since a decoration that's listed in the
        // annotations panel but invisible in the text looks identical to a rendering failure.
        logDecorationCounts(highlightDecorations.size, noteDecorations.size)

        if (force) {
            // CLAUDE-ADDED: applyDecorations() is a *diff* against the last list the navigator was
            // given for this group (EpubNavigatorViewModel keeps one per group and runs
            // changesByHref against it), so re-submitting an identical list produces zero changes
            // and zero JavaScript -- a plain re-push is silently a no-op. Clearing first makes the
            // second call a genuine list of Added changes. The clear is scoped to every loaded
            // resource and the re-add to each decoration's own href, so this only ever rebuilds
            // what's currently on screen; chapters loaded later are decorated by Readium's own
            // onResourceLoaded replay off the same per-group state this leaves behind.
            nav.applyDecorations(emptyList(), ANNOTATIONS_GROUP_HIGHLIGHTS)
            nav.applyDecorations(emptyList(), ANNOTATIONS_GROUP_NOTES)
        }

        nav.applyDecorations(highlightDecorations, ANNOTATIONS_GROUP_HIGHLIGHTS)
        nav.applyDecorations(noteDecorations, ANNOTATIONS_GROUP_NOTES)
    }

    private fun logDecorationCounts(highlights: Int, notes: Int) {
        val skippedHighlights = _state.value.highlights.size - highlights
        val skippedNotes = _state.value.notes.size - notes
        Log.i(
            LOG_TAG,
            "decorations: $highlights/${_state.value.highlights.size} highlights, " +
                "$notes/${_state.value.notes.size} notes" +
                if (skippedHighlights > 0 || skippedNotes > 0) {
                    " -- skipped $skippedHighlights highlight(s) and $skippedNotes note(s) with an " +
                        "unusable locator"
                } else {
                    ""
                }
        )
    }

    /** Re-pushes the current highlight/note decorations to the navigator without re-fetching from
     *  the server -- see ReaderActivity's currentLocator collector, which calls this every time the
     *  visible chapter changes as a defense against decorations that raced the initial fetch.
     *  Forced, because an unforced re-push of an unchanged list is a no-op (see [applyDecorations]). */
    fun reapplyDecorations() {
        scope.launch { applyDecorations(force = true) }
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
