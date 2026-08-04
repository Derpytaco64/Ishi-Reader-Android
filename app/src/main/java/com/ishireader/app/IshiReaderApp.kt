package com.ishireader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.prefs.AppPreferences
import com.ishireader.app.data.repository.AuthRepository
import com.ishireader.app.data.repository.LibraryPrefsRepository
import com.ishireader.app.data.repository.LibraryRepository
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.PositionRepository
import com.ishireader.app.data.repository.StatsRepository
import okhttp3.OkHttpClient

/**
 * Hand-rolled service locator instead of a DI framework -- the object graph here is small
 * (one network client, a handful of thin repositories) and doesn't earn its keep yet. Reach
 * for Hilt if this grows past a handful of dependencies.
 */
class IshiReaderApp : Application(), ImageLoaderFactory {

    lateinit var preferences: AppPreferences
        private set
    lateinit var network: NetworkModule
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var positionRepository: PositionRepository
        private set
    lateinit var libraryPrefsRepository: LibraryPrefsRepository
        private set
    lateinit var notesRepository: NotesRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        network = NetworkModule(this)
        authRepository = AuthRepository(network)
        libraryRepository = LibraryRepository(network)
        positionRepository = PositionRepository(network)
        libraryPrefsRepository = LibraryPrefsRepository(network)
        notesRepository = NotesRepository(network)
        statsRepository = StatsRepository(network)
    }

    /**
     * Book covers are served from the same session-protected origin as the rest of the API
     * (see /api/books' `cover` field), so Coil needs the same cookie jar the Retrofit client
     * uses rather than a plain default client.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().cookieJar(network.cookieJar).build() }
        .build()
}
