package com.ishireader.app.data.repository

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
 *  Unlike library-prefs, AniList's own state isn't exclusively owned by this app (the user can also
 *  edit it from AniList's own site/app), so a `progress` patch is clamped to never regress below the
 *  highest value already known locally (cached remote + anything still pending) -- see
 *  [clampProgressPatch]. Every other field (status/score/dates/repeat) is a deliberate user edit
 *  from the tracking sheet and stays last-write-wins, same as library-prefs' own patch semantics. */
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
                ApiResult.Failure(response.serverErrorMessage("AniList search failed"))
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
                ApiResult.Failure(response.serverErrorMessage("Couldn't load AniList entry"))
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
    suspend fun patchEntry(mediaId: Int, patch: JsonObject): ApiResult<Unit> = withContext(Dispatchers.IO) {
        outboxMutex.withLock {
            val pending = JsonObject(readPendingPatch(mediaId) + clampProgressPatch(mediaId, patch))
            if (pending.isEmpty()) return@withLock ApiResult.Success(Unit)
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
