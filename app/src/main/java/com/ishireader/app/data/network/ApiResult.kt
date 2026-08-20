package com.ishireader.app.data.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    /** [isNetworkError] distinguishes "couldn't reach the server at all" (an IOException/timeout)
     *  from "the server responded and said no" (a non-2xx response) -- most callers don't care,
     *  but LoginViewModel needs it to tell "offline" apart from "actually logged out" when
     *  deciding whether to fall back to the cached, downloaded-only library.
     *  [isAuthError] flags a 401 from a call that authenticates against a *third-party* account
     *  (currently just AniList, see AniListRepository) rather than this app's own session -- lets
     *  callers show "reconnect your account" instead of a generic error message. */
    data class Failure(val message: String, val isNetworkError: Boolean = false, val isAuthError: Boolean = false) : ApiResult<Nothing>()
}

fun <T> ApiResult<T>.dataOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}
