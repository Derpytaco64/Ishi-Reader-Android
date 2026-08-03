package com.ishireader.app.data.model

import java.text.Collator

/** Mirrors Ishi-Read's sortPublications.ts exactly -- same 6 modes, same default. */
enum class SortMode(val label: String) {
    ADDED_NEWEST("Date Added (Newest)"),
    ADDED_OLDEST("Date Added (Oldest)"),
    TITLE_ASC("Title (A–Z)"),
    TITLE_DESC("Title (Z–A)"),
    AUTHOR_ASC("Author (A–Z)"),
    AUTHOR_DESC("Author (Z–A)")
}

val DEFAULT_SORT_MODE = SortMode.ADDED_NEWEST

private val collator: Collator = Collator.getInstance()

/** [addedAt] lets shelf views sort by "date added to shelf" instead of "date added to library". */
fun List<Book>.sortedByMode(mode: SortMode, addedAt: (Book) -> Double? = { it.addedAt }): List<Book> =
    when (mode) {
        SortMode.TITLE_ASC -> sortedWith(compareBy(collator) { it.title })
        SortMode.TITLE_DESC -> sortedWith(compareByDescending(collator) { it.title })
        SortMode.AUTHOR_ASC -> sortedWith(compareBy(collator) { it.author })
        SortMode.AUTHOR_DESC -> sortedWith(compareByDescending(collator) { it.author })
        SortMode.ADDED_NEWEST -> sortedByDescending { addedAt(it) ?: 0.0 }
        SortMode.ADDED_OLDEST -> sortedBy { addedAt(it) ?: 0.0 }
    }
