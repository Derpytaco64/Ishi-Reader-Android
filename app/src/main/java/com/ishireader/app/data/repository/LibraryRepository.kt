package com.ishireader.app.data.repository

import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(private val network: NetworkModule) {

    /** Last successful fetch, kept around so the detail screen can look a book up by URL
     *  without a dedicated single-book endpoint or hoisting LibraryViewModel out of its
     *  natural per-destination scope. */
    @Volatile
    private var cachedBooks: List<Book> = emptyList()

    suspend fun fetchBooks(): ApiResult<List<Book>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.books()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                cachedBooks = body.books
                ApiResult.Success(body.books)
            } else {
                ApiResult.Failure("Couldn't load library (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    fun findCached(manifestUrl: String): Book? = cachedBooks.find { it.manifestUrl() == manifestUrl }
}
