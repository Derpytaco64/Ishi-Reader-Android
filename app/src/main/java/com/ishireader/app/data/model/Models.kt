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

/** The trimmed, unauthenticated user summary the login picker fetches -- see /api/auth/users,
 *  which deliberately strips isAdmin/needsPasswordSetup before anyone is signed in. */
@Serializable
data class PublicUsersResponse(val users: List<PublicUser> = emptyList())

@Serializable
data class SetupPasswordRequest(val userId: String, val password: String)

/** [image] is a data URL ("data:image/png;base64,...") -- mirrors the website's client-side
 *  FileReader.readAsDataURL, since /api/auth/avatar expects the same JSON shape either way. */
@Serializable
data class AvatarUploadRequest(val image: String)

@Serializable
data class AvatarUploadResponse(val avatarUrl: String? = null)

@Serializable
data class UpdateProfileRequest(val name: String)

@Serializable
data class UpdateProfileResponse(val user: PublicUser? = null)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class ChangePasswordResponse(val ok: Boolean = false)

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

@Serializable
data class NoteUpsertRequest(val manifestUrl: String, val item: StoredNote)

/** One of the fixed 5-swatch highlight colors -- see HIGHLIGHT_COLORS in the website, "yellow"/
 *  "green"/"blue"/"pink"/"purple", stored as its id string not a hex value. */
@Serializable
data class StoredHighlight(
    val id: String,
    val locator: JsonElement? = null,
    val color: String,
    val createdAt: Double,
    val chapterTitle: String? = null
)

@Serializable
data class HighlightsResponse(val items: List<StoredHighlight> = emptyList())

@Serializable
data class HighlightUpsertRequest(val manifestUrl: String, val item: StoredHighlight)

@Serializable
data class StoredBookmark(
    val id: String,
    val locator: JsonElement? = null,
    val createdAt: Double,
    val chapterTitle: String? = null
)

@Serializable
data class BookmarksResponse(val items: List<StoredBookmark> = emptyList())

@Serializable
data class BookmarkUpsertRequest(val manifestUrl: String, val item: StoredBookmark)

/** One calendar day's reading within a completed run -- mirrors DailyReadingBucket.ts. wpm/percent
 *  are derived from seconds/words/progressionDelta at render time rather than stored precomputed. */
@Serializable
data class DailyReadingBucket(
    val date: String,
    val seconds: Double = 0.0,
    val words: Double = 0.0,
    val progressionDelta: Double = 0.0
)

/** One archived reading run, created whenever the in-reader timer is reset with "save" -- mirrors
 *  StoredCompletedReadTime.ts. dailyHistory is optional since entries saved before that field
 *  existed have none. */
@Serializable
data class StoredCompletedReadTime(
    val id: String,
    val seconds: Double,
    val completedAt: Double,
    val dailyHistory: List<DailyReadingBucket>? = null
)

@Serializable
data class CompletedReadTimesResponse(val items: List<StoredCompletedReadTime> = emptyList())

@Serializable
data class CompletedReadTimeUpsertRequest(val manifestUrl: String, val item: StoredCompletedReadTime)

/** The live per-book active-seconds counter -- overwritten wholesale on each flush, not appended
 *  to (mirrors readingTime/<bookHash>.json being a single raw number, not an array). */
@Serializable
data class ReadingTimeResponse(val seconds: Double? = null)

@Serializable
data class ReadingTimeRequest(val manifestUrl: String, val seconds: Double)

/** Carries one book's progress/annotations/reading-time onto a different library entry -- see
 *  api/userdata/migrateBookData/route.ts. Source and destination must resolve to different content
 *  hashes; the server rejects an exact-hash match. */
@Serializable
data class MigrateBookDataRequest(val sourceManifestUrl: String, val destManifestUrl: String)

/** Computed once per book (whitespace-token count of every resource's body text) and persisted
 *  forever -- never recomputed once set, mirrors useBookWordCount.ts. */
@Serializable
data class WordCountResponse(val wordCount: Double? = null)

@Serializable
data class WordCountRequest(val manifestUrl: String, val wordCount: Double)

/** Computed server-side on first request (the original Thorium Reader's 1024-characters-per-page
 *  estimate) and cached forever after -- mirrors pageCountApi.ts. GET-only; unlike word count,
 *  the client never pushes this value, the server always derives it. */
@Serializable
data class PageCountResponse(val pageCount: Int? = null)

/** One accepted reading-speed observation between two locator-change events -- mirrors
 *  ReadingSpeedSample.ts. Global/cross-book, not scoped to a single manifestUrl. */
@Serializable
data class ReadingSpeedSample(val deltaWords: Double, val deltaSeconds: Double, val timestamp: Double)

@Serializable
data class ReadingSpeedSamplesResponse(val samples: List<ReadingSpeedSample> = emptyList())

@Serializable
data class ReadingSpeedSamplesRequest(val samples: List<ReadingSpeedSample>)

@Serializable
data class DailyReadingHistoryResponse(val buckets: List<DailyReadingBucket> = emptyList())

