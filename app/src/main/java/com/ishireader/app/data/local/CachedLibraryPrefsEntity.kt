package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row cache of the last successful /api/userdata/library-prefs GET -- that one blob backs
 * Continue Reading dismissals, custom shelves, and theme/accent/coverSize/shelfOrder/visibility
 * (see LibraryPrefsRepository), so without a cache a server-unreachable launch would silently show
 * previously-dismissed books again and reset every one of those settings to its default. [id] is
 * always 0: there's exactly one blob per logged-in user, and only one user is ever signed in on a
 * given install at a time.
 */
@Entity(tableName = "cached_library_prefs")
data class CachedLibraryPrefsEntity(
    @PrimaryKey val id: Int = 0,
    val prefsJson: String
)
