package com.ishireader.app

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ishireader.app.data.local.IshiReaderDatabase
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.prefs.AppPreferences
import com.ishireader.app.data.prefs.ReaderPreferencesStore
import com.ishireader.app.data.repository.AdminRepository
import com.ishireader.app.data.repository.AuthRepository
import com.ishireader.app.data.repository.BookDownloadRepository
import com.ishireader.app.data.repository.CompletedReadsRepository
import com.ishireader.app.data.repository.LibraryPrefsRepository
import com.ishireader.app.data.repository.LibraryRepository
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.PositionRepository
import com.ishireader.app.data.repository.StatsRepository
import com.ishireader.app.data.sync.IshiWorkerFactory
import com.ishireader.app.data.sync.PositionReconciler
import com.ishireader.app.data.sync.SyncScheduler
import okhttp3.OkHttpClient

/**
 * Hand-rolled service locator instead of a DI framework -- the object graph here is small
 * (one network client, a handful of thin repositories) and doesn't earn its keep yet. Reach
 * for Hilt if this grows past a handful of dependencies.
 */
class IshiReaderApp : Application(), ImageLoaderFactory, Configuration.Provider {

    lateinit var preferences: AppPreferences
        private set
    lateinit var readerPreferencesStore: ReaderPreferencesStore
        private set
    lateinit var network: NetworkModule
        private set
    lateinit var database: IshiReaderDatabase
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var positionRepository: PositionRepository
        private set
    lateinit var bookDownloadRepository: BookDownloadRepository
        private set
    lateinit var libraryPrefsRepository: LibraryPrefsRepository
        private set
    lateinit var notesRepository: NotesRepository
        private set
    lateinit var completedReadsRepository: CompletedReadsRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var adminRepository: AdminRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        readerPreferencesStore = ReaderPreferencesStore(this)
        network = NetworkModule(this)
        database = IshiReaderDatabase.getInstance(this)
        val syncScheduler = SyncScheduler(this)
        authRepository = AuthRepository(network, database.cachedUserDao())
        libraryRepository = LibraryRepository(network, database.cachedBookDao())
        positionRepository = PositionRepository(
            database.positionDao(),
            syncScheduler,
            PositionReconciler(database.positionDao(), network)
        )
        bookDownloadRepository = BookDownloadRepository(this, network)
        libraryPrefsRepository = LibraryPrefsRepository(
            network,
            database.cachedLibraryPrefsDao(),
            database.pendingLibraryPrefsPatchDao(),
            syncScheduler
        )
        notesRepository = NotesRepository(network)
        completedReadsRepository = CompletedReadsRepository(network)
        statsRepository = StatsRepository(network, database.cachedUserStatsDao())
        adminRepository = AdminRepository(network)
    }

    /**
     * Book covers are served from the same session-protected origin as the rest of the API
     * (see /api/books' `cover` field), so Coil needs the same cookie jar the Retrofit client
     * uses rather than a plain default client.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().cookieJar(network.cookieJar).build() }
        .build()

    /** Registers IshiWorkerFactory so PositionSyncWorker gets its DAO/NetworkModule injected
     *  instead of relying on WorkManager's default no-arg reflection -- pairs with disabling the
     *  default auto-initializer in AndroidManifest.xml, which would otherwise win the race. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                IshiWorkerFactory(
                    database.positionDao(),
                    database.cachedLibraryPrefsDao(),
                    database.pendingLibraryPrefsPatchDao(),
                    network
                )
            )
            .build()
}
