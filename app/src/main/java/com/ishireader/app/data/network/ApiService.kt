package com.ishireader.app.data.network

import com.ishireader.app.data.model.AdminUserResponse
import com.ishireader.app.data.model.AdminUsersResponse
import com.ishireader.app.data.model.AniListAuthorizeUrlResponse
import com.ishireader.app.data.model.AniListDisconnectResponse
import com.ishireader.app.data.model.AniListExchangeRequest
import com.ishireader.app.data.model.AniListExchangeResponse
import com.ishireader.app.data.model.AniListMediaEntryResponse
import com.ishireader.app.data.model.AniListSaveEntryResponse
import com.ishireader.app.data.model.AniListSearchResponse
import com.ishireader.app.data.model.AniListSettingsRequest
import com.ishireader.app.data.model.AniListSettingsResponse
import com.ishireader.app.data.model.AvatarUploadRequest
import com.ishireader.app.data.model.AvatarUploadResponse
import com.ishireader.app.data.model.BookFolderField
import com.ishireader.app.data.model.BookmarkUpsertRequest
import com.ishireader.app.data.model.BookmarksResponse
import com.ishireader.app.data.model.BooksResponse
import com.ishireader.app.data.model.ChangePasswordRequest
import com.ishireader.app.data.model.ChangePasswordResponse
import com.ishireader.app.data.model.ClearedManifestCacheResponse
import com.ishireader.app.data.model.ClearedSpeedSamplesResponse
import com.ishireader.app.data.model.CompletedListenUpsertRequest
import com.ishireader.app.data.model.CompletedListensResponse
import com.ishireader.app.data.model.CompletedReadTimeUpsertRequest
import com.ishireader.app.data.model.CompletedReadTimesResponse
import com.ishireader.app.data.model.CreateUserRequest
import com.ishireader.app.data.model.DailyListeningHistoryRequest
import com.ishireader.app.data.model.DailyListeningHistoryResponse
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
import com.ishireader.app.data.model.MigrateOrphanedDataRequest
import com.ishireader.app.data.model.NoteUpsertRequest
import com.ishireader.app.data.model.NotesResponse
import com.ishireader.app.data.model.OrphanedDataReport
import com.ishireader.app.data.model.PageCountResponse
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.model.PositionResponse
import com.ishireader.app.data.model.PublicUsersResponse
import com.ishireader.app.data.model.ReadingProgressionResponse
import com.ishireader.app.data.model.ReadingSpeedSamplesRequest
import com.ishireader.app.data.model.ReadingSpeedSamplesResponse
import com.ishireader.app.data.model.ReadingTimeRequest
import com.ishireader.app.data.model.ReadingTimeResponse
import com.ishireader.app.data.model.ReadiumPortField
import com.ishireader.app.data.model.ReadiumPortRequest
import com.ishireader.app.data.model.ReadiumUrlField
import com.ishireader.app.data.model.ResetPasswordRequest
import com.ishireader.app.data.model.SetupPasswordRequest
import com.ishireader.app.data.model.UpdateProfileRequest
import com.ishireader.app.data.model.UpdateProfileResponse
import com.ishireader.app.data.model.UserDataFolderField
import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.model.WeeklyBookTypeStats
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

    @POST("api/auth/avatar")
    suspend fun uploadAvatar(@Body request: AvatarUploadRequest): Response<AvatarUploadResponse>

    /** Self-service display-name change only -- username changes stay admin-only. */
    @POST("api/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UpdateProfileResponse>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ChangePasswordResponse>

    @GET("api/books")
    suspend fun books(): Response<BooksResponse>

    /** Clears the server's in-memory manifest cache (title/author/cover resolution) so the next
     *  GET re-resolves every book from scratch -- the mobile counterpart to the website's
     *  "Refresh Manifest Cache" user-menu action. */
    @DELETE("api/books")
    suspend fun clearManifestCache(): Response<ClearedManifestCacheResponse>

    /** Streams the raw publication file (epub/pdf/cbz) so the Readium navigator can open it as a
     *  local asset -- @Streaming keeps Retrofit from buffering the whole body into memory first. */
    @Streaming
    @GET("api/books/download")
    suspend fun downloadBook(@Query("manifestUrl") manifestUrl: String): Response<ResponseBody>

    /** CBZ-only chapter/RTL metadata the bundled Go manifest server can't provide (it never parses
     *  ComicInfo.xml) -- see comicInfo.ts. Empty/null fields for a non-comic book. */
    @GET("api/books/reading-progression")
    suspend fun getReadingProgression(@Query("manifestUrl") manifestUrl: String): Response<ReadingProgressionResponse>

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

    @GET("api/userdata/dailyListeningHistory")
    suspend fun getDailyListeningHistory(@Query("manifestUrl") manifestUrl: String): Response<DailyListeningHistoryResponse>

    @POST("api/userdata/dailyListeningHistory")
    suspend fun setDailyListeningHistory(@Body request: DailyListeningHistoryRequest): Response<Unit>

    @GET("api/userdata/wordCount")
    suspend fun getWordCount(@Query("manifestUrl") manifestUrl: String): Response<WordCountResponse>

    @POST("api/userdata/wordCount")
    suspend fun setWordCount(@Body request: WordCountRequest): Response<Unit>

    /** GET-only, computes-and-caches on the server on a cache miss -- see PageCountResponse. */
    @GET("api/userdata/pageCount")
    suspend fun getPageCount(@Query("manifestUrl") manifestUrl: String): Response<PageCountResponse>

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

    /** offset 0 is the current week; each step below 0 pages back another 7 days -- see the server
     *  route's own comment for why it's clamped server-side to never go past the current week. */
    @GET("api/userdata/stats/weekly")
    suspend fun weeklyStats(@Query("offset") offset: Int = 0): Response<WeeklyBookTypeStats>

    @POST("api/userdata/migrateBookData")
    suspend fun migrateBookData(@Body request: MigrateBookDataRequest): Response<Unit>

    /** null url means the instance hasn't configured an AniList client_id/secret yet (Admin
     *  Settings). Not admin-gated -- any signed-in user needs this to connect their own account. */
    @GET("api/auth/anilist/authorize-url")
    suspend fun getAniListAuthorizeUrl(): Response<AniListAuthorizeUrlResponse>

    /** PIN-flow completion: exchanges the code the user copy-pasted from AniList's own /oauth/pin
     *  page for an access token, stored server-side against this session's user. */
    @POST("api/auth/anilist/exchange")
    suspend fun exchangeAniListCode(@Body request: AniListExchangeRequest): Response<AniListExchangeResponse>

    @POST("api/auth/anilist/disconnect")
    suspend fun disconnectAniList(): Response<AniListDisconnectResponse>

    /** Manga/one-shot only -- see the server route's format_in filter. */
    @GET("api/anilist/search")
    suspend fun searchAniList(@Query("query") query: String): Response<AniListSearchResponse>

    @GET("api/anilist/list-entry")
    suspend fun getAniListEntry(@Query("mediaId") mediaId: Int): Response<AniListMediaEntryResponse>

    /** Server does a presence-based patch -- only the keys actually present in the JSON body are
     *  touched (an omitted key means "don't change this field", an explicit null clears it), same
     *  convention as [patchLibraryPrefs]/[adminUpdateUser]. Build the JsonObject with only the
     *  fields that actually changed. */
    @POST("api/anilist/list-entry")
    suspend fun saveAniListEntry(@Body patch: JsonObject): Response<AniListSaveEntryResponse>

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

    /** Admin-scoped counterpart to [uploadAvatar] -- same request/response shape, targets `id`
     *  instead of the caller's own session. See api/admin/users/[id]/avatar/route.ts. */
    @POST("api/admin/users/{id}/avatar")
    suspend fun adminUploadAvatar(@Path("id") id: String, @Body request: AvatarUploadRequest): Response<AvatarUploadResponse>

    @GET("api/admin/orphaned-data")
    suspend fun adminScanOrphanedData(): Response<OrphanedDataReport>

    @DELETE("api/admin/orphaned-data")
    suspend fun adminDeleteOrphanedData(): Response<OrphanedDataReport>

    @POST("api/admin/orphaned-data/migrate")
    suspend fun adminMigrateOrphanedData(@Body request: MigrateOrphanedDataRequest): Response<Unit>

    @DELETE("api/admin/reading-speed-samples")
    suspend fun adminClearReadingSpeedSamples(): Response<ClearedSpeedSamplesResponse>

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

    /** Admin-only instance-wide AniList app registration -- see AniListSettingsResponse's own
     *  comment. Distinct from the per-user auth/anilist routes above. */
    @GET("api/settings/anilist")
    suspend fun getAniListSettings(): Response<AniListSettingsResponse>

    @POST("api/settings/anilist")
    suspend fun setAniListSettings(@Body body: AniListSettingsRequest): Response<AniListSettingsResponse>

    @GET("api/settings/login-accent-color")
    suspend fun getLoginAccentColor(): Response<LoginAccentColorField>

    @POST("api/settings/login-accent-color")
    suspend fun setLoginAccentColor(@Body body: LoginAccentColorField): Response<LoginAccentColorField>

    @GET("api/settings/login-theme-mode")
    suspend fun getLoginThemeMode(): Response<LoginThemeModeField>

    @POST("api/settings/login-theme-mode")
    suspend fun setLoginThemeMode(@Body body: LoginThemeModeField): Response<LoginThemeModeField>
}
