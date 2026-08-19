package com.ishireader.app.data.sync

/** The "kind" discriminator shared by [com.ishireader.app.data.local.AnnotationCacheEntity]/
 *  [com.ishireader.app.data.local.AnnotationOutboxEntity] rows, [LocalFirstAnnotationStore], and
 *  [AnnotationsSyncWorker] -- kept in one place so the repositories and the worker can't drift on
 *  the string each uses to route a row to the right upsert/delete API call. [COMPLETED_LISTEN]
 *  isn't an annotation, but completedListens (GET the list, POST upsert-by-id, DELETE by id -- see
 *  ListeningTimeRepository) has the exact same shape, so it reuses this store rather than
 *  duplicating it for one more id-keyed list. [COMPLETED_READ] (StoredCompletedReadTime, the
 *  "reading sessions" list on BookDetailScreen -- see CompletedReadsRepository) is the reading
 *  counterpart of [COMPLETED_LISTEN] and reuses the store for the same reason: it was the one
 *  piece of the reading-timer data left network-first with no offline cache, which is what made
 *  the completed-sessions list go blank offline even after ReadingTimerRepository/
 *  ReadingTimerReconciler got their own fix. */
object AnnotationKind {
    const val HIGHLIGHT = "highlight"
    const val BOOKMARK = "bookmark"
    const val NOTE = "note"
    const val COMPLETED_LISTEN = "completed_listen"
    const val COMPLETED_READ = "completed_read"
}
