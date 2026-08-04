package com.ishireader.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val lockedUntil: Long? = null,
    val needsPasswordSetup: Boolean? = null,
    val userId: String? = null
)

@Serializable
data class PublicUser(
    val id: String,
    val username: String,
    val name: String? = null,
    val isAdmin: Boolean = false,
    val avatarUrl: String? = null,
    val needsPasswordSetup: Boolean = false
)

@Serializable
data class MeResponse(val user: PublicUser? = null)

@Serializable
data class BookSeries(val name: String, val position: Double? = null)

@Serializable
data class Book(
    val title: String,
    val author: String,
    val cover: String,
    val url: String,
    val rendition: String? = null,
    val isAudiobook: Boolean = false,
    val addedAt: Double? = null,
    val lastReadAt: Double? = null,
    val series: BookSeries? = null,
    val description: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val modified: String? = null,
    val language: String? = null,
    val tags: List<String> = emptyList(),
    val isbn: String? = null,
    val calibreId: String? = null,
    val uuid: String? = null,
    val fileSize: String? = null
)

@Serializable
data class BooksResponse(val books: List<Book> = emptyList())

/**
 * The `locator` field is passed through as raw JSON rather than a hand-rolled data class:
 * Ishi-Read's server stores whatever Locator JSON the Readium navigator hands it, and the
 * Kotlin toolkit's own `Locator.toJSON()` / `Locator.fromJSON()` already define that shape.
 * Duplicating it here would just be another place for the two to drift apart.
 */
@Serializable
data class PositionResponse(val locator: JsonElement? = null)

@Serializable
data class PositionRequest(val manifestUrl: String, val locator: JsonElement)

@Serializable
data class ApiError(val error: String? = null)

/**
 * `libraryPrefs` is a freeform per-user JSON blob the server shallow-merges on every POST
 * (theme, accentColor, customShelves, continueReadingDismissed, etc.) -- passed through as raw
 * JSON here too, same reasoning as PositionResponse's locator, since this app only ever reads
 * or patches a handful of its keys rather than owning the whole shape.
 */
@Serializable
data class LibraryPrefsResponse(val libraryPrefs: JsonElement? = null)

/** One book's membership in a shelf -- addedAt is when it joined *this* shelf, distinct from
 *  Book.addedAt (when it was added to the library). */
@Serializable
data class ShelfBookEntry(val url: String, val addedAt: Double)

/** customShelves is stored server-side as a flat array under library-prefs, not an object keyed
 *  by id -- the array's own order *is* display order, there's no separate ordering field. */
@Serializable
data class CustomShelf(
    val id: String,
    val name: String,
    val icon: String,
    val books: List<ShelfBookEntry> = emptyList()
)

/** locator is passed through raw for the same reason as PositionResponse.locator -- this app only
 *  ever needs to read `locator.text.highlight` out of it (see NotesMarkdown.kt) for the export's
 *  optional quoted passage. */
@Serializable
data class StoredNote(
    val id: String,
    val locator: JsonElement? = null,
    val text: String,
    val createdAt: Double,
    val updatedAt: Double? = null,
    val chapterTitle: String? = null
)

@Serializable
data class NotesResponse(val items: List<StoredNote> = emptyList())

/** Mirrors the server's UserStats shape (src/lib/userData/statsTypes.ts) -- a whole-library
 *  aggregate for the current user, not per-book like everything else here. Time totals are
 *  Double (accumulated from per-session floating-point seconds), unlike the plain integer counts.
 *  null averageWpm means no eligible reading-speed sample exists yet, not a rate of zero. */
@Serializable
data class UserStats(
    val booksInLibrary: Int = 0,
    val booksStarted: Int = 0,
    val booksFinished: Int = 0,
    val totalReadingSeconds: Double = 0.0,
    val totalWordsRead: Int = 0,
    val averageWpm: Int? = null,
    val currentStreakDays: Int = 0,
    val highlightsCount: Int = 0,
    val bookmarksCount: Int = 0,
    val notesCount: Int = 0,
    val audiobooksInLibrary: Int = 0,
    val audiobooksStarted: Int = 0,
    val audiobooksFinished: Int = 0,
    val totalListeningSeconds: Double = 0.0
)
