package com.ishireader.app.data.repository

import com.ishireader.app.data.model.AdminUser
import com.ishireader.app.data.model.AvatarUploadRequest
import com.ishireader.app.data.model.BookFolderField
import com.ishireader.app.data.model.CreateUserRequest
import com.ishireader.app.data.model.LoginAccentColorField
import com.ishireader.app.data.model.LoginThemeModeField
import com.ishireader.app.data.model.MigrateOrphanedDataRequest
import com.ishireader.app.data.model.OrphanedDataReport
import com.ishireader.app.data.model.ReadiumPortRequest
import com.ishireader.app.data.model.ReadiumUrlField
import com.ishireader.app.data.model.ResetPasswordRequest
import com.ishireader.app.data.model.UserDataFolderField
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

/** Wraps the admin/users endpoints (user management) and the admin-only settings endpoints
 *  (server config) -- everything AdminPageClient.tsx's admin panel does, minus its 30s
 *  active-status polling (not worth it for a screen a mobile admin opens occasionally). */
class AdminRepository(private val network: NetworkModule) {

    suspend fun listUsers(): ApiResult<List<AdminUser>> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminListUsers() }.toApiResult("load users") { it.users }
    }

    suspend fun createUser(username: String, name: String, password: String, isAdmin: Boolean): ApiResult<AdminUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                network.api.adminCreateUser(CreateUserRequest(username, name, password, isAdmin))
            }.toApiResult("create user") { it.user ?: error("missing user in response") }
        }

    suspend fun renameUser(id: String, username: String, name: String): ApiResult<AdminUser> = withContext(Dispatchers.IO) {
        val patch = JsonObject(mapOf("username" to JsonPrimitive(username), "name" to JsonPrimitive(name)))
        runCatching { network.api.adminUpdateUser(id, patch) }.toApiResult("update user") { it.user ?: error("missing user in response") }
    }

    suspend fun setAdmin(id: String, isAdmin: Boolean): ApiResult<AdminUser> = withContext(Dispatchers.IO) {
        val patch = JsonObject(mapOf("isAdmin" to JsonPrimitive(isAdmin)))
        runCatching { network.api.adminUpdateUser(id, patch) }.toApiResult("update user") { it.user ?: error("missing user in response") }
    }

    suspend fun setDisabled(id: String, disabled: Boolean): ApiResult<AdminUser> = withContext(Dispatchers.IO) {
        val patch = JsonObject(mapOf("disabled" to JsonPrimitive(disabled)))
        runCatching { network.api.adminUpdateUser(id, patch) }.toApiResult("update user") { it.user ?: error("missing user in response") }
    }

    suspend fun deleteUser(id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminDeleteUser(id) }.toApiResult("delete user") {}
    }

    suspend fun resetPassword(id: String, newPassword: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminResetPassword(id, ResetPasswordRequest(newPassword)) }.toApiResult("reset password") {}
    }

    suspend fun unlockUser(id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminUnlockUser(id) }.toApiResult("unlock user") {}
    }

    suspend fun uploadUserAvatar(id: String, imageDataUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminUploadAvatar(id, AvatarUploadRequest(imageDataUrl)) }.toApiResult("upload avatar") {}
    }

    suspend fun scanOrphanedData(): ApiResult<OrphanedDataReport> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminScanOrphanedData() }.toApiResult("scan for orphaned data") { it }
    }

    suspend fun deleteOrphanedData(): ApiResult<OrphanedDataReport> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminDeleteOrphanedData() }.toApiResult("delete orphaned data") { it }
    }

    /** Recover-instead-of-delete counterpart to [deleteOrphanedData] -- carries one orphaned book's
     *  UserData onto a live library entry. See api/admin/orphaned-data/migrate/route.ts. */
    suspend fun migrateOrphanedData(userId: String, sourceHash: String, destManifestUrl: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                network.api.adminMigrateOrphanedData(MigrateOrphanedDataRequest(userId, sourceHash, destManifestUrl))
            }.toApiResult("migrate orphaned data") {}
        }

    suspend fun clearReadingSpeedSamples(): ApiResult<Int> = withContext(Dispatchers.IO) {
        runCatching { network.api.adminClearReadingSpeedSamples() }.toApiResult("clear WPM samples") { it.clearedCount }
    }

    suspend fun getBookFolder(): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.getBookFolder() }.toApiResult("load book folder") { it.bookFolder }
    }

    suspend fun setBookFolder(path: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.setBookFolder(BookFolderField(path)) }.toApiResult("save book folder") { it.bookFolder }
    }

    suspend fun getReadiumUrl(): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.getReadiumUrl() }.toApiResult("load Readium URL") { it.readiumUrl }
    }

    suspend fun setReadiumUrl(url: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.setReadiumUrl(ReadiumUrlField(url)) }.toApiResult("save Readium URL") { it.readiumUrl }
    }

    suspend fun getReadiumPort(): ApiResult<Int> = withContext(Dispatchers.IO) {
        runCatching { network.api.getReadiumPort() }.toApiResult("load server port") { it.readiumPort }
    }

    suspend fun setReadiumPort(port: String): ApiResult<Int> = withContext(Dispatchers.IO) {
        runCatching { network.api.setReadiumPort(ReadiumPortRequest(port)) }.toApiResult("save server port") { it.readiumPort }
    }

    suspend fun getUserDataFolder(): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.getUserDataFolder() }.toApiResult("load user data folder") { it.userDataFolder }
    }

    suspend fun setUserDataFolder(path: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.setUserDataFolder(UserDataFolderField(path)) }.toApiResult("save user data folder") { it.userDataFolder }
    }

    suspend fun getLoginAccentColor(): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.getLoginAccentColor() }.toApiResult("load accent color") { it.loginAccentColor }
    }

    suspend fun setLoginAccentColor(hex: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.setLoginAccentColor(LoginAccentColorField(hex)) }.toApiResult("save accent color") { it.loginAccentColor }
    }

    suspend fun getLoginThemeMode(): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.getLoginThemeMode() }.toApiResult("load theme mode") { it.loginThemeMode }
    }

    suspend fun setLoginThemeMode(mode: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching { network.api.setLoginThemeMode(LoginThemeModeField(mode)) }.toApiResult("save theme mode") { it.loginThemeMode }
    }
}

/** Shared Response<T>-to-ApiResult<R> mapping so every admin call above stays a one-liner --
 *  same isSuccessful/body-not-null/exception shape every other repository in this package uses. */
private fun <T, R> Result<Response<T>>.toApiResult(action: String, extract: (T) -> R): ApiResult<R> =
    fold(
        onSuccess = { response ->
            val body = response.body()
            if (response.isSuccessful && body != null) {
                // extract can throw (createUser/renameUser/setAdmin/setDisabled's "missing user in
                // response" guard) -- caught here rather than left to crash the caller's coroutine.
                runCatching { extract(body) }.fold(
                    onSuccess = { ApiResult.Success(it) },
                    onFailure = { ApiResult.Failure("Couldn't $action") }
                )
            } else {
                // Server error bodies here are JSON ({error: "..."}), not the success shape T, so
                // response.errorBody() can't be decoded as T -- fall back to the status code the
                // same way every other repository's Failure message does.
                ApiResult.Failure("Couldn't $action (${response.code()})")
            }
        },
        onFailure = { e -> ApiResult.Failure(e.message ?: "Network error") }
    )
