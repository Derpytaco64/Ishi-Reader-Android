package com.ishireader.app.data.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Retrofit needs a base URL at construction time, but the Ishi-Read server address is
 * something the user types in (it's a self-hosted instance, there's no fixed API host).
 * This rebuilds the client whenever [configure] is called with a new URL, and exposes the
 * currently-active [ApiService] via [api]. Call [configure] once at app start with the
 * last-saved server URL, and again whenever the user changes it in settings.
 */
class NetworkModule(context: Context) {

    val cookieJar = PersistentCookieJar(context.applicationContext)

    private val json = Json { ignoreUnknownKeys = true }

    private var currentBaseUrl: String? = null
    private var currentApi: ApiService? = null

    val api: ApiService
        get() = currentApi ?: error("NetworkModule.configure(serverUrl) must be called before use")

    val isConfigured: Boolean
        get() = currentApi != null

    fun configure(serverUrl: String) {
        val normalized = serverUrl.trimEnd('/')
        if (normalized == currentBaseUrl) return

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$normalized/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        currentBaseUrl = normalized
        currentApi = retrofit.create(ApiService::class.java)
    }
}
