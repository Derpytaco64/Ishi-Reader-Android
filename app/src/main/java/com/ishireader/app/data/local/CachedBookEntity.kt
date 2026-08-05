package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The on-device cache of the last successful /api/books fetch, so the library can still render
 * (a downloaded book's own metadata included) when the server can't be reached -- see
 * LibraryRepository.fetchBooks and LoginViewModel's offline-entry path. [bookJson] is the whole
 * Book serialized verbatim (mirrors PositionEntity.locatorJson's reasoning: Book already has a
 * `@Serializable` shape with several nested/polymorphic-ish fields -- series, tags, etc -- that
 * aren't worth re-deriving as Room type converters just to store a read-only cache).
 */
@Entity(tableName = "cached_books")
data class CachedBookEntity(
    @PrimaryKey val url: String,
    val bookJson: String
)
