package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * List-shaped outbox for AniList progress/tracking-field writes not yet confirmed synced -- one row
 * per linked mediaId (unlike PendingLibraryPrefsPatchEntity's single global row, several different
 * series can have independent unsynced edits at once). [patchJson] holds only the fields actually
 * pending as a serialized JsonObject (status/score/progress/repeat/startedAt/completedAt) -- a key's
 * *absence* means "untouched since last sync", matching the presence-based patch semantics of the
 * server's POST /api/anilist/list-entry route and [com.ishireader.app.data.network.ApiService.
 * saveAniListEntry]. Both the ambient reading-progress writer (chapter number increased) and the
 * explicit TrackingSheet edits (status/score/dates/repeat) merge into this same row, keyed by
 * mediaId, so a flush only ever needs one SaveMediaListEntry call per series.
 */
@Entity(tableName = "pending_anilist_patch")
data class PendingAniListPatchEntity(
    @PrimaryKey val mediaId: Int,
    val patchJson: String,
    val updatedAtMillis: Long
)
