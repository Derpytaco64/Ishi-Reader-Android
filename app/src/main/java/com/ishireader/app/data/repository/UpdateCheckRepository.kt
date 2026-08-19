package com.ishireader.app.data.repository

import com.ishireader.app.data.model.GithubRelease
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.GithubService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Checks GitHub's releases API for a newer tagged version than the one installed -- this app is
 * sideloaded (no Play Store auto-update), so this is the only update signal a user gets short of
 * manually checking the repo. Talks to a fixed api.github.com host, so it needs its own Retrofit
 * client rather than NetworkModule's, which is bound to the user's self-hosted Ishi-Read server.
 */
class UpdateCheckRepository {

    private val service: GithubService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubService::class.java)
    }

    suspend fun latestRelease(): ApiResult<GithubRelease> = withContext(Dispatchers.IO) {
        try {
            val response = service.latestRelease()
            val release = response.body()
            if (response.isSuccessful && release != null) {
                ApiResult.Success(release)
            } else {
                ApiResult.Failure("Couldn't check for updates")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }
}

/** Compares GitHub tag names like "v1.3.0" against a versionName like "1.2.8" -- numeric,
 *  dot-separated component comparison so a missing/extra trailing component (e.g. "1.3" vs
 *  "1.3.0") doesn't misfire as newer/older. */
fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    val latestParts = latestTag.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = currentVersion.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val latest = latestParts.getOrElse(i) { 0 }
        val current = currentParts.getOrElse(i) { 0 }
        if (latest != current) return latest > current
    }
    return false
}
