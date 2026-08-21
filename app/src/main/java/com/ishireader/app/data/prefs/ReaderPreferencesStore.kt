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
 *
 * Kept as two entirely separate persisted [ReaderSettings] objects, one for EPUB/text books and one
 * for comics, rather than a single shared one -- comic-only concerns (theme restricted to
 * Light/Dark, reading direction) used to live on the same object as text settings, which meant
 * picking e.g. Dark or Right-to-Left while reading a manga silently changed the very next EPUB
 * opened too. [ReaderSettings.comicReadingDirection] living on the EPUB-scoped object is harmless
 * (it stays at its AUTO default forever, since the comic-only settings UI never writes to that
 * instance), so no gating is needed in ReaderSettings.toEpubPreferences itself.
 */
class ReaderPreferencesStore(private val context: Context) {

    private object Keys {
        val EPUB_SETTINGS_JSON = stringPreferencesKey("reader_settings_json")
        val COMIC_SETTINGS_JSON = stringPreferencesKey("reader_settings_json_comic")
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun settings(isComic: Boolean): Flow<ReaderSettings> {
        val key = if (isComic) Keys.COMIC_SETTINGS_JSON else Keys.EPUB_SETTINGS_JSON
        return context.readerPreferencesDataStore.data.map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull() } ?: ReaderSettings()
        }
    }

    suspend fun save(settings: ReaderSettings, isComic: Boolean) {
        val key = if (isComic) Keys.COMIC_SETTINGS_JSON else Keys.EPUB_SETTINGS_JSON
        context.readerPreferencesDataStore.edit { it[key] = json.encodeToString(settings) }
    }
}
