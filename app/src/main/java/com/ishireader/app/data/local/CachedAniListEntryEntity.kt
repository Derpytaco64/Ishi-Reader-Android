package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-through cache of the last-known AniList state for one linked media, keyed by AniList's own
 * mediaId (not manifestUrl -- an AniList entry represents a whole series, not one book/volume file,
 * so it can't be keyed the way PositionEntity/etc. are). The whole [AniListMedia] (including its
 * nested mediaListEntry) is stored as one JSON blob, same convention as CachedBookEntity, rather
 * than exploded into columns -- this is purely a display/comparison cache, never queried by field.
 * Also doubles as the "remote" baseline AniListRepository.patchEntry compares an incoming progress
 * write against, so a stale offline chapter-progress push can never regress a value that's actually
 * moved forward on AniList's own site/app in the meantime.
 */
@Entity(tableName = "cached_anilist_entry")
data class CachedAniListEntryEntity(
    @PrimaryKey val mediaId: Int,
    val mediaJson: String,
    val updatedAtMillis: Long
)
