package com.ishireader.app.reader

import android.util.Log
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
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
        // goForward/goBackward are semantic (reading-progression-relative, not screen-relative --
        // see OverflowableNavigator.goForward's own doc), so for RTL content (manga) "next" is
        // visually to the left: flip which screen zone maps to which call, rather than always
        // sending left-tap to goBackward.
        val isRtl = overflow?.overflow?.value?.readingProgression == ReadingProgression.RTL
        // CLAUDE-ADDED: temporary diagnostic logging for the RTL portrait-vs-landscape tap-direction
        // bug -- see EpubNavigatorFragment.goToNextResource/goToPreviousResource and R2RTLViewPager
        // for the matching log lines. Safe/cheap to leave in briefly; strip once the bug is found.
        val zone = when {
            fraction < EDGE_ZONE_FRACTION -> "LEFT_EDGE"
            fraction > 1 - EDGE_ZONE_FRACTION -> "RIGHT_EDGE"
            else -> "MIDDLE"
        }
        Log.d(
            "IshiRTLDebug",
            "ChromeTapInputListener.onTap: point.x=${event.point.x} width=$width fraction=$fraction " +
                "zone=$zone isRtl=$isRtl readingProgression=${overflow?.overflow?.value?.readingProgression}"
        )
        when {
            fraction < EDGE_ZONE_FRACTION -> if (isRtl) overflow?.goForward(animated = true) else overflow?.goBackward(animated = true)
            fraction > 1 - EDGE_ZONE_FRACTION -> if (isRtl) overflow?.goBackward(animated = true) else overflow?.goForward(animated = true)
            else -> onToggleChrome()
        }
        return true
    }
}
