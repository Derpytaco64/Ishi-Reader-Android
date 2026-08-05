package com.ishireader.app.data.repository

import com.ishireader.app.data.local.CachedLibraryPrefsDao
import com.ishireader.app.data.local.CachedLibraryPrefsEntity
import com.ishireader.app.data.local.PendingLibraryPrefsPatchDao
import com.ishireader.app.data.local.PendingLibraryPrefsPatchEntity
import com.ishireader.app.data.model.AppSettings
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.CustomShelf
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KEY_CONTINUE_READING_DISMISSED = "continueReadingDismissed"
private const val KEY_CUSTOM_SHELVES = "customShelves"
private const val KEY_THEME = "theme"
private const val KEY_ACCENT_COLOR = "accentColor"
private const val KEY_COVER_SIZE = "coverSize"
private const val KEY_SHELF_ORDER = "shelfOrder"
private const val KEY_SHELF_VISIBILITY = "shelfVisibility"

@Serializable
private data class SettingsFields(
    val theme: String? = null,
    val accentColor: String? = null,
    val coverSize: String? = null,
    val shelfOrder: List<String>? = null,
    val shelfVisibility: Map<String, Boolean>? = null
)

/**
 * Handles the library-prefs fields the Home screen's Continue Reading shelf, the Shelves tab, and
 * the settings drawer (theme, accentColor, coverSize, shelfOrder/shelfVisibility) need -- all
 * mirroring useCustomShelves.ts's convention of treating this as one freeform per-user blob the
 * server shallow-merges on every PATCH.
 *
 * The whole blob is cached in Room (see [fetchPrefsBlob]) so a server-unreachable launch still
 * shows the real Continue Reading dismissals/shelves/settings instead of silently resetting to
 * empty/default -- that used to happen because every getter here independently hit the network
 * and swallowed failures into an empty result.
 *
 * Writes (see [patchPrefs]) are local-first, same spirit as PositionRepository: every patch is
 * applied to the cache immediately and merged into a pending outbox row *before* attempting the
 * network call, so an offline accent-color or shelf-order change is never lost -- it's saved on
 * device and pushed by [com.ishireader.app.data.sync.LibraryPrefsSyncWorker] once connectivity
 * returns. A live push still happens inline when possible so the common case doesn't wait on
 * WorkManager's scheduling latency.
 */
