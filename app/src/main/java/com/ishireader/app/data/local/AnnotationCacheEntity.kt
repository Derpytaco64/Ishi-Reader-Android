package com.ishireader.app.data.local

import androidx.room.Entity

/** Last-known merged list for one (kind, book) pair -- "kind" is "highlight"/"bookmark"/"note"
 *  (see AnnotationKind), so highlights/bookmarks/notes share one table instead of three near-
 *  identical ones. [itemsJson] is a JSON-encoded list of the raw item objects (StoredHighlight/
 *  StoredBookmark/StoredNote), kept as a string the same way [CachedLibraryPrefsEntity] does --
 *  Room never needs to query into it, only [LocalFirstAnnotationStore] decodes it. */
@Entity(tableName = "annotation_cache", primaryKeys = ["kind", "manifestUrl"])
data class AnnotationCacheEntity(
    val kind: String,
    val manifestUrl: String,
    val itemsJson: String,
    val updatedAtMillis: Long
)
