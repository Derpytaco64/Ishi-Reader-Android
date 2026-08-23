package com.ishireader.app.data.repository

import android.util.Log
import com.ishireader.app.data.local.CachedAniListEntryDao
import com.ishireader.app.data.local.CachedAniListEntryEntity
import com.ishireader.app.data.local.CachedUserDao
import com.ishireader.app.data.local.PendingAniListPatchDao
import com.ishireader.app.data.local.PendingAniListPatchEntity
import com.ishireader.app.data.model.AniListExchangeRequest
import com.ishireader.app.data.model.AniListMedia
import com.ishireader.app.data.model.AniListMediaListEntry
import com.ishireader.app.data.model.AniListSearchResult
import com.ishireader.app.data.model.AniListTitle
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.Response

/** Manga-only AniList sync -- connect/disconnect/search/read/write here, all against Ishi-Read's
 *  own proxy routes (never graphql.anilist.co directly, see AniListModels.kt's file doc).
 *
 *  Progress/tracking-field writes ([patchEntry]) are local-first and offline-capable, same shape as
 *  [LibraryPrefsRepository]: merged into a Room outbox row immediately, with a best-effort inline
 *  push attempted right away and [com.ishireader.app.data.sync.AniListSyncWorker] as the fallback.
 *  A manual edit from the tracking sheet (status/score/progress/dates/repeat) is a deliberate user
 *  choice and always stays last-write-wins, including a `progress` field the user typed in lower
 *  than what's already there -- see [TrackingSheet][com.ishireader.app.ui.bookdetail.TrackingSheet].
 *  [MangaAniListProgressTracker][com.ishireader.app.reader.MangaAniListProgressTracker]'s own
 *  chapter-advance pushes are the one caller that opts into [clampProgressPatch] (`clampProgress =
 *  true`), since those are inferred from page position rather than a deliberate user choice, and
 *  AniList's own state isn't exclusively owned by this app (the user can also edit it from AniList's
 *  own site/app) -- an inferred push should never regress below the highest value already known
 *  locally (cached remote + anything still pending). */
