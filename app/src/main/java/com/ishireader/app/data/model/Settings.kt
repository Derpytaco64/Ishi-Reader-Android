package com.ishireader.app.data.model

/** Persisted under library-prefs' freeform blob (see LibraryPrefsRepository) so these settings
 *  follow the user across devices/reinstalls, same as customShelves/continueReadingDismissed. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class CoverSize(val key: String, val minWidthDp: Int) {
    SMALL("small", 100), MEDIUM("medium", 130), LARGE("large", 160);

    companion object {
        fun fromKey(key: String?): CoverSize = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** The Home tab's shelves -- order here is display order, matching how customShelves' own array
 *  order doubles as its display order. */
enum class HomeShelfId(val key: String, val displayName: String) {
    CONTINUE_READING("continueReading", "Continue Reading"),
    LAST_SERIES_READ("lastSeriesRead", "Last Series Read"),
    RECENTLY_ADDED("recentlyAdded", "Recently Added"),
    MY_LIBRARY("myLibrary", "My Library");

    companion object {
        fun fromKey(key: String): HomeShelfId? = entries.firstOrNull { it.key == key }
        val Default: List<HomeShelfId> = entries.toList()
    }
}

/** accentColor is a "#RRGGBB" hex string (or null for the app's default color) rather than a
 *  Compose Color -- this model is shared by the repository layer, which has no reason to depend
 *  on a UI graphics type. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: String? = null,
    val coverSize: CoverSize = CoverSize.MEDIUM,
    val shelfOrder: List<HomeShelfId> = HomeShelfId.Default,
    val shelfVisibility: Map<HomeShelfId, Boolean> = emptyMap()
) {
    fun isShelfVisible(id: HomeShelfId): Boolean = shelfVisibility[id] ?: true
}
