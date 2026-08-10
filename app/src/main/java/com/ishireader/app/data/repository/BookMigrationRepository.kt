package com.ishireader.app.data.repository

import com.ishireader.app.data.model.MigrateBookDataRequest
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carries a book's progress/annotations/reading-time onto a different library entry -- see the
 *  server's api/userdata/migrateBookData route. Used from the Migrate Book Data dialog when a
 *  metadata edit rewrites a book's file (changing its content hash, see resolveBookIdentity
 *  server-side) and orphans the old entry's data under a hash nothing resolves to anymore. */
class BookMigrationRepository(private val network: NetworkModule) {

    suspend fun migrateBookData(sourceManifestUrl: String, destManifestUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.migrateBookData(MigrateBookDataRequest(sourceManifestUrl, destManifestUrl))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't migrate book data (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
