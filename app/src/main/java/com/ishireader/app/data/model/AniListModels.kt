package com.ishireader.app.data.model

import kotlinx.serialization.Serializable

/** Manga-only AniList sync (light novels are out of scope, see comicInfo.ts's format_in filter on
 *  the server) -- every DTO here mirrors the shape of Ishi-Read's api/anilist and api/auth/anilist
 *  route trees, which do all the actual AniList GraphQL talking. This app's network layer never
 *  calls graphql.anilist.co directly. */

/** null [url] means the instance hasn't set up an AniList client_id/secret in Admin Settings yet. */
@Serializable
data class AniListAuthorizeUrlResponse(val url: String? = null)

@Serializable
data class AniListExchangeRequest(val code: String)

@Serializable
data class AniListExchangeResponse(
    val connected: Boolean = false,
    val anilistUserId: Int? = null,
    val scoreFormat: String? = null
)

@Serializable
data class AniListDisconnectResponse(val connected: Boolean = false)

@Serializable
data class AniListTitle(val romaji: String? = null, val english: String? = null)

@Serializable
data class AniListCoverImage(val medium: String? = null)

@Serializable
data class AniListSearchResult(
    val id: Int,
    val title: AniListTitle,
    val coverImage: AniListCoverImage? = null,
    val format: String? = null,
    val chapters: Int? = null
)

@Serializable
data class AniListSearchResponse(val results: List<AniListSearchResult> = emptyList())

@Serializable
data class AniListFuzzyDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

/** status is one of AniList's MediaListStatus enum values as a plain string (CURRENT/PLANNING/
 *  COMPLETED/DROPPED/PAUSED/REPEATING) -- the UI maps these to display labels itself. score follows
 *  whatever scoreFormat the AniList account uses (see PublicUser/UserRecord.anilistScoreFormat,
 *  fetched once at connect time), not a fixed 0-100 scale. */
@Serializable
data class AniListMediaListEntry(
    val id: Int,
    val status: String,
    val score: Double = 0.0,
    val progress: Int = 0,
    val repeat: Int = 0,
    val startedAt: AniListFuzzyDate? = null,
    val completedAt: AniListFuzzyDate? = null
)

@Serializable
data class AniListMedia(
    val id: Int,
    val chapters: Int? = null,
    val title: AniListTitle,
    val coverImage: AniListCoverImage? = null,
    /** Null until the user has actually added this to their AniList list -- the tracking sheet
     *  treats a null entry as "not yet linked/added", not an error. */
    val mediaListEntry: AniListMediaListEntry? = null
)

@Serializable
data class AniListMediaEntryResponse(val media: AniListMedia)

@Serializable
data class AniListSaveEntryResponse(val entry: AniListMediaListEntry)

/** One series' link to an AniList media entry -- stored as the "anilistLinks" key in the
 *  library-prefs freeform blob (see LibraryPrefsRepository), keyed by a normalized series name (or
 *  a standalone book's own identity key when it has no series), same shallow-merge-per-key
 *  convention as customShelves/theme/accentColor. syncEnabled lets a user link a series for manual
 *  tracking without opting into automatic chapter-progress pushes from the reader. */
@Serializable
data class AniListLink(val mediaId: Int, val syncEnabled: Boolean = true)
