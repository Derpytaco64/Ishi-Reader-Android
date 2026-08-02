package com.ishireader.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ishi_reader_prefs")

/**
 * Persists the one piece of config the app can't derive on its own: which Ishi-Read
 * server to talk to. Session state itself lives in [com.ishireader.app.data.network.PersistentCookieJar].
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url.trimEnd('/') }
    }
}
