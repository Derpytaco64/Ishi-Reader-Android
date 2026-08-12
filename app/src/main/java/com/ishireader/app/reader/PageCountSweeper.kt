@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.reader

import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
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
     *  thread (fragment transactions require it). */
    suspend fun sweep(
        fragmentManager: FragmentManager,
        containerId: Int,
        classLoader: ClassLoader
    ): Map<String, Int> {
        val readingOrder = publication.readingOrder
        if (readingOrder.isEmpty()) return emptyMap()

        var awaitingHref: Url? = null
        var continuation: CancellableContinuation<Int>? = null

        val listener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: org.readium.r2.shared.publication.Locator) {
                if (totalPages > 0 && locator.href == awaitingHref) {
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

        val results = mutableMapOf<String, Int>()
        try {
            // No explicit go() for the very first resource -- a null initialLocator already makes
            // the fragment navigate to the reading order's first resource itself on creation (see
            // EpubNavigatorFragment.onViewCreated), and the continuation is registered *before*
            // the fragment is attached, so whichever onPageChanged fires first (from that implicit
            // navigation) is guaranteed to be the one this catches -- calling go() here too would
            // race that implicit navigation instead of replacing it.
            val firstHref = readingOrder.first().url()
            awaitingHref = firstHref
            val firstPages = withTimeoutOrNull(RESOURCE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    continuation = cont
                    fragmentManager.beginTransaction().add(containerId, fragment, SWEEP_FRAGMENT_TAG).commitNow()
                }
            }
            results[firstHref.toString()] = firstPages ?: 1

            for (link in readingOrder.drop(1)) {
                val locator = publication.locatorFromLink(link) ?: continue
                val href = link.url()
                awaitingHref = href
                val pages = withTimeoutOrNull(RESOURCE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        continuation = cont
                        fragment.go(locator, animated = false)
                    }
                }
                results[href.toString()] = pages ?: 1
            }
        } finally {
            continuation = null
            fragmentManager.beginTransaction().remove(fragment).commitNow()
        }
        return results
    }

    companion object {
        private const val SWEEP_FRAGMENT_TAG = "page_count_sweep_fragment"
        private const val RESOURCE_TIMEOUT_MS = 8_000L
    }
}
