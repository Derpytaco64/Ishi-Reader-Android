package com.ishireader.app.reader

import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.aniListSeriesKey
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.repository.AniListRepository
import com.ishireader.app.data.repository.LibraryPrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Auto-advances a linked manga's AniList `progress` field as the user reads, using the CBZ's own
 * ComicInfo.xml chapter bookmarks (server-parsed, see comicInfo.ts's parseChapterNumber) matched
 * against the live page position. Manga-only: each CBZ resource renders as exactly one dynamic page
 * (see DynamicPageCountTracker's own doc comment on the comic page-count algorithm), so
 * [DynamicPageCountState.currentPage] minus one already *is* the 0-based reading-order/bookmark
 * image index -- no locator/position translation needed here, unlike a prose EPUB's chapter TOC
 * (which this app doesn't attempt AniList sync for at all -- manga-only, see AniListModels.kt).
 *
 * A no-op, by design, for any book with no series or whose series isn't linked+sync-enabled in
 * library-prefs' anilistLinks map -- ReaderActivity constructs and starts one of these
 * unconditionally per book-open rather than checking first.
 */
class MangaAniListProgressTracker(
    private val network: NetworkModule,
    private val libraryPrefsRepository: LibraryPrefsRepository,
    private val aniListRepository: AniListRepository,
    private val scope: CoroutineScope
) {
    private var mediaId: Int? = null

    /** 0-based bookmark page index to parsed chapter number, ascending by page index. */
    private var sortedBookmarks: List<Pair<Int, Int>> = emptyList()
    private var lastPushedChapter: Int = 0
    private var started = false

    fun start(manifestUrl: String, book: Book?) {
        if (started || book == null) return
        started = true

        scope.launch {
            val link = libraryPrefsRepository.getAniListLinks()[book.aniListSeriesKey()]
                ?.takeIf { it.syncEnabled } ?: return@launch

            val response = try {
                network.api.getReadingProgression(manifestUrl)
            } catch (e: Exception) {
                return@launch
            }
            val bookmarks = response.body()?.bookmarks
            if (!response.isSuccessful || bookmarks == null) return@launch

            mediaId = link.mediaId
            sortedBookmarks = bookmarks
                .mapNotNull { bookmark -> bookmark.chapterNumber?.let { bookmark.pageIndex to it } }
                .sortedBy { it.first }
        }
    }

    /** [currentPageOneBased] is [DynamicPageCountState.currentPage] -- see class doc for why no
     *  further translation into a bookmark page index is needed for manga. */
    fun onPageChanged(currentPageOneBased: Int) {
        val id = mediaId ?: return
        if (sortedBookmarks.isEmpty()) return

        val pageIndex = currentPageOneBased - 1
        val chapter = sortedBookmarks.lastOrNull { it.first <= pageIndex }?.second ?: return
        if (chapter <= lastPushedChapter) return
        lastPushedChapter = chapter

        scope.launch {
            aniListRepository.patchEntry(id, JsonObject(mapOf("progress" to JsonPrimitive(chapter))))
        }
    }
}
