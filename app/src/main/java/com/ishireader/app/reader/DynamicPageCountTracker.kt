package com.ishireader.app.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** [resourceStartPages] mirrors the website's resourcePages (ExactPageResourceEntry[]) dispatched
 *  out to consumers beyond the reader's own footer -- the TOC panel and annotation rows remap
 *  their coarse positions()-derived page numbers through this so every page number shown anywhere
 *  in the app agrees with the live footer. Keyed by each reading-order resource's href *string*
 *  (Url.toString(), fragment-free) rather than [Url] itself -- callers building this key from a
 *  [org.readium.r2.shared.publication.Link]'s href (e.g. a TOC entry, which can carry a
 *  fragment identifying a sub-heading) already strip the fragment and compare as strings, same
 *  as this class's own reading-order lookups (see [DynamicPageCountTracker.recompute]) do
 *  internally.
 *
 *  [isLoading] is true until [DynamicPageCountTracker.applyExactCounts] has real, swept counts for
 *  the book's current settings/device fingerprint -- consumers should show a loading indicator
 *  rather than any page number while this is true, since there is no meaningful partial number
 *  (see DynamicPageCountTracker's own doc comment for why this app no longer estimates).
 *
 *  [loadingProgress] is the fraction (0f-1f) of reading-order resources [PageCountSweeper] has
 *  measured so far, via [DynamicPageCountTracker.reportSweepProgress] -- null while [isLoading] is
 *  false, or before the first progress report (e.g. still waiting on the cache lookup that
 *  precedes a sweep), so consumers fall back to an indeterminate indicator until it's known. */
data class DynamicPageCountState(
    val isLoading: Boolean = true,
    val loadingProgress: Float? = null,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val resourceStartPages: Map<String, Int> = emptyMap(),
    /** Same keys as [resourceStartPages] -- how many dynamic pages each resource itself spans,
     *  used to place a locator's within-resource progression onto a specific page (see
     *  [dynamicPageForLocator]) rather than just the resource's first page. */
    val resourcePageCounts: Map<String, Int> = emptyMap()
)

/** Mirrors the website's findExactPageForLocator: places [locator] on a specific dynamic page
 *  using its resource's start page plus its within-resource progression, rather than just that
 *  resource's first page -- e.g. a highlight near the end of a long chapter shows near the end of
 *  that chapter's page range, not at its start. Deliberately not the cruder
 *  `totalProgression * totalPages` shortcut, which drifts since chapters vary in length. Null
 *  when the resource hasn't been reached by [DynamicPageCountTracker.applyExactCounts] yet (still
 *  loading), or is outside the reading order. */
fun dynamicPageForLocator(state: DynamicPageCountState, locator: Locator): Int? {
    val href = locator.href.toString().substringBefore("#")
    val start = state.resourceStartPages[href] ?: return null
    val pages = state.resourcePageCounts[href] ?: return start
    if (pages <= 1) return start
    val progression = (locator.locations.progression ?: 0.0).coerceIn(0.0, 1.0)
    val withinIndex = kotlin.math.round(progression * (pages - 1)).toInt()
    return start + withinIndex
}

/**
 * A "dynamic" page count -- unlike the position/totalPositions the reader already shows elsewhere
 * (a fixed ~1024-char content-chunk index from [Publication.positions], unrelated to actual
 * rendered layout), this reflects real on-screen pages under the *current* font/margin/column
 * settings, changing live as those change.
 *
 * The per-resource page counts themselves come from [PageCountSweeper] (a from-scratch, full-book
 * off-screen sweep, cached by ExactPageCountRepository per settings/device fingerprint -- see
 * ReaderSettings.layoutFingerprint) via [applyExactCounts], not estimated. An earlier version of
 * this class estimated unvisited chapters' page counts from a ratio (real/coarse) observed only
 * among chapters the user happened to visit that session -- cheap, but that ratio depended on
 * which chapters were visited and in what order, so the same book at the same settings could
 * report anywhere from ~520 to ~730 total pages session to session. [PageCountSweeper] measures
 * every resource for real instead, so the total is deterministic for a given book + settings +
 * device, matching the exact rendered page count rather than an extrapolated guess.
 *
 * This class's own remaining job is just tracking *which* page you're currently on -- driven live
 * by [EpubNavigatorFragment]'s own (deprecated, but still public) [PaginationListener], since
 * Readium's WebView already computes exactly this (a real scrollWidth/viewportWidth-based page
 * index) for whichever resource is on screen. Display-only: navigation/scrubbing stays on the
 * stable, cheap positions()-based index.
 */
class DynamicPageCountTracker(private val publication: Publication) : EpubNavigatorFragment.PaginationListener {

    private val _state = MutableStateFlow(DynamicPageCountState())
    val state: StateFlow<DynamicPageCountState> = _state.asStateFlow()

    /** Fragment-free href strings -- see [dynamicPageForLocator]'s own matching for why string,
     *  not [org.readium.r2.shared.util.Url] equality: Url.equals is a *strict*, non-normalized
     *  comparison of the raw URI string (see readium-shared's own Url.kt doc comment, which warns
     *  exactly about this and recommends `isEquivalent` instead) -- so a live locator's href
     *  carrying so much as a fragment the reading-order Link itself never had, or a differently
     *  percent-encoded character, silently fails `==` against the matching [readingOrderHrefs]
     *  entry. That's not a crash or a null callers would notice: [recompute]'s loop has no `break`,
     *  so a failed match on the *current* resource just leaves [currentHref] unmatched while
     *  whichever earlier resource happened to match last (if any) wins -- which showed up as the
     *  live footer's page number/percent suddenly jumping backwards mid-session with no
     *  corresponding navigation. Comparing the same normalized string [dynamicPageForLocator] and
     *  [resourceStartPages][DynamicPageCountState.resourceStartPages] already use elsewhere in this
     *  file removes that whole class of mismatch. */
    private val readingOrderHrefs: List<String> =
        publication.readingOrder.map { it.url().toString().substringBefore("#") }
    private var resourcePageCounts: Map<String, Int> = emptyMap()
    private var currentHref: String? = null
    private var currentPageIndex: Int = 0
    private var loadingProgress: Float? = null

    /** Drops any previously-applied exact counts and returns [state] to the loading state -- call
     *  before re-sweeping (or checking the cache) for a new settings/device fingerprint, so
     *  consumers don't keep showing page numbers computed under the *previous* settings while the
     *  new sweep/cache-lookup is in flight. */
    fun markLoading() {
        resourcePageCounts = emptyMap()
        loadingProgress = null
        recompute()
    }

    /** Reports how far [PageCountSweeper] has gotten through the reading order (called from its
     *  onProgress callback), so [state] can drive a determinate progress indicator instead of an
     *  indeterminate spinner. A no-op once loading has finished (or if [markLoading] has since
     *  reset for a newer settings/device fingerprint than this report belongs to -- callers only
     *  bother reporting for a still-current sweep, but a stale report arriving late is harmless
     *  here since it just gets ignored). */
    fun reportSweepProgress(completed: Int, total: Int) {
        if (total <= 0 || resourcePageCounts.isNotEmpty()) return
        loadingProgress = completed.toFloat() / total
        _state.value = _state.value.copy(loadingProgress = loadingProgress)
    }

    /** Applies real, swept per-resource page counts (href string -> page count), ending the
     *  loading state. Missing reading-order resources (shouldn't normally happen -- every resource
     *  is swept) fall back to 1 page rather than crashing on a lookup miss. */
    fun applyExactCounts(countsByHref: Map<String, Int>) {
        resourcePageCounts = readingOrderHrefs.associateWith { href -> countsByHref[href] ?: 1 }
        recompute()
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        currentHref = locator.href.toString().substringBefore("#")
        currentPageIndex = pageIndex
        recompute()
    }

    private fun recompute() {
        if (resourcePageCounts.isEmpty()) {
            _state.value = DynamicPageCountState(isLoading = true, loadingProgress = loadingProgress)
            return
        }

        val href = currentHref
        var pagesBefore = 0
        var currentResourceStart: Int? = null
        val startPages = mutableMapOf<String, Int>()
        val pageCounts = mutableMapOf<String, Int>()
        for (resourceHref in readingOrderHrefs) {
            startPages[resourceHref] = pagesBefore + 1
            if (resourceHref == href) currentResourceStart = pagesBefore
            val pages = resourcePageCounts[resourceHref] ?: 1
            pageCounts[resourceHref] = pages
            pagesBefore += pages
        }

        _state.value = DynamicPageCountState(
            isLoading = false,
            currentPage = currentResourceStart?.let { it + currentPageIndex + 1 },
            totalPages = pagesBefore,
            resourceStartPages = startPages,
            resourcePageCounts = pageCounts
        )
    }
}
