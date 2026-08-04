package com.ishireader.app.data.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

fun <T> ApiResult<T>.dataOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}
