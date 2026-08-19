package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of a book's word count -- purely so a book already computed once doesn't get
 *  recomputed from raw HTML every single session the server happens to be unreachable at open
 *  time. [posted] tracks whether this device has ever successfully told the server, since the
 *  value is meant to be POSTed exactly once (see WordCountRequest's doc comment); an unposted
 *  local computation is retried by [com.ishireader.app.data.sync.ReadingTimerSyncWorker] rather
 *  than silently dropped the way the old fire-and-forget setWordCount left it. */
@Entity(tableName = "word_count_cache")
data class WordCountCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val wordCount: Double,
    val posted: Boolean
)
