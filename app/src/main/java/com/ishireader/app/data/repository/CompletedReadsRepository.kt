package com.ishireader.app.data.repository

import com.ishireader.app.data.model.CompletedReadTimeUpsertRequest
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompletedReadsRepository(private val network: NetworkModule) {

    suspend fun getCompletedReadTimes(manifestUrl: String): ApiResult<List<StoredCompletedReadTime>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getCompletedReadTimes(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.items)
            } else {
                ApiResult.Failure("Couldn't load completed reads (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Archives a finished reading run -- the server upserts by id, so this also covers editing
     *  one if a caller ever needs to (not currently exposed in the UI, mirrors the website). */
    suspend fun saveCompletedReadTime(manifestUrl: String, item: StoredCompletedReadTime): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.upsertCompletedReadTime(CompletedReadTimeUpsertRequest(manifestUrl, item))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save completed read (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteCompletedReadTime(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.deleteCompletedReadTime(manifestUrl, id)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't delete completed read (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
