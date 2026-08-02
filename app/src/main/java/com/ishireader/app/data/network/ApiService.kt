package com.ishireader.app.data.network

import com.ishireader.app.data.model.BooksResponse
import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.LoginResponse
import com.ishireader.app.data.model.MeResponse
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.model.PositionResponse
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
}
