package com.ishireader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PositionEntity::class,
        CachedBookEntity::class,
        CachedLibraryPrefsEntity::class,
        CachedUserStatsEntity::class,
        CachedUserEntity::class,
        PendingLibraryPrefsPatchEntity::class,
        ExactPageCountEntity::class,
        ExactPercentEntity::class,
        ReadingTimeCacheEntity::class,
        DailyReadingBucketCacheEntity::class,
        PendingDailyReadingBucketDeltaEntity::class,
        ReadingSpeedSampleCacheEntity::class,
        PendingReadingSpeedSamplesEntity::class,
        WordCountCacheEntity::class,
        PageCountCacheEntity::class,
        AnnotationCacheEntity::class,
        AnnotationOutboxEntity::class,
        ListeningTimeCacheEntity::class,
        DailyListeningBucketCacheEntity::class,
        PendingDailyListeningBucketDeltaEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class IshiReaderDatabase : RoomDatabase() {

    abstract fun positionDao(): PositionDao
    abstract fun cachedBookDao(): CachedBookDao
    abstract fun cachedLibraryPrefsDao(): CachedLibraryPrefsDao
    abstract fun cachedUserStatsDao(): CachedUserStatsDao
    abstract fun cachedUserDao(): CachedUserDao
    abstract fun pendingLibraryPrefsPatchDao(): PendingLibraryPrefsPatchDao
    abstract fun exactPageCountDao(): ExactPageCountDao
    abstract fun exactPercentDao(): ExactPercentDao
    abstract fun readingTimeCacheDao(): ReadingTimeCacheDao
    abstract fun dailyReadingBucketDao(): DailyReadingBucketDao
    abstract fun readingSpeedSampleDao(): ReadingSpeedSampleDao
    abstract fun wordCountCacheDao(): WordCountCacheDao
    abstract fun pageCountCacheDao(): PageCountCacheDao
    abstract fun annotationCacheDao(): AnnotationCacheDao
    abstract fun annotationOutboxDao(): AnnotationOutboxDao
    abstract fun listeningTimeCacheDao(): ListeningTimeCacheDao
    abstract fun dailyListeningBucketDao(): DailyListeningBucketDao

    companion object {
        @Volatile
        private var instance: IshiReaderDatabase? = null

        fun getInstance(context: Context): IshiReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IshiReaderDatabase::class.java,
                    "ishi-reader.db"
                )
                    // App is pre-release (no shipped users to preserve data for) -- destructive
                    // fallback beats hand-writing a migration for a table that never existed in
                    // any released version.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
