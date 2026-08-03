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
