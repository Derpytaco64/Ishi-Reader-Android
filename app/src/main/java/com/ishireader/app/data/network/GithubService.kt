package com.ishireader.app.data.network

import com.ishireader.app.data.model.GithubRelease
import retrofit2.Response
import retrofit2.http.GET

interface GithubService {
    @GET("repos/Derpytaco64/Ishi-Reader-Android/releases/latest")
    suspend fun latestRelease(): Response<GithubRelease>
}
