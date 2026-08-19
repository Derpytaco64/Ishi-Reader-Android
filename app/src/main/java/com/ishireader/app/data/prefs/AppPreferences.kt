package com.ishireader.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ishi_reader_prefs")

/**
 * Persists the config the app can't derive on its own: which Ishi-Read server to talk to, and
 * whether the last connection attempt to it actually succeeded. Session state itself lives in
 * [com.ishireader.app.data.network.PersistentCookieJar] -- [wasLoggedIn] is a separate, coarser
 * signal used only to decide whether a *server-unreachable* cold start should still let the user
 * into their downloaded library (see LoginViewModel.connect) rather than block on connectivity.
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val WAS_LOGGED_IN = booleanPreferencesKey("was_logged_in")
        val SHOW_DOWNLOADED_ONLY = booleanPreferencesKey("show_downloaded_only")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url.trimEnd('/') }
    }

    val wasLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.WAS_LOGGED_IN] ?: false }

    suspend fun setWasLoggedIn(value: Boolean) {
        context.dataStore.edit { it[Keys.WAS_LOGGED_IN] = value }
    }

    /** Whether the hamburger's "Only show downloaded books" toggle is on -- deliberately not part
     *  of [com.ishireader.app.data.repository.LibraryPrefsRepository]'s server-synced AppSettings:
     *  which books are downloaded differs per device, so this has to stay local-only. */
    val showDownloadedOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DOWNLOADED_ONLY] ?: false }

    suspend fun setShowDownloadedOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DOWNLOADED_ONLY] = value }
    }
}
