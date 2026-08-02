package com.ishireader.app.data.repository

import com.ishireader.app.data.model.LoginRequest
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val network: NetworkModule) {

    suspend fun login(username: String, password: String): ApiResult<PublicUser> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.login(LoginRequest(username, password))
            val body = response.body()

            when {
                !response.isSuccessful -> ApiResult.Failure(body?.error ?: "Login failed (${response.code()})")
                body?.needsPasswordSetup == true -> ApiResult.Failure("This account needs a password set up on the web app first")
                body?.ok == true -> fetchCurrentUser()
                else -> ApiResult.Failure(body?.error ?: "Login failed")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun fetchCurrentUser(): ApiResult<PublicUser> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.me()
            val user = response.body()?.user
            if (response.isSuccessful && user != null) {
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("Not signed in")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching { network.api.logout() }
            network.cookieJar.clear()
        }
    }
}
