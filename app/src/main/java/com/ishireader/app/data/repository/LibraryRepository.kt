package com.ishireader.app.data.repository

import com.ishireader.app.data.local.CachedBookDao
import com.ishireader.app.data.local.CachedBookEntity
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Falls back to the last successful fetch (persisted in Room) when the network call fails, so a
 * server that's merely unreachable doesn't erase the library on screen -- only a genuinely empty
 * cache (nothing ever fetched, e.g. this device's very first launch offline) surfaces as a real
 * failure. Callers keep working against the same ApiResult<List<Book>> contract either way.
 */
class LibraryRepository(
    private val network: NetworkModule,
    private val cachedBookDao: CachedBookDao
) {

    /** Last successful fetch (or cache fallback), kept around so the detail screen can look a
     *  book up by URL without a dedicated single-book endpoint or hoisting LibraryViewModel out
     *  of its natural per-destination scope. */
    @Volatile
    private var cachedBooks: List<Book> = emptyList()

    suspend fun fetchBooks(): ApiResult<List<Book>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.books()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                cachedBooks = body.books
                persistToLocalCache(body.books)
                ApiResult.Success(body.books)
            } else {
                fallBackToLocalCache("Couldn't load library (${response.code()})")
            }
        } catch (e: Exception) {
            fallBackToLocalCache(e.message ?: "Network error")
        }
    }

    fun findCached(manifestUrl: String): Book? = cachedBooks.find { it.manifestUrl() == manifestUrl }

    private suspend fun fallBackToLocalCache(errorMessage: String): ApiResult<List<Book>> {
        val cached = readLocalCache()
        if (cached.isEmpty()) return ApiResult.Failure(errorMessage)

        cachedBooks = cached
        return ApiResult.Success(cached)
    }

    private suspend fun readLocalCache(): List<Book> =
        cachedBookDao.getAll().mapNotNull { runCatching { Json.decodeFromString<Book>(it.bookJson) }.getOrNull() }

    private suspend fun persistToLocalCache(books: List<Book>) {
        cachedBookDao.replaceAll(books.map { CachedBookEntity(it.url, Json.encodeToString(it)) })
    }
}
