package com.ishireader.app.data.model

import kotlinx.serialization.Serializable

/** Mirrors Ishi-Read's /api/books/reading-progression route -- CBZ-only (readingProgression is
 *  null and bookmarks is empty for a non-comic book, since the bundled Go manifest server never
 *  reads ComicInfo.xml itself). [chapterNumber] is parsed server-side out of each bookmark's
 *  "Chapter N - Title" string (see comicInfo.ts's parseChapterNumber) -- null when no number could
 *  be parsed out of that particular bookmark's title. */
@Serializable
data class CBZPageBookmark(val pageIndex: Int, val title: String, val chapterNumber: Int? = null)

@Serializable
data class ReadingProgressionResponse(
    val readingProgression: String? = null,
    val bookmarks: List<CBZPageBookmark> = emptyList()
)
