package com.ishireader.app.data.repository

import com.ishireader.app.data.model.Book
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(private val network: NetworkModule) {

    suspend fun fetchBooks(): ApiResult<List<Book>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.books()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.books)
            } else {
                ApiResult.Failure("Couldn't load library (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
