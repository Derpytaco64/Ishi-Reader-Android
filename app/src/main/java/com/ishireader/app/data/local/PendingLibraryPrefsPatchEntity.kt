package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox for library-prefs writes made while the server was unreachable (accent color, home shelf
 * order/visibility, custom shelves, continue-reading dismissals -- anything routed through
 * LibraryPrefsRepository.patchPrefs). [patchJson] is the cumulative shallow-merged patch still
 * owed to the server, using the same merge semantics as the server's own PATCH handler; successive
 * offline edits merge into this one row rather than queuing separately, mirroring
 * LibraryPrefsRepository's existing cache-merge convention. Cleared once a flush succeeds. Always
 * id 0, same single-row convention as [CachedLibraryPrefsEntity].
 */
@Entity(tableName = "pending_library_prefs_patch")
data class PendingLibraryPrefsPatchEntity(
    @PrimaryKey val id: Int = 0,
    val patchJson: String
)
