package com.ishireader.app.data.network

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Ishi-Read authenticates with an httpOnly session cookie (see /api/auth/login), not a
 * bearer token, so the app needs a real cookie jar rather than an Authorization header.
 * Persisted to SharedPreferences (as JSON, one entry per host) so the session survives
 * process death -- the server sets a 30-day maxAge on it.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("ishi_reader_cookies", Context.MODE_PRIVATE)
    private val cache = mutableMapOf<String, MutableList<Cookie>>()

    init {
        prefs.all.forEach { (host, raw) ->
            if (raw is String) {
                val restored = runCatching { json.decodeFromString<List<StoredCookie>>(raw) }
                    .getOrDefault(emptyList())
                    .map { it.toCookie() }
                if (restored.isNotEmpty()) cache[host] = restored.toMutableList()
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val existing = cache.getOrPut(host) { mutableListOf() }

        for (cookie in cookies) {
            existing.removeAll { it.name == cookie.name }
            existing.add(cookie)
        }

        persist(host, existing)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cache[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val (valid, expired) = cookies.partition { it.expiresAt > now }
        if (expired.isNotEmpty()) {
            cache[host] = valid.toMutableList()
            persist(host, valid)
        }
        return valid
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.edit().clear().apply()
    }

    private fun persist(host: String, cookies: List<Cookie>) {
        prefs.edit()
            .putString(host, json.encodeToString(cookies.map { it.toStoredCookie() }))
            .apply()
    }

    private fun Cookie.toStoredCookie() = StoredCookie(
        name = name,
        value = value,
        expiresAt = expiresAt,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly
    )

    private fun StoredCookie.toCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }
}
