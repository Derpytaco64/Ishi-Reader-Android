package com.ishireader.app.data.repository

import com.ishireader.app.data.local.CachedUserDao
import com.ishireader.app.data.local.CachedUserEntity
import com.ishireader.app.data.model.AvatarUploadRequest
import com.ishireader.app.data.model.ChangePasswordRequest
import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.model.SetupPasswordRequest
import com.ishireader.app.data.model.UpdateProfileRequest
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response

/** Richer than [ApiResult] because the site's own login flow (see LoginPageClient.tsx) branches
 *  three ways on the same /api/auth/login response -- signed in, this account has no password yet
 *  (-> setup form), or a plain failure (wrong password / locked out). */
sealed class LoginAttemptResult {
    data class LoggedIn(val user: PublicUser) : LoginAttemptResult()
    data class NeedsPasswordSetup(val userId: String) : LoginAttemptResult()
    data class Failed(val message: String, val lockedUntil: Long? = null) : LoginAttemptResult()
}

class AuthRepository(
    private val network: NetworkModule,
    private val cachedUserDao: CachedUserDao
) {

    /** Unauthenticated account picker list for the login screen -- disabled accounts and
     *  admin-only fields are already stripped server-side (see /api/auth/users). */
    suspend fun listPublicUsers(): ApiResult<List<PublicUser>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.publicUsers()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.users)
            } else {
                ApiResult.Failure("Couldn't load accounts (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun login(username: String, password: String): LoginAttemptResult = withContext(Dispatchers.IO) {
        try {
            val response = network.api.login(LoginRequest(username, password))
            val body = response.body()
            val needsSetupUserId = body?.takeIf { it.needsPasswordSetup == true }?.userId

            when {
                needsSetupUserId != null -> LoginAttemptResult.NeedsPasswordSetup(needsSetupUserId)
                !response.isSuccessful ->
                    LoginAttemptResult.Failed(body?.error ?: "Login failed (${response.code()})", body?.lockedUntil)
                body?.ok == true -> when (val user = fetchCurrentUser()) {
                    is ApiResult.Success -> LoginAttemptResult.LoggedIn(user.data)
                    is ApiResult.Failure -> LoginAttemptResult.Failed(user.message)
                }
                else -> LoginAttemptResult.Failed(body?.error ?: "Login failed")
            }
        } catch (e: Exception) {
            LoginAttemptResult.Failed(e.message ?: "Network error")
        }
    }

    /** Only reachable for an account with no password yet -- see /api/auth/setup-password. */
    suspend fun setupPassword(userId: String, password: String): LoginAttemptResult = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setupPassword(SetupPasswordRequest(userId, password))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                when (val user = fetchCurrentUser()) {
                    is ApiResult.Success -> LoginAttemptResult.LoggedIn(user.data)
                    is ApiResult.Failure -> LoginAttemptResult.Failed(user.message)
                }
            } else {
                LoginAttemptResult.Failed(body?.error ?: "Couldn't set password (${response.code()})")
            }
        } catch (e: Exception) {
            LoginAttemptResult.Failed(e.message ?: "Network error")
        }
    }

    suspend fun getLoginAccentColor(): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getLoginAccentColor()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.loginAccentColor)
            } else {
                ApiResult.Failure("Couldn't load accent color")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getLoginThemeMode(): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getLoginThemeMode()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.loginThemeMode)
            } else {
                ApiResult.Failure("Couldn't load theme mode")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Caches a successful fetch and falls back to that cache on a network error (not a real "not
     *  signed in" response) so the top bar's name/avatar survive a server-unreachable resume
     *  instead of reverting to the initial-letter placeholder -- see TopBarViewModel, the only
     *  other reader of this besides the post-login flows above, which don't need offline entry. */
    suspend fun fetchCurrentUser(): ApiResult<PublicUser> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.me()
            val user = response.body()?.user
            if (response.isSuccessful && user != null) {
                cachedUserDao.set(user.toEntity())
                ApiResult.Success(user)
            } else {
                // Server reachable and says no -- a real "not signed in," not a connectivity issue.
                ApiResult.Failure("Not signed in")
            }
        } catch (e: Exception) {
            // Couldn't reach the server at all (IOException/timeout/etc.) -- see
            // LoginViewModel.connect, which uses this to allow offline entry to a cached library.
            cachedUserDao.get()?.toPublicUser()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching { network.api.logout() }
            network.cookieJar.clear()
        }
    }

    /** [imageDataUrl] is a data URL ("data:image/png;base64,..."), same shape the website's
     *  FileReader.readAsDataURL produces client-side. Returns the new cache-busted avatar path
     *  (server-relative, needs [NetworkModule.baseUrl] prepended same as [PublicUser.avatarUrl]). */
    suspend fun uploadAvatar(imageDataUrl: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.uploadAvatar(AvatarUploadRequest(imageDataUrl))
            val avatarUrl = response.body()?.avatarUrl
            if (response.isSuccessful && avatarUrl != null) {
                ApiResult.Success(avatarUrl)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't upload picture"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Self-service display-name change only -- username changes stay admin-only. */
    suspend fun updateProfile(name: String): ApiResult<PublicUser> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.updateProfile(UpdateProfileRequest(name))
            val user = response.body()?.user
            if (response.isSuccessful && user != null) {
                cachedUserDao.set(user.toEntity())
                ApiResult.Success(user)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't save name"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
            if (response.isSuccessful && response.body()?.ok == true) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(response.serverErrorMessage("Couldn't change password"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}

@Serializable
private data class ErrorBody(val error: String? = null)

/** These three endpoints' validation messages (password length, current-password mismatch, image
 *  too large/unsupported type) are the whole point of surfacing them -- unlike this file's other
 *  calls, a bare status code wouldn't tell the user what to fix. [Response.errorBody] holds the
 *  server's `{error: "..."}` JSON on a non-2xx response (its success-shaped body is null then). */
private fun <T> Response<T>.serverErrorMessage(fallback: String): String {
    val raw = errorBody()?.string()
    val parsed = raw?.let { runCatching { Json.decodeFromString<ErrorBody>(it) }.getOrNull() }
    return parsed?.error?.takeIf { it.isNotBlank() } ?: "$fallback (${code()})"
}

private fun PublicUser.toEntity() = CachedUserEntity(
    userId = id,
    username = username,
    name = name,
    isAdmin = isAdmin,
    avatarUrl = avatarUrl,
    needsPasswordSetup = needsPasswordSetup
)

private fun CachedUserEntity.toPublicUser() = PublicUser(
    id = userId,
    username = username,
    name = name,
    isAdmin = isAdmin,
    avatarUrl = avatarUrl,
    needsPasswordSetup = needsPasswordSetup
)
