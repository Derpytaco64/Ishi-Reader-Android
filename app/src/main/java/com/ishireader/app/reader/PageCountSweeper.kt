@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.reader

import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import kotlin.coroutines.resume

/**
 * Measures a real, exact page count for every resource in [publication]'s reading order -- not
 * just chapters the user happens to visit -- by driving a second, hidden (but attached and
 * laid-out) [EpubNavigatorFragment] through the whole book and capturing Readium's own
 * scrollWidth/viewportWidth pagination measurement ([EpubNavigatorFragment.PaginationListener])
 * for each resource in turn. There's no headless/off-screen measurement API in this Readium
 * version (readium-navigator 3.1.1) -- the internals that turn preferences into CSS
 * (HtmlInjector/WebViewServer) are `internal` to org.readium.r2.navigator.epub -- so reusing the
 * real fragment/WebView stack via its public factory is the only way to reproduce Readium's exact
 * pagination math without reflecting into internal classes. The host container is expected to be
 * `View.INVISIBLE` (not `GONE`, which would skip layout/measurement) and sized to match the real
 * navigator's own content area, since the measured viewport size is itself a layout input (see
 * ReaderSettings.layoutFingerprint).
 */
class PageCountSweeper(
    private val publication: Publication,
    private val preferences: EpubPreferences
) {

    /** Runs the sweep, hosting a temporary fragment in [containerId] and removing it when done.
     *  Each resource gets up to [RESOURCE_TIMEOUT_MS] to report a page count before falling back
     *  to 1 page, so one stuck resource can't hang the whole sweep. Must be called from the main
     *  thread (fragment transactions require it). [onProgress] is called after each resource
     *  finishes (or times out) with (resources completed so far, total resources), so callers can
     *  show a determinate progress indicator instead of an indeterminate one. */
    suspend fun sweep(
        fragmentManager: FragmentManager,
        containerId: Int,
        classLoader: ClassLoader,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, Int> {
        val readingOrder = publication.readingOrder
        if (readingOrder.isEmpty()) return emptyMap()
        val total = readingOrder.size

        // Fragment-free href string, not org.readium.r2.shared.util.Url -- Url.equals is a strict,
        // non-normalized comparison of the raw URI string (see its own doc comment, which warns
        // about exactly this and recommends `isEquivalent` instead), so a live pagination locator's
        // href carrying so much as a fragment the reading-order Link itself never had -- or any
        // other difference in exact string form -- silently fails `==` against [awaitingHref] below.
        // That's not a transient race: it fails the same way for the same resource on every sweep,
        // so the affected resource permanently times out and falls back to 1 page (see the
        // withTimeoutOrNull calls below) no matter how many times the book is re-swept. Same fix as
        // DynamicPageCountTracker's own onPageChanged/recompute matching.
        var awaitingHref: String? = null
        var continuation: CancellableContinuation<Int>? = null

        val listener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: org.readium.r2.shared.publication.Locator) {
                if (totalPages > 0 && locator.href.toString().substringBefore("#") == awaitingHref) {
                    continuation?.takeIf { it.isActive }?.resume(totalPages)
                    continuation = null
                }
            }
        }

        val navigatorFactory = EpubNavigatorFactory(publication = publication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = null,
            initialPreferences = preferences,
            paginationListener = listener,
            configuration = EpubNavigatorFragment.Configuration()
        )
        val fragment = fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment

        // Waits for the next onPageChanged matching [awaitingHref], returning its totalPages (or
        // null on timeout). [trigger] is run *after* the continuation is registered, mirroring the
        // original single-measurement version below -- callers just supply what kicks off that
        // navigation (the initial fragment attach for the very first resource, fragment.go() for
        // every other measurement).
        suspend fun awaitPageCount(trigger: () -> Unit): Int? =
            withTimeoutOrNull(RESOURCE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    continuation = cont
                    trigger()
                }
            }

        val results = mutableMapOf<String, Int>()
        try {
            // No explicit go() for the very first resource -- a null initialLocator already makes
            // the fragment navigate to the reading order's first resource itself on creation (see
            // EpubNavigatorFragment.onViewCreated), and the continuation is registered *before*
            // the fragment is attached, so whichever onPageChanged fires first (from that implicit
            // navigation) is guaranteed to be the one this catches -- calling go() here too would
            // race that implicit navigation instead of replacing it.
            val firstLink = readingOrder.first()
            val firstHref = firstLink.url()
            awaitingHref = firstHref.toString().substringBefore("#")
            var best = awaitPageCount {
                fragmentManager.beginTransaction().add(containerId, fragment, SWEEP_FRAGMENT_TAG).commitNow()
            } ?: 0

            // Readium's R2WebView.numPages (what onPageChanged reports) is computed live from the
            // WebView's *current* scrollWidth at the moment notifyCurrentLocation happens to poll
            // it -- see R2WebView.numPages's getter and EpubNavigatorFragment.onContentReady, which
            // resolves off a postVisualStateCallback the instant a frame is painted, not once every
            // <img> in the resource has finished decoding and reflowed the page. An image without
            // explicit width/height reserved in its markup grows the page *after* that first
            // measurement lands, but nothing re-triggers notifyCurrentLocation during a headless
            // sweep (no user scroll ever happens) to pick up the corrected, larger count -- so an
            // image-containing resource's page count silently sticks at whatever it measured before
            // its images finished loading, every single sweep. Re-issuing go() to the same locator
            // after a short settle delay forces exactly that re-measurement (go() -> loadLocator ->
            // onProgressionChanged -> notifyCurrentLocation, even when already on this resource --
            // see EpubNavigatorFragment.go()'s unconditional loadLocatorAt call), so this takes the
            // larger of the two readings rather than trusting whichever fired first.
            publication.locatorFromLink(firstLink)?.let { locator ->
                delay(IMAGE_SETTLE_DELAY_MS)
                best = maxOf(best, awaitPageCount { fragment.go(locator, animated = false) } ?: 0)
            }
            results[firstHref.toString()] = best.takeIf { it > 0 } ?: 1
            var processed = 1
            onProgress(processed, total)

            for (link in readingOrder.drop(1)) {
                val locator = publication.locatorFromLink(link)
                if (locator == null) {
                    processed++
                    onProgress(processed, total)
                    continue
                }
                val href = link.url()
                awaitingHref = href.toString().substringBefore("#")
                val firstPass = awaitPageCount { fragment.go(locator, animated = false) } ?: 0
                delay(IMAGE_SETTLE_DELAY_MS)
                val settledPass = awaitPageCount { fragment.go(locator, animated = false) } ?: 0
                results[href.toString()] = maxOf(firstPass, settledPass).takeIf { it > 0 } ?: 1
                processed++
                onProgress(processed, total)
            }
        } finally {
            continuation = null
            // Guarded: if the sweep was cancelled because the host Activity is being torn down
            // (see ReaderActivity.pageCountSweepJob), this cleanup can end up running after the
            // FragmentManager itself has already saved state or been destroyed, and a plain
            // commitNow() would throw in either case -- there's nothing left worth crashing over
            // at that point, the hidden fragment is going away with the rest of the Activity
            // regardless of whether this transaction actually runs.
            runCatching {
                fragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            }
        }
        return results
    }

    companion object {
        private const val SWEEP_FRAGMENT_TAG = "page_count_sweep_fragment"
        private const val RESOURCE_TIMEOUT_MS = 8_000L

        /** How long to wait after a resource's first page-count measurement before forcing a
         *  second one (see the sweep loop) -- long enough for a locally-bundled EPUB image to
         *  finish decoding and reflow the page, short enough not to badly bloat total sweep time
         *  across a few hundred resources. */
        private const val IMAGE_SETTLE_DELAY_MS = 400L
    }
}
