package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-device cache of Ishi-Read's /api/books/reading-progression response (see
 * ReadingProgressionResponse) -- the local CBZ parser never reads ComicInfo.xml itself, so this
 * server response is the only source for a comic's reading direction and synthesized chapter TOC
 * (see ComicToc.kt), normally fetched fresh on every book open. Without this cache, opening a
 * comic offline silently got neither: the chapter title pill had nothing to show and Reading
 * Direction "Auto" couldn't resolve. [responseJson] is the response serialized verbatim (mirrors
 * ReaderPreferencesStore's json-blob approach) rather than typed columns, since it's a small,
 * already-@Serializable DTO with no query needs beyond "the whole thing, by manifestUrl".
 */
@Entity(tableName = "comic_reading_progression_cache")
data class ComicReadingProgressionCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val responseJson: String,
    val updatedAtMillis: Long
)
