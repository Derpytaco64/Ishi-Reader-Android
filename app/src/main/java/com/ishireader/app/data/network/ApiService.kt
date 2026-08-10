package com.ishireader.app.data.network

import com.ishireader.app.data.model.AdminUserResponse
import com.ishireader.app.data.model.AdminUsersResponse
import com.ishireader.app.data.model.BookFolderField
import com.ishireader.app.data.model.BookmarkUpsertRequest
import com.ishireader.app.data.model.BookmarksResponse
import com.ishireader.app.data.model.BooksResponse
import com.ishireader.app.data.model.CompletedListenUpsertRequest
import com.ishireader.app.data.model.CompletedListensResponse
import com.ishireader.app.data.model.CompletedReadTimeUpsertRequest
import com.ishireader.app.data.model.CompletedReadTimesResponse
import com.ishireader.app.data.model.CreateUserRequest
import com.ishireader.app.data.model.DailyReadingHistoryRequest
import com.ishireader.app.data.model.DailyReadingHistoryResponse
import com.ishireader.app.data.model.HighlightUpsertRequest
import com.ishireader.app.data.model.HighlightsResponse
import com.ishireader.app.data.model.LibraryPrefsResponse
import com.ishireader.app.data.model.ListeningTimeRequest
import com.ishireader.app.data.model.ListeningTimeResponse
import com.ishireader.app.data.model.LoginAccentColorField
import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.LoginResponse
import com.ishireader.app.data.model.LoginThemeModeField
import com.ishireader.app.data.model.MeResponse
import com.ishireader.app.data.model.MigrateBookDataRequest
import com.ishireader.app.data.model.NoteUpsertRequest
import com.ishireader.app.data.model.NotesResponse
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.model.PositionResponse
import com.ishireader.app.data.model.PublicUsersResponse
import com.ishireader.app.data.model.ReadingSpeedSamplesRequest
import com.ishireader.app.data.model.ReadingSpeedSamplesResponse
import com.ishireader.app.data.model.ReadingTimeRequest
import com.ishireader.app.data.model.ReadingTimeResponse
import com.ishireader.app.data.model.ReadiumPortField
import com.ishireader.app.data.model.ReadiumPortRequest
import com.ishireader.app.data.model.ReadiumUrlField
import com.ishireader.app.data.model.ResetPasswordRequest
import com.ishireader.app.data.model.SetupPasswordRequest
import com.ishireader.app.data.model.UserDataFolderField
import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.model.WordCountRequest
import com.ishireader.app.data.model.WordCountResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    /** Streams the raw publication file (epub/pdf/cbz) so the Readium navigator can open it as a
     *  local asset -- @Streaming keeps Retrofit from buffering the whole body into memory first. */
    @Streaming
    @GET("api/books/download")
    suspend fun downloadBook(@Query("manifestUrl") manifestUrl: String): Response<ResponseBody>

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

    /** Upsert-by-id -- also used to save edits to an existing note (same route, no separate PUT). */
    @POST("api/userdata/notes")
    suspend fun upsertNote(@Body request: NoteUpsertRequest): Response<Unit>

    @DELETE("api/userdata/notes")
    suspend fun deleteNote(@Query("manifestUrl") manifestUrl: String, @Query("id") id: String): Response<Unit>

    @GET("api/userdata/highlights")
    suspend fun getHighlights(@Query("manifestUrl") manifestUrl: String): Response<HighlightsResponse>

    @POST("api/userdata/highlights")
    suspend fun upsertHighlight(@Body request: HighlightUpsertRequest): Response<Unit>

    @DELETE("api/userdata/highlights")
    suspend fun deleteHighlight(@Query("manifestUrl") manifestUrl: String, @Query("id") id: String): Response<Unit>

    @GET("api/userdata/bookmarks")
    suspend fun getBookmarks(@Query("manifestUrl") manifestUrl: String): Response<BookmarksResponse>

    @POST("api/userdata/bookmarks")
    suspend fun upsertBookmark(@Body request: BookmarkUpsertRequest): Response<Unit>

    @DELETE("api/userdata/bookmarks")
    suspend fun deleteBookmark(@Query("manifestUrl") manifestUrl: String, @Query("id") id: String): Response<Unit>

    @GET("api/userdata/completedReadTimes")
    suspend fun getCompletedReadTimes(@Query("manifestUrl") manifestUrl: String): Response<CompletedReadTimesResponse>

    @POST("api/userdata/completedReadTimes")
    suspend fun upsertCompletedReadTime(@Body request: CompletedReadTimeUpsertRequest): Response<Unit>

    @DELETE("api/userdata/completedReadTimes")
    suspend fun deleteCompletedReadTime(@Query("manifestUrl") manifestUrl: String, @Query("id") id: String): Response<Unit>

    /** Whole-file overwrite, not an append -- see ReadingTimeRequest doc. */
    @GET("api/userdata/readingTime")
    suspend fun getReadingTime(@Query("manifestUrl") manifestUrl: String): Response<ReadingTimeResponse>

    @POST("api/userdata/readingTime")
    suspend fun setReadingTime(@Body request: ReadingTimeRequest): Response<Unit>

    @GET("api/userdata/listeningTime")
    suspend fun getListeningTime(@Query("manifestUrl") manifestUrl: String): Response<ListeningTimeResponse>

    @POST("api/userdata/listeningTime")
    suspend fun setListeningTime(@Body request: ListeningTimeRequest): Response<Unit>

    @GET("api/userdata/completedListens")
    suspend fun getCompletedListens(@Query("manifestUrl") manifestUrl: String): Response<CompletedListensResponse>

    @POST("api/userdata/completedListens")
    suspend fun upsertCompletedListen(@Body request: CompletedListenUpsertRequest): Response<Unit>

    @DELETE("api/userdata/completedListens")
    suspend fun deleteCompletedListen(@Query("manifestUrl") manifestUrl: String, @Query("id") id: String): Response<Unit>

    @GET("api/userdata/wordCount")
    suspend fun getWordCount(@Query("manifestUrl") manifestUrl: String): Response<WordCountResponse>

    @POST("api/userdata/wordCount")
    suspend fun setWordCount(@Body request: WordCountRequest): Response<Unit>

    /** Global per-user buffer, no manifestUrl -- carries the live WPM estimate across book switches. */
    @GET("api/userdata/readingSpeedSamples")
    suspend fun getReadingSpeedSamples(): Response<ReadingSpeedSamplesResponse>

    @POST("api/userdata/readingSpeedSamples")
    suspend fun setReadingSpeedSamples(@Body request: ReadingSpeedSamplesRequest): Response<Unit>

    @GET("api/userdata/dailyReadingHistory")
    suspend fun getDailyReadingHistory(@Query("manifestUrl") manifestUrl: String): Response<DailyReadingHistoryResponse>

    @POST("api/userdata/dailyReadingHistory")
    suspend fun setDailyReadingHistory(@Body request: DailyReadingHistoryRequest): Response<Unit>

    @GET("api/userdata/stats")
    suspend fun stats(): Response<UserStats>

    @POST("api/userdata/migrateBookData")
    suspend fun migrateBookData(@Body request: MigrateBookDataRequest): Response<Unit>

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
