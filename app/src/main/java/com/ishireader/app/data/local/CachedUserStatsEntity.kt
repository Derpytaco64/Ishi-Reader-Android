package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row cache of the last successful /api/userdata/stats fetch, so opening the stats dialog
 * offline shows the last known numbers instead of nothing (see StatsRepository). [id] is always 0,
 * same convention as [CachedLibraryPrefsEntity].
 */
@Entity(tableName = "cached_user_stats")
data class CachedUserStatsEntity(
    @PrimaryKey val id: Int = 0,
    val booksInLibrary: Int,
    val booksStarted: Int,
    val booksFinished: Int,
    val totalReadingSeconds: Double,
    val totalWordsRead: Int,
    val averageWpm: Int?,
    val currentStreakDays: Int,
    val highlightsCount: Int,
    val bookmarksCount: Int,
    val notesCount: Int,
    val audiobooksInLibrary: Int,
    val audiobooksStarted: Int,
    val audiobooksFinished: Int,
    val totalListeningSeconds: Double
)