class LibraryPrefsRepository(
    private val network: NetworkModule,
    private val cachedLibraryPrefsDao: CachedLibraryPrefsDao,
    private val pendingPatchDao: PendingLibraryPrefsPatchDao,
    private val syncScheduler: SyncScheduler
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getContinueReadingDismissed(): Map<String, Double> = withContext(Dispatchers.IO) {
        val dismissed = fetchPrefsBlob()[KEY_CONTINUE_READING_DISMISSED] ?: return@withContext emptyMap()
        dismissed.jsonObject.mapValues { (_, value) -> value.jsonPrimitive.doubleOrNull ?: 0.0 }
    }

    suspend fun setContinueReadingDismissed(dismissed: Map<String, Double>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val patch = JsonObject(
            mapOf(KEY_CONTINUE_READING_DISMISSED to JsonObject(dismissed.mapValues { JsonPrimitive(it.value) }))
        )
        patchPrefs(patch)
    }

    /** customShelves is a flat JSON array under library-prefs -- array order is display order,
     *  there's no separate ordering field (mirrors useCustomShelves.ts). */
    suspend fun getCustomShelves(): List<CustomShelf> = withContext(Dispatchers.IO) {
        val shelves = fetchPrefsBlob()[KEY_CUSTOM_SHELVES] ?: return@withContext emptyList()
        runCatching { json.decodeFromJsonElement(ListSerializer(CustomShelf.serializer()), shelves) }
            .getOrDefault(emptyList())
    }

    /** Always writes the whole list back -- matches the site's own pattern of mutating a local
     *  copy of the array and PATCHing it in full rather than diffing individual shelves. */
    suspend fun setCustomShelves(shelves: List<CustomShelf>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val element = json.encodeToJsonElement(ListSerializer(CustomShelf.serializer()), shelves)
        patchPrefs(JsonObject(mapOf(KEY_CUSTOM_SHELVES to element)))
    }

    /** Decodes straight from the top-level prefs object (ignoreUnknownKeys skips customShelves/
     *  continueReadingDismissed/anything else living in the same blob) rather than pulling each
     *  key out individually -- there's no nested shape to unwrap here, unlike customShelves. */
    suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        val fields = runCatching { json.decodeFromJsonElement<SettingsFields>(fetchPrefsBlob()) }
            .getOrDefault(SettingsFields())
        AppSettings(
            theme = ThemeMode.fromKey(fields.theme),
            accentColor = fields.accentColor,
            coverSize = CoverSize.fromKey(fields.coverSize),
            shelfOrder = fields.shelfOrder?.mapNotNull { HomeShelfId.fromKey(it) }
                ?.let { ordered -> ordered + (HomeShelfId.Default - ordered.toSet()) }
                ?: HomeShelfId.Default,
            shelfVisibility = fields.shelfVisibility
                ?.mapNotNull { (key, visible) -> HomeShelfId.fromKey(key)?.let { it to visible } }
                ?.toMap()
                ?: emptyMap()
        )
    }

    suspend fun patchSettings(settings: AppSettings): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val patch = JsonObject(
            mapOf(
                KEY_THEME to JsonPrimitive(settings.theme.key),
                KEY_ACCENT_COLOR to JsonPrimitive(settings.accentColor),
                KEY_COVER_SIZE to JsonPrimitive(settings.coverSize.key),
                KEY_SHELF_ORDER to json.encodeToJsonElement(ListSerializer(String.serializer()), settings.shelfOrder.map { it.key }),
                KEY_SHELF_VISIBILITY to JsonObject(settings.shelfVisibility.mapKeys { it.key.key }.mapValues { JsonPrimitive(it.value) })
            )
        )
        patchPrefs(patch)
    }

    /** Fetches the whole blob, caching it on success; falls back to the cached copy (or an empty
     *  object, for a device that's never fetched successfully at all) on any failure. A pending
     *  offline patch is always re-applied on top of whatever comes back, in case this GET reaches
     *  the server before the outbox has had a chance to flush its own writes -- otherwise a read
     *  right after an offline edit could briefly show the pre-edit server value again. */
    private suspend fun fetchPrefsBlob(): JsonObject {
        val fetched = try {
            val response = network.api.getLibraryPrefs()
            val prefs = response.body()?.libraryPrefs?.jsonObject
            if (response.isSuccessful && prefs != null) {
                cachedLibraryPrefsDao.set(CachedLibraryPrefsEntity(prefsJson = prefs.toString()))
                prefs
            } else {
                readCachedPrefsBlob()
            }
        } catch (e: Exception) {
            readCachedPrefsBlob()
        }
        return JsonObject(fetched + readPendingPatch())
    }

    private suspend fun readCachedPrefsBlob(): JsonObject =
        cachedLibraryPrefsDao.get()
            ?.let { runCatching { Json.parseToJsonElement(it.prefsJson).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())

    private suspend fun readPendingPatch(): JsonObject =
        pendingPatchDao.get()
            ?.let { runCatching { Json.parseToJsonElement(it.patchJson).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())

    /** Local-first: merges [patch] into the cache and the pending outbox *before* touching the
     *  network, so the write is durable even if the app is killed mid-request. Then makes one
     *  best-effort attempt to push the full accumulated pending patch (not just this call's own
     *  delta) right away -- that also flushes anything left over from an earlier offline edit
     *  instead of leaving it stuck behind a newer one. Any failure (network or server) leaves the
     *  outbox row in place and hands off to [SyncScheduler.scheduleLibraryPrefsSync] for retry;
     *  from the caller's perspective that's still a success, since the edit is safely saved. */
    private suspend fun patchPrefs(patch: JsonObject): ApiResult<Unit> {
        cachedLibraryPrefsDao.set(CachedLibraryPrefsEntity(prefsJson = JsonObject(readCachedPrefsBlob() + patch).toString()))
        val pending = JsonObject(readPendingPatch() + patch)
        pendingPatchDao.set(PendingLibraryPrefsPatchEntity(patchJson = pending.toString()))

        return try {
            val response = network.api.patchLibraryPrefs(pending)
            if (response.isSuccessful) {
                pendingPatchDao.clear()
                ApiResult.Success(Unit)
            } else {
                syncScheduler.scheduleLibraryPrefsSync()
                ApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            syncScheduler.scheduleLibraryPrefsSync()
            ApiResult.Success(Unit)
        }
    }
}
