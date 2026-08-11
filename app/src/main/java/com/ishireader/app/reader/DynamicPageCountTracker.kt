package com.ishireader.app.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

/** [resourceStartPages] mirrors the website's resourcePages (ExactPageResourceEntry[]) dispatched
 *  out to consumers beyond the reader's own footer -- the TOC panel and annotation rows remap
 *  their coarse positions()-derived page numbers through this so every page number shown anywhere
 *  in the app agrees with the live footer. Keyed by each reading-order resource's href *string*
 *  (Url.toString(), fragment-free) rather than [Url] itself -- callers building this key from a
 *  [org.readium.r2.shared.publication.Link]'s href (e.g. a TOC entry, which can carry a
 *  fragment identifying a sub-heading) already strip the fragment and compare as strings, same
 *  as this class's own [estimatedPages]/reading-order lookups do internally. */
data class DynamicPageCountState(
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
 *  when the resource hasn't been reached by [DynamicPageCountTracker.onPositionsLoaded] (e.g. an
 *  href outside the reading order). */
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
 * settings, changing live as those change. Mirrors the intent of the website's useExactPageCount
 * (a from-scratch off-screen full-book layout scan -- explicitly a "spike... not a finished
 * feature" per its own doc comment, since a full re-render of every resource off-screen is
 * expensive and edge-case-heavy).
 *
 * This gets the same kind of number far more cheaply by reusing [EpubNavigatorFragment]'s own
 * (deprecated, but still public) [EpubNavigatorFragment.PaginationListener] -- Readium's own
 * WebView already computes exactly this (a real scrollWidth/viewportWidth-based page count) for
 * whichever resource is currently on screen, it's just not otherwise exposed. Resources not yet
 * visited this session have no real count, so their page count is estimated from their share of
 * the (already loaded elsewhere, for the scrub slider) coarse positions() list, scaled by the
 * average real/coarse ratio observed so far -- exact for the resource currently being read, and
 * for the book-wide total once most of it has been visited; before that, still a reasonable
 * estimate. Display-only: navigation/scrubbing stays on the stable, cheap positions()-based index.
 */
class DynamicPageCountTracker(private val publication: Publication) : EpubNavigatorFragment.PaginationListener {

    private val _state = MutableStateFlow(DynamicPageCountState())
    val state: StateFlow<DynamicPageCountState> = _state.asStateFlow()

    private var readingOrderHrefs: List<Url> = emptyList()
    private var coarseCountByHref: Map<Url, Int> = emptyMap()
    private val realPagesByHref = mutableMapOf<Url, Int>()
    private var currentHref: Url? = null
    private var currentPageIndex: Int = 0

    /** Seeds the coarse per-resource estimate -- call once [Publication.positions] resolves (it's
     *  already loaded elsewhere in ReaderActivity for the scrub slider/position indicator, so this
     *  is free -- no extra positions() computation here). */
    fun onPositionsLoaded(positions: List<Locator>) {
        readingOrderHrefs = publication.readingOrder.map { it.url() }
        coarseCountByHref = positions.groupingBy { it.href }.eachCount()
        recompute()
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        currentHref = locator.href
        currentPageIndex = pageIndex
        if (totalPages > 0) realPagesByHref[locator.href] = totalPages
        recompute()
    }

    private fun estimatedPages(href: Url): Int {
        realPagesByHref[href]?.let { return it }
        val coarse = coarseCountByHref[href] ?: 1
        val estimate = coarse * averageRealToCoarseRatio()
        return kotlin.math.max(1, kotlin.math.round(estimate).toInt())
    }

    /** How real page counts have tended to compare to the coarse positions()-derived guess among
     *  resources actually visited so far -- 1.0 (i.e. just use the coarse count) until at least
     *  one resource has a real measurement. */
    private fun averageRealToCoarseRatio(): Double {
        val ratios = realPagesByHref.entries.mapNotNull { (href, real) ->
            coarseCountByHref[href]?.takeIf { it > 0 }?.let { real.toDouble() / it }
        }
        return if (ratios.isEmpty()) 1.0 else ratios.average()
    }

    private fun recompute() {
        if (readingOrderHrefs.isEmpty()) return
        val href = currentHref

        var pagesBefore = 0
        var currentResourceStart: Int? = null
        val startPages = mutableMapOf<String, Int>()
        val pageCounts = mutableMapOf<String, Int>()
        for (resourceHref in readingOrderHrefs) {
            val key = resourceHref.toString()
            startPages[key] = pagesBefore + 1
            if (resourceHref == href) currentResourceStart = pagesBefore
            val pages = estimatedPages(resourceHref)
            pageCounts[key] = pages
            pagesBefore += pages
        }

        _state.value = DynamicPageCountState(
            currentPage = currentResourceStart?.let { it + currentPageIndex + 1 },
            totalPages = pagesBefore,
            resourceStartPages = startPages,
            resourcePageCounts = pageCounts
        )
    }
}