@Serializable
data class DailyReadingHistoryRequest(val manifestUrl: String, val buckets: List<DailyReadingBucket>)

/** Mirrors AdminPageClient.tsx's local AdminUser shape and /api/admin/users' stripSecrets output
 *  (passwordHash/passwordSalt stripped server-side) -- only ever fetched by an admin. */
@Serializable
data class AdminUser(
    val id: String,
    val username: String,
    val name: String,
    val isAdmin: Boolean = false,
    val avatarExt: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val createdAt: Long = 0L,
    val disabled: Boolean = false,
    /** Whether any of this user's sessions was active in roughly the last few minutes -- see
     *  getActiveUserIds in the server's auth.ts. */
    val isActive: Boolean = false
)

@Serializable
data class AdminUsersResponse(val users: List<AdminUser> = emptyList())

@Serializable
data class AdminUserResponse(val user: AdminUser? = null)

/** Mirrors OrphanedBook/OrphanedUserData/OrphanedDataReport in the server's
 *  next-lib/userData/orphanedData.ts -- the shape /api/admin/orphaned-data's GET/DELETE both return. */
@Serializable
data class OrphanedBook(
    val hash: String,
    val subdirs: List<String> = emptyList(),
    val title: String? = null,
    val manifestUrl: String? = null
)

@Serializable
data class OrphanedUserData(
    val userId: String,
    val username: String,
    val name: String,
    val books: List<OrphanedBook> = emptyList(),
    val fileCount: Int = 0
)

@Serializable
data class OrphanedDataReport(val users: List<OrphanedUserData> = emptyList(), val totalFiles: Int = 0)

/** /api/admin/reading-speed-samples' DELETE response -- how many users' rolling WPM buffers got
 *  cleared. */
@Serializable
data class ClearedSpeedSamplesResponse(val clearedCount: Int = 0)

/** /api/books' DELETE response -- how many entries were in the server's in-memory manifest cache
 *  (title/author/cover resolution, keyed by content fingerprint) before it was cleared. */
@Serializable
data class ClearedManifestCacheResponse(val clearedCount: Int = 0)

@Serializable
data class CreateUserRequest(val username: String, val name: String, val password: String, val isAdmin: Boolean)

@Serializable
data class ResetPasswordRequest(val newPassword: String)

@Serializable
data class BookFolderField(val bookFolder: String = "")

@Serializable
data class ReadiumUrlField(val readiumUrl: String = "")

/** Mirrors the server's split GET (number, e.g. `getReadiumServerPort()`) vs POST body (string,
 *  parsed by `normalizeReadiumPort`) shape for the same field -- two classes because the wire type
 *  differs by direction, unlike every other settings field here. */
@Serializable
data class ReadiumPortField(val readiumPort: Int = 0)

@Serializable
data class ReadiumPortRequest(val readiumPort: String)

@Serializable
data class UserDataFolderField(val userDataFolder: String = "")

@Serializable
data class LoginAccentColorField(val loginAccentColor: String = "#2f6fed")

@Serializable
data class LoginThemeModeField(val loginThemeMode: String = "dark")

/** Audiobook counterpart to StoredCompletedReadTime -- deliberately simpler (no wordCount/wpm
 *  equivalent, see the website's listeningTimeTypes.ts), just when a listen-through started and
 *  finished, plus its own day-by-day breakdown. */
@Serializable
data class StoredCompletedListen(
    val id: String,
    val startedAt: Double,
    val completedAt: Double,
    val dailyHistory: List<DailyListeningBucket>? = null
)

@Serializable
data class CompletedListensResponse(val items: List<StoredCompletedListen> = emptyList())

@Serializable
data class CompletedListenUpsertRequest(val manifestUrl: String, val item: StoredCompletedListen)

/** One calendar day's listening within a listen-through -- audiobook counterpart of
 *  DailyReadingBucket, minus the words/wpm dimension (progress is already exact via the position
 *  locator's own totalProgression, see AudiobookLocator.kt, so there's no reading-speed estimate
 *  to derive here). */
@Serializable
data class DailyListeningBucket(
    val date: String,
    val seconds: Double = 0.0,
    val progressionDelta: Double = 0.0
)

@Serializable
data class DailyListeningHistoryResponse(val buckets: List<DailyListeningBucket> = emptyList())

@Serializable
data class DailyListeningHistoryRequest(val manifestUrl: String, val buckets: List<DailyListeningBucket>)

/** accumulatedSeconds is a lifetime total for the book (never reset on completion, unlike
 *  ReadingTimeResponse); startedAt is the current listen-through's start marker, null when
 *  nothing is in progress -- mirrors the website's StoredListeningTime. */
@Serializable
data class ListeningTimeData(
    val accumulatedSeconds: Double = 0.0,
    val startedAt: Double? = null
)

@Serializable
data class ListeningTimeResponse(val data: ListeningTimeData? = null)

@Serializable
data class ListeningTimeRequest(val manifestUrl: String, val accumulatedSeconds: Double, val startedAt: Double?)

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