class AniListRepository(
    private val network: NetworkModule,
    private val cachedUserDao: CachedUserDao,
    private val cachedAniListEntryDao: CachedAniListEntryDao,
    private val pendingPatchDao: PendingAniListPatchDao,
    private val syncScheduler: SyncScheduler
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Cached AniList score format for the signed-in user (POINT_100/POINT_10_DECIMAL/POINT_10/
     *  POINT_5/POINT_3), read off the same cached user record the top bar uses -- avoids threading
     *  PublicUser through the tracking sheet just for this one field. */
    suspend fun getScoreFormat(): String? = cachedUserDao.get()?.anilistScoreFormat

    /** URL to open in a Custom Tab to start the PIN flow -- null means the instance hasn't set up
     *  an AniList client_id/secret in Admin Settings yet. */
    suspend fun getAuthorizeUrl(): ApiResult<String?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getAniListAuthorizeUrl()
            if (response.isSuccessful) {
                ApiResult.Success(response.body()?.url)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't reach AniList"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    /** Completes the PIN flow: [code] is what the user copy-pasted off AniList's own /oauth/pin
     *  page after approving the app in a Custom Tab. */
    suspend fun connect(code: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.exchangeAniListCode(AniListExchangeRequest(code))
            val body = response.body()
            if (response.isSuccessful && body?.connected == true) {
                refreshCachedConnection(true, body.scoreFormat)
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't connect to AniList"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    /** Forgets the token on the server -- see the server route's own note: AniList itself has no
     *  revocation endpoint, so this doesn't invalidate the token, only stops using it. */
    suspend fun disconnect(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.disconnectAniList()
            if (response.isSuccessful) {
                refreshCachedConnection(false, null)
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't disconnect AniList"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    /** Manga/one-shot only -- see the server route's format_in filter. Inherently online-only,
     *  same as the connect flow -- no offline fallback needed. */
    suspend fun search(query: String): ApiResult<List<AniListSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.searchAniList(query)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.results)
            } else {
                onPossibleAuthError(response.code())
                ApiResult.Failure(response.serverErrorMessage("AniList search failed"), isAuthError = response.code() == 401)
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    /** Live fetch, used to populate the tracking sheet when it opens -- also refreshes the local
     *  cache, which both drives offline display and raises the "remote" floor [patchEntry]'s
     *  progress clamp compares against, so opening the sheet is what picks up a change made
     *  elsewhere (AniList's own site/app) since the last sync. */
    suspend fun getEntry(mediaId: Int): ApiResult<AniListMedia> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getAniListEntry(mediaId)
            val media = response.body()?.media
            if (response.isSuccessful && media != null) {
                cachedAniListEntryDao.set(CachedAniListEntryEntity(mediaId, json.encodeToString(AniListMedia.serializer(), media), System.currentTimeMillis()))
                ApiResult.Success(media)
            } else {
                onPossibleAuthError(response.code())
                ApiResult.Failure(response.serverErrorMessage("Couldn't load AniList entry"), isAuthError = response.code() == 401)
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    /** Last-known state for offline rendering -- null if this media has never been fetched on this
     *  device (not the same as "not on the AniList list yet", which is a non-null [AniListMedia]
     *  with a null mediaListEntry). */
    suspend fun getCachedEntry(mediaId: Int): AniListMedia? = withContext(Dispatchers.IO) {
        cachedAniListEntryDao.get(mediaId)?.mediaJson
            ?.let { runCatching { json.decodeFromString(AniListMedia.serializer(), it) }.getOrNull() }
    }

    /** Merges [patch] (only the fields actually changing -- status/score/progress/repeat/startedAt/
     *  completedAt, an explicit JsonNull clears a field, an absent key leaves it untouched) into the
     *  pending outbox row for [mediaId] and makes one best-effort inline push attempt. Any failure
     *  (offline, or the server/AniList itself failing) leaves the merged patch queued for
     *  [com.ishireader.app.data.sync.AniListSyncWorker] and is still reported as a success to the
     *  caller, same "the edit is safely saved either way" contract as LibraryPrefsRepository.patchPrefs. */
    /** Runs under [NonCancellable] -- callers push this from lifecycle/viewModel-scoped coroutines
     *  (e.g. [MangaAniListProgressTracker][com.ishireader.app.reader.MangaAniListProgressTracker]'s
     *  per-page-turn pushes on ReaderActivity's lifecycleScope), and without this a page turned right
     *  before backing out of the reader could have its outbox write cancelled by scope teardown
     *  before [pendingPatchDao.upsert] ever runs -- silently dropping the edit instead of queuing it
     *  for [com.ishireader.app.data.sync.AniListSyncWorker], same failure class ReaderActivity.onPause
     *  already documents for position saves. */
    /** [autoCompleteOnLastChapter], when true, also injects `status: COMPLETED` if [patch]'s
     *  (post-clamp) `progress` reaches the series' known total chapter count -- see
     *  [applyAutoComplete]. Runs under the same [outboxMutex] as the clamp/merge below so it sees
     *  the same atomic snapshot of cache + pending outbox, offline included (both reads are local
     *  Room lookups, no network). Only [MangaAniListProgressTracker]'s inferred pushes opt in; a
     *  manual tracking-sheet edit is a deliberate user choice that shouldn't be second-guessed. */
    suspend fun patchEntry(
        mediaId: Int,
        patch: JsonObject,
        clampProgress: Boolean = false,
        autoCompleteOnLastChapter: Boolean = false
    ): ApiResult<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        outboxMutex.withLock {
            Log.d("AniListDbg", "patchEntry called mediaId=$mediaId patch=$patch clampProgress=$clampProgress")
            val clamped = if (clampProgress) clampProgressPatch(mediaId, patch) else patch
            val effectivePatch = if (autoCompleteOnLastChapter) applyAutoComplete(mediaId, clamped) else clamped
            val pending = JsonObject(readPendingPatch(mediaId) + effectivePatch)
            Log.d("AniListDbg", "effectivePatch=$effectivePatch pending=$pending")
            if (pending.isEmpty()) { Log.d("AniListDbg", "pending empty, bailing before outbox write"); return@withLock ApiResult.Success(Unit) }
            pendingPatchDao.upsert(PendingAniListPatchEntity(mediaId, pending.toString(), System.currentTimeMillis()))

            try {
                val body = JsonObject(pending + mapOf("mediaId" to JsonPrimitive(mediaId)))
                val response = network.api.saveAniListEntry(body)
                val entry = response.body()?.entry
                if (response.isSuccessful && entry != null) {
                    cacheSavedEntry(mediaId, entry)
                    pendingPatchDao.remove(mediaId)
                    ApiResult.Success(Unit)
                } else {
                    onPossibleAuthError(response.code())
                    syncScheduler.scheduleAniListSync()
                    ApiResult.Success(Unit)
                }
            } catch (e: Exception) {
                syncScheduler.scheduleAniListSync()
                ApiResult.Success(Unit)
            }
        }
    }

    private suspend fun readPendingPatch(mediaId: Int): JsonObject =
        pendingPatchDao.get(mediaId)?.patchJson
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())

    /** Drops a `progress` field that wouldn't actually move the known value forward -- the known
     *  value is the higher of the last-fetched cache and whatever's still sitting unsynced in the
     *  outbox, so a burst of chapter-advance writes queued back-to-back while offline still clamps
     *  correctly against each other, not just against the stale cache. Every other field passes
     *  through untouched -- see the class doc for why only progress needs this. */
    private suspend fun clampProgressPatch(mediaId: Int, patch: JsonObject): JsonObject {
        val incoming = (patch["progress"] as? JsonPrimitive)?.intOrNull ?: return patch
        val cached = getCachedEntry(mediaId)?.mediaListEntry?.progress ?: 0
        val pendingProgress = (readPendingPatch(mediaId)["progress"] as? JsonPrimitive)?.intOrNull ?: 0
        val known = maxOf(cached, pendingProgress)
        return if (incoming > known) patch else JsonObject(patch - "progress")
    }

    /** Injects `status: COMPLETED` alongside a `progress` push that reaches the series' last known
     *  chapter -- mirrors Tachiyomi's own "mark completed" behavior. A no-op (returns [patch]
     *  unchanged) whenever: [patch] has no `progress` key (nothing to check, e.g.
     *  [clampProgressPatch] already dropped it as a non-advance); the total chapter count isn't
     *  cached yet (this device never fetched this series' entry, so "last chapter" is unknown); or
     *  the *effective* status -- a still-pending, not-yet-synced status edit from the outbox if one
     *  exists, else the last-synced cached status -- is already COMPLETED (no churn). Checking the
     *  pending patch rather than only the cache matters offline: a manual status edit queued earlier
     *  in the same offline session hasn't reached [cacheSavedEntry] yet, so reading the cache alone
     *  could stomp it right back to COMPLETED.
     *
     *  If the effective status is REPEATING -- a reread in progress, started via
     *  [com.ishireader.app.ui.bookdetail.TrackingViewModel.setStatus]'s progress-reset -- reaching
     *  the last chapter again completes *that* reread: status flips back to COMPLETED and `repeat`
     *  increments by one, mirroring Tachiyomi's own rereading-completion behavior. The effective
     *  repeat count (pending outbox value if one's already queued this session, else the last-synced
     *  cache) is read the same offline-safe way as effectiveStatus, so this is correct even fully
     *  offline -- both reads are local Room lookups under the same [outboxMutex] as the merge below. */
    private suspend fun applyAutoComplete(mediaId: Int, patch: JsonObject): JsonObject {
        val progress = (patch["progress"] as? JsonPrimitive)?.intOrNull ?: return patch
        val totalChapters = getCachedEntry(mediaId)?.chapters ?: return patch
        if (progress < totalChapters) return patch

        val pending = readPendingPatch(mediaId)
        val cachedEntry = getCachedEntry(mediaId)?.mediaListEntry
        val effectiveStatus = (pending["status"] as? JsonPrimitive)?.content ?: cachedEntry?.status
        if (effectiveStatus == "COMPLETED") return patch

        return if (effectiveStatus == "REPEATING") {
            val effectiveRepeat = (pending["repeat"] as? JsonPrimitive)?.intOrNull ?: cachedEntry?.repeat ?: 0
            JsonObject(
                patch + mapOf(
                    "status" to JsonPrimitive("COMPLETED"),
                    "repeat" to JsonPrimitive(effectiveRepeat + 1)
                )
            )
        } else {
            JsonObject(patch + mapOf("status" to JsonPrimitive("COMPLETED")))
        }
    }

    /** Updates just the mediaListEntry portion of the cache after a successful save, preserving
     *  whatever title/cover/chapters info is already cached (or starting from a title-less stub if
     *  this media was never fetched via [getEntry] before its first patch -- a later sheet-open
     *  fills those in properly). */
    private suspend fun cacheSavedEntry(mediaId: Int, entry: AniListMediaListEntry) {
        val existing = getCachedEntry(mediaId)
        val updated = (existing ?: AniListMedia(id = mediaId, chapters = null, title = AniListTitle()))
            .copy(mediaListEntry = entry)
        cachedAniListEntryDao.set(CachedAniListEntryEntity(mediaId, json.encodeToString(AniListMedia.serializer(), updated), System.currentTimeMillis()))
    }

    /** Keeps the top bar / user menu's cached anilistConnected/anilistScoreFormat fields in sync
     *  immediately after a connect/disconnect, rather than waiting for the next full /api/auth/me
     *  refresh. */
    private suspend fun refreshCachedConnection(connected: Boolean, scoreFormat: String?) {
        val cached = cachedUserDao.get() ?: return
        cachedUserDao.set(cached.copy(anilistConnected = connected, anilistScoreFormat = scoreFormat))
    }

    /** A 401 from any of the AniList-proxy routes above means the stored token is missing, expired,
     *  or was revoked from AniList's own site -- AniList issues no refresh token, so there's nothing
     *  to silently recover here. Clearing the cached connected flag makes the account sheet and
     *  tracking sheet fall back to their "not connected" state on next open, which is what prompts
     *  the user to reconnect, instead of them seeing a stale "connected" UI paired with errors. */
    private suspend fun onPossibleAuthError(httpCode: Int) {
        if (httpCode == 401) refreshCachedConnection(false, null)
    }

    companion object {
        /** Shared with [com.ishireader.app.data.sync.AniListSyncWorker] -- see class doc and
         *  LibraryPrefsRepository.outboxMutex for why this must be shared rather than per-instance. */
        val outboxMutex = Mutex()
    }
}

// CLAUDE-ADDED: Named distinctly from AuthRepository's own private ErrorBody/serverErrorMessage
// pair -- Kotlin top-level classes keep one FqName per package regardless of `private`, so two
// same-named private top-level classes in the same package collide as a redeclaration even though
// each is only meant to be visible within its own file.
@Serializable
private data class AniListErrorBody(val error: String? = null)

/** Same convention as AuthRepository's own private copy -- see that file's comment. */
private fun <T> Response<T>.serverErrorMessage(fallback: String): String {
    val raw = errorBody()?.string()
    val parsed = raw?.let { runCatching { Json.decodeFromString<AniListErrorBody>(it) }.getOrNull() }
    return parsed?.error?.takeIf { it.isNotBlank() } ?: "$fallback (${code()})"
}
