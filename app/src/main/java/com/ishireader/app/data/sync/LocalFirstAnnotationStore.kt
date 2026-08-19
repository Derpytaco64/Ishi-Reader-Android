package com.ishireader.app.data.sync

import com.ishireader.app.data.local.AnnotationCacheDao
import com.ishireader.app.data.local.AnnotationCacheEntity
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.local.AnnotationOutboxEntity
import com.ishireader.app.data.network.ApiResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val OP_UPSERT = "UPSERT"
private const val OP_DELETE = "DELETE"

/**
 * Generic local-first CRUD store for one manifestUrl-scoped, id-keyed annotation list --
 * highlights, bookmarks, and notes all share this exact server shape (GET the list, POST
 * upsert-by-id, DELETE by id; see HighlightUpsertRequest/BookmarkUpsertRequest/NoteUpsertRequest),
 * so one implementation covers all three instead of three copies. Unlike ReadingTimerRepository
 * there's no merge-math needed -- an upsert or delete only ever touches its own id, so queuing and
 * replaying it later can't corrupt the rest of the list the way a whole-value overwrite could.
 *
 * Every write is applied to the local cache and queued in the outbox *before* the network call
 * (mirrors LibraryPrefsRepository.patchPrefs), so a highlight/bookmark/note made offline is saved
 * on-device immediately and drained by [AnnotationsSyncWorker] once connectivity returns, instead
 * of the old fire-and-forget POST silently dropping it.
 */
class LocalFirstAnnotationStore<T>(
    private val kind: String,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> String,
    private val cacheDao: AnnotationCacheDao,
    private val outboxDao: AnnotationOutboxDao,
    private val syncScheduler: SyncScheduler,
    private val fetchFromServer: suspend (manifestUrl: String) -> ApiResult<List<T>>,
    private val pushUpsert: suspend (manifestUrl: String, item: T) -> Boolean,
    private val pushDelete: suspend (manifestUrl: String, id: String) -> Boolean
) {
    private val listSerializer = ListSerializer(serializer)

    /** Fetches fresh from the server when reachable (refreshing the cache), otherwise falls back
     *  to the cache -- either way, always re-applies this device's own not-yet-confirmed outbox
     *  writes on top, so a create/edit/delete made moments ago shows up immediately even if the
     *  GET that raced it hasn't reflected it server-side yet. */
    suspend fun getAll(manifestUrl: String): ApiResult<List<T>> {
        val fetched = fetchFromServer(manifestUrl)
        val base = when (fetched) {
            is ApiResult.Success -> {
                cacheDao.set(AnnotationCacheEntity(kind, manifestUrl, encodeList(fetched.data), now()))
                fetched.data
            }
            is ApiResult.Failure -> readCache(manifestUrl)
        }
        return ApiResult.Success(applyOutbox(manifestUrl, base))
    }

    suspend fun save(manifestUrl: String, item: T) {
        val id = idOf(item)
        writeCacheUpsert(manifestUrl, item)
        outboxDao.upsert(AnnotationOutboxEntity(kind, manifestUrl, id, OP_UPSERT, encodeOne(item), now()))
        syncScheduler.scheduleAnnotationsSync()
        trySync(manifestUrl, id)
    }

    suspend fun delete(manifestUrl: String, id: String) {
        writeCacheDelete(manifestUrl, id)
        outboxDao.upsert(AnnotationOutboxEntity(kind, manifestUrl, id, OP_DELETE, null, now()))
        syncScheduler.scheduleAnnotationsSync()
        trySync(manifestUrl, id)
    }

    private suspend fun trySync(manifestUrl: String, id: String) {
        val row = outboxDao.getForBook(kind, manifestUrl).find { it.itemId == id } ?: return
        val ok = try {
            if (row.opType == OP_UPSERT) {
                val item = row.itemJson?.let(::decodeOne) ?: return
                pushUpsert(manifestUrl, item)
            } else {
                pushDelete(manifestUrl, id)
            }
        } catch (e: Exception) {
            false
        }
        if (ok) outboxDao.remove(kind, manifestUrl, id)
    }

    private suspend fun readCache(manifestUrl: String): List<T> =
        cacheDao.get(kind, manifestUrl)?.itemsJson?.let(::decodeList) ?: emptyList()

    private suspend fun applyOutbox(manifestUrl: String, base: List<T>): List<T> {
        val outbox = outboxDao.getForBook(kind, manifestUrl)
        if (outbox.isEmpty()) return base
        val byId = base.associateByTo(linkedMapOf()) { idOf(it) }
        outbox.forEach { row ->
            if (row.opType == OP_DELETE) {
                byId.remove(row.itemId)
            } else {
                row.itemJson?.let(::decodeOne)?.let { byId[row.itemId] = it }
            }
        }
        return byId.values.toList()
    }

    private suspend fun writeCacheUpsert(manifestUrl: String, item: T) {
        val current = readCache(manifestUrl).associateByTo(linkedMapOf()) { idOf(it) }
        current[idOf(item)] = item
        cacheDao.set(AnnotationCacheEntity(kind, manifestUrl, encodeList(current.values.toList()), now()))
    }

    private suspend fun writeCacheDelete(manifestUrl: String, id: String) {
        val current = readCache(manifestUrl).filterNot { idOf(it) == id }
        cacheDao.set(AnnotationCacheEntity(kind, manifestUrl, encodeList(current), now()))
    }

    private fun encodeList(items: List<T>) = Json.encodeToString(listSerializer, items)
    private fun decodeList(json: String) = runCatching { Json.decodeFromString(listSerializer, json) }.getOrDefault(emptyList())
    private fun encodeOne(item: T) = Json.encodeToString(serializer, item)
    private fun decodeOne(json: String) = runCatching { Json.decodeFromString(serializer, json) }.getOrNull()

    private fun now() = System.currentTimeMillis()
}
