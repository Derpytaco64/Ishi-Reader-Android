package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Read-only display cache of the server-computed page count (see PageCountResponse's doc comment
 *  -- GET-only, the client never pushes this value, so there's no write-conflict risk here, just a
 *  fallback so the "# of pages" chip/estimate doesn't go blank while offline). */
@Entity(tableName = "page_count_cache")
data class PageCountCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val pageCount: Int
)
