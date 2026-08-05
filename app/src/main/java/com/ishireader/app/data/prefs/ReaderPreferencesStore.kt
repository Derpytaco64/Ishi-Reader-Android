package com.ishireader.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ishireader.app.data.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.readerPreferencesDataStore by preferencesDataStore(name = "ishi_reader_reader_prefs")

/**
 * Local-only, on-device store for in-book reading preferences (font/spacing/theme/layout) -- see
 * ReaderSettings for why these are separate from AppSettings/LibraryPrefsRepository. Unlike most
 * of this app's settings, these deliberately don't sync to the server: the website itself only
 * syncs theme+fontFamily across devices via /api/userdata/settings and keeps everything else
 * (size, spacing, layout) device-local, reasoning that phone vs. desktop reading preferences are
 * legitimately different. Even that narrow theme/fontFamily sync isn't wired up here yet -- it's a
 * small follow-up better suited to when the rest of offline sync is being finished, not something
 * that has to land before reader settings work at all.
 */
class ReaderPreferencesStore(private val context: Context) {

    private object Keys {
        val SETTINGS_JSON = stringPreferencesKey("reader_settings_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<ReaderSettings> = context.readerPreferencesDataStore.data.map { prefs ->
        prefs[Keys.SETTINGS_JSON]
            ?.let { runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull() }
            ?: ReaderSettings()
    }

    suspend fun save(settings: ReaderSettings) {
        context.readerPreferencesDataStore.edit { it[Keys.SETTINGS_JSON] = json.encodeToString(settings) }
    }
}
