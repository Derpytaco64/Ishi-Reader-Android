package com.ishireader.app.data.local

import androidx.room.Entity

/**
 * Outbox row for one highlight/bookmark/note create-or-edit ("UPSERT") or delete not yet confirmed
 * synced. Unlike ReadingTimerRepository's caches, an annotation write never needs merge-math to
 * stay safe offline -- each row is upsert-by-id or delete-by-id against the server, so queuing and
 * replaying it later can't clobber anything else in the list the way a whole-value overwrite could.
 * The old AnnotationsRepository/NotesRepository had no queue at all: a highlight/bookmark/note
 * created while offline just silently failed its POST and vanished (though it stayed decorated
 * in-session since [com.ishireader.app.reader.AnnotationsController] optimistically holds it in
 * memory until the next refresh). [itemJson] is null for a DELETE row.
 */
@Entity(tableName = "annotation_outbox", primaryKeys = ["kind", "manifestUrl", "itemId"])
data class AnnotationOutboxEntity(
    val kind: String,
    val manifestUrl: String,
    val itemId: String,
    val opType: String,
    val itemJson: String?,
    val updatedAtMillis: Long
)
