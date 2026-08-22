package com.ishireader.app.reader

import android.util.Log
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CBZPageBookmark
import com.ishireader.app.data.model.aniListSeriesKey
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
 * unconditionally per book-open rather than checking first. [bookmarks] is passed in rather than
 * fetched here -- ReaderActivity already fetches ApiService.getReadingProgression once per comic
 * open (for the TOC, see buildComicToc), so this class would otherwise be making the exact same
 * network call a second time.
 */
class MangaAniListProgressTracker(
    private val libraryPrefsRepository: LibraryPrefsRepository,
    private val aniListRepository: AniListRepository,
    private val scope: CoroutineScope
) {
    private var mediaId: Int? = null

    /** 0-based bookmark page index to parsed chapter number, ascending by page index. */
    private var sortedBookmarks: List<Pair<Int, Int>> = emptyList()
    private var lastPushedChapter: Int = 0
    private var started = false

    fun start(book: Book?, bookmarks: List<CBZPageBookmark>) {
        Log.d("AniListDbg", "start() called: started=$started book=${book?.title} bookmarks=${bookmarks.size}")
        if (started || book == null) return
        started = true

        scope.launch {
            val seriesKey = book.aniListSeriesKey()
            val links = libraryPrefsRepository.getAniListLinks()
            Log.d("AniListDbg", "seriesKey=$seriesKey links=$links")
            val link = links[seriesKey]?.takeIf { it.syncEnabled } ?: run {
                Log.d("AniListDbg", "no matching sync-enabled link, bailing")
                return@launch
            }

            mediaId = link.mediaId
            sortedBookmarks = bookmarks
                .mapNotNull { bookmark -> bookmark.chapterNumber?.let { bookmark.pageIndex to it } }
                .sortedBy { it.first }
            Log.d("AniListDbg", "resolved mediaId=$mediaId sortedBookmarks=$sortedBookmarks")
        }
    }

    /** [currentPageOneBased] is [DynamicPageCountState.currentPage] -- see class doc for why no
     *  further translation into a bookmark page index is needed for manga. [totalPages] is
     *  [DynamicPageCountState.totalPages].
     *
     *  Pushes the last *completed* chapter, not the one currently on screen: landing on a new
     *  chapter's first page only proves the previous chapter's last page was read, so that's what
     *  gets pushed. The chapter the reader is sitting in only counts once the very last page of the
     *  book is reached, since there's no further bookmark transition to catch its own completion. */
    fun onPageChanged(currentPageOneBased: Int, totalPages: Int?) {
        Log.d("AniListDbg", "onPageChanged: page=$currentPageOneBased total=$totalPages mediaId=$mediaId bookmarks=${sortedBookmarks.size}")
        val id = mediaId ?: return
        if (sortedBookmarks.isEmpty()) return

        val pageIndex = currentPageOneBased - 1
        val currentChapterIdx = sortedBookmarks.indexOfLast { it.first <= pageIndex }
        if (currentChapterIdx < 0) return

        val bookFinished = totalPages != null && currentPageOneBased >= totalPages
        val completedChapter = when {
            bookFinished -> sortedBookmarks[currentChapterIdx].second
            currentChapterIdx > 0 -> sortedBookmarks[currentChapterIdx - 1].second
            else -> null
        } ?: return
        Log.d("AniListDbg", "completedChapter=$completedChapter lastPushed=$lastPushedChapter")
        if (completedChapter <= lastPushedChapter) return
        lastPushedChapter = completedChapter
        Log.d("AniListDbg", "PUSHING progress=$completedChapter for mediaId=$id")

        scope.launch {
            // clampProgress = true -- this push is inferred from page position, not a deliberate
            // user edit, so it must never regress AniList's progress below what's already known
            // (see AniListRepository's class doc). A manual edit from the tracking sheet skips this.
            aniListRepository.patchEntry(id, JsonObject(mapOf("progress" to JsonPrimitive(completedChapter))), clampProgress = true)
        }
    }
}
