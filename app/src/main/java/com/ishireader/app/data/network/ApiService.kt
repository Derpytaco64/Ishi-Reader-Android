package com.ishireader.app.data.network

import com.ishireader.app.data.model.BooksResponse
import com.ishireader.app.data.model.LibraryPrefsResponse
import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.LoginResponse
import com.ishireader.app.data.model.MeResponse
import com.ishireader.app.data.model.NotesResponse
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.model.PositionResponse
import com.ishireader.app.data.model.UserStats
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Mirrors the endpoints under Ishi-Read's src/app/api/ that the mobile client needs for v1. */
interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    @GET("api/books")
    suspend fun books(): Response<BooksResponse>

    @GET("api/userdata/position")
    suspend fun getPosition(@Query("manifestUrl") manifestUrl: String): Response<PositionResponse>

    @POST("api/userdata/position")
    suspend fun setPosition(@Body request: PositionRequest): Response<Unit>

    @GET("api/userdata/library-prefs")
    suspend fun getLibraryPrefs(): Response<LibraryPrefsResponse>

    /** Server does a shallow top-level merge, not an overwrite -- only send the keys you mean to patch. */
    @POST("api/userdata/library-prefs")
    suspend fun patchLibraryPrefs(@Body patch: JsonObject): Response<Unit>

    @GET("api/userdata/notes")
    suspend fun getNotes(@Query("manifestUrl") manifestUrl: String): Response<NotesResponse>

    @GET("api/userdata/stats")
    suspend fun stats(): Response<UserStats>
}
