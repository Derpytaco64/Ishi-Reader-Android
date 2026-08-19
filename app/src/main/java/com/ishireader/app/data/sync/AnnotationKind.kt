package com.ishireader.app.data.sync

/** The "kind" discriminator shared by [com.ishireader.app.data.local.AnnotationCacheEntity]/
 *  [com.ishireader.app.data.local.AnnotationOutboxEntity] rows, [LocalFirstAnnotationStore], and
 *  [AnnotationsSyncWorker] -- kept in one place so the repositories and the worker can't drift on
 *  the string each uses to route a row to the right upsert/delete API call. [COMPLETED_LISTEN]
 *  isn't an annotation, but completedListens (GET the list, POST upsert-by-id, DELETE by id -- see
 *  ListeningTimeRepository) has the exact same shape, so it reuses this store rather than
 *  duplicating it for one more id-keyed list. */
object AnnotationKind {
    const val HIGHLIGHT = "highlight"
    const val BOOKMARK = "bookmark"
    const val NOTE = "note"
    const val COMPLETED_LISTEN = "completed_listen"
}
