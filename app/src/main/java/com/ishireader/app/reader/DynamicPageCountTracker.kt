package com.ishireader.app.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

data class DynamicPageCountState(val currentPage: Int? = null, val totalPages: Int? = null)

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
        val href = currentHref ?: return

        var pagesBefore = 0
        for (resourceHref in readingOrderHrefs) {
            if (resourceHref == href) break
            pagesBefore += estimatedPages(resourceHref)
        }

        val total = readingOrderHrefs.sumOf { estimatedPages(it) }
        _state.value = DynamicPageCountState(currentPage = pagesBefore + currentPageIndex + 1, totalPages = total)
    }
}
