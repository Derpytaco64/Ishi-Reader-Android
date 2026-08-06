package com.ishireader.app.reader

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Moon+ Reader-style tap zones: left/right thirds turn pages, the middle third toggles the reader
 * chrome (settings/timer/annotations toolbar, plus the system bars -- see ReaderActivity). Readium
 * doesn't turn pages on tap by itself -- InputListener.onTap's own doc says it only fires when
 * "nothing handled the event internally" (link-following, decoration taps), so tap-to-turn and
 * tap-to-reveal are both entirely app-provided here, same as most Readium-based reference apps.
 */
@OptIn(ExperimentalReadiumApi::class)
class ChromeTapInputListener(
    private val navigator: VisualNavigator,
    private val onToggleChrome: () -> Unit
) : InputListener {

    private companion object {
        const val EDGE_ZONE_FRACTION = 0.3
    }

    override fun onTap(event: TapEvent): Boolean {
        val width = navigator.publicationView.width
        if (width <= 0) {
            onToggleChrome()
            return true
        }

        val fraction = event.point.x / width
        val overflow = navigator as? OverflowableNavigator
        when {
            fraction < EDGE_ZONE_FRACTION -> overflow?.goBackward(animated = true)
            fraction > 1 - EDGE_ZONE_FRACTION -> overflow?.goForward(animated = true)
            else -> onToggleChrome()
        }
        return true
    }
}
