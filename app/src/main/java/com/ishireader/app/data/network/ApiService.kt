package com.ishireader.app.data.network

import com.ishireader.app.data.model.AdminUserResponse
import com.ishireader.app.data.model.AdminUsersResponse
import com.ishireader.app.data.model.BookFolderField
import com.ishireader.app.data.model.BooksResponse
import com.ishireader.app.data.model.CreateUserRequest
import com.ishireader.app.data.model.LibraryPrefsResponse
import com.ishireader.app.data.model.LoginAccentColorField
import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.LoginResponse
import com.ishireader.app.data.model.LoginThemeModeField
import com.ishireader.app.data.model.MeResponse
import com.ishireader.app.data.model.NotesResponse
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.model.PositionResponse
import com.ishireader.app.data.model.PublicUsersResponse
import com.ishireader.app.data.model.ReadiumPortField
import com.ishireader.app.data.model.ReadiumPortRequest
import com.ishireader.app.data.model.ReadiumUrlField
import com.ishireader.app.data.model.ResetPasswordRequest
import com.ishireader.app.data.model.SetupPasswordRequest
import com.ishireader.app.data.model.UserDataFolderField
import com.ishireader.app.data.model.UserStats
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors the endpoints under Ishi-Read's src/app/api/ that the mobile client needs for v1. */
interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    /** Public and unauthenticated -- the login picker needs this before any session exists. */
    @GET("api/auth/users")
    suspend fun publicUsers(): Response<PublicUsersResponse>

    @POST("api/auth/setup-password")
    suspend fun setupPassword(@Body request: SetupPasswordRequest): Response<LoginResponse>

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

    @GET("api/admin/users")
    suspend fun adminListUsers(): Response<AdminUsersResponse>

    @POST("api/admin/users")
    suspend fun adminCreateUser(@Body request: CreateUserRequest): Response<AdminUserResponse>

    /** Server does a shallow patch here too (username/name/isAdmin/disabled), same convention as
     *  patchLibraryPrefs -- only send the keys you mean to change. */
    @PATCH("api/admin/users/{id}")
    suspend fun adminUpdateUser(@Path("id") id: String, @Body patch: JsonObject): Response<AdminUserResponse>

    @DELETE("api/admin/users/{id}")
    suspend fun adminDeleteUser(@Path("id") id: String): Response<Unit>

    @POST("api/admin/users/{id}/reset-password")
    suspend fun adminResetPassword(@Path("id") id: String, @Body request: ResetPasswordRequest): Response<Unit>

    @POST("api/admin/users/{id}/unlock")
    suspend fun adminUnlockUser(@Path("id") id: String): Response<Unit>

    @GET("api/settings/book-folder")
    suspend fun getBookFolder(): Response<BookFolderField>

    @POST("api/settings/book-folder")
    suspend fun setBookFolder(@Body body: BookFolderField): Response<BookFolderField>

    @GET("api/settings/readium-url")
    suspend fun getReadiumUrl(): Response<ReadiumUrlField>

    @POST("api/settings/readium-url")
    suspend fun setReadiumUrl(@Body body: ReadiumUrlField): Response<ReadiumUrlField>

    @GET("api/settings/readium-port")
    suspend fun getReadiumPort(): Response<ReadiumPortField>

    @POST("api/settings/readium-port")
    suspend fun setReadiumPort(@Body body: ReadiumPortRequest): Response<ReadiumPortField>

    @GET("api/settings/user-data-folder")
    suspend fun getUserDataFolder(): Response<UserDataFolderField>

    @POST("api/settings/user-data-folder")
    suspend fun setUserDataFolder(@Body body: UserDataFolderField): Response<UserDataFolderField>

    @GET("api/settings/login-accent-color")
    suspend fun getLoginAccentColor(): Response<LoginAccentColorField>

    @POST("api/settings/login-accent-color")
    suspend fun setLoginAccentColor(@Body body: LoginAccentColorField): Response<LoginAccentColorField>

    @GET("api/settings/login-theme-mode")
    suspend fun getLoginThemeMode(): Response<LoginThemeModeField>

    @POST("api/settings/login-theme-mode")
    suspend fun setLoginThemeMode(@Body body: LoginThemeModeField): Response<LoginThemeModeField>
}
