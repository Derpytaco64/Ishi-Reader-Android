package com.ishireader.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

/** A tapped image, already decoded to a displayable [Bitmap] -- read straight from the EPUB's own
 *  local resource bytes at full resolution, not whatever (possibly downscaled) rendering the
 *  WebView happened to be showing on screen. */
data class TappedImage(val bitmap: Bitmap, val alt: String)

/**
 * Detects taps on `<img>`/SVG `<image>` elements in the currently visible page and reports them
 * via [onImageTapped] instead of letting the normal page-turn/chrome-toggle behavior in [fallback]
 * handle them -- mirrors the website's StatefulReader, which checks for an image tap before its
 * normal tap handler (see getClickedImage.ts).
 *
 * Readium Kotlin's [TapEvent] only carries a screen point, not which element was tapped (unlike
 * the website's Readium JS build, which reports the tapped element inline with every tap/click
 * event) -- so this queries the DOM itself via [EpubNavigatorFragment.evaluateJavascript]. That's
 * necessarily async, so this listener always claims the tap (returns true) and, once the DOM query
 * resolves, either shows the image or replays the tap through [fallback] -- avoiding a flash of
 * both the image overlay and a chrome-toggle/page-turn firing for the same tap.
 */
@OptIn(ExperimentalReadiumApi::class)
class ImageTapInputListener(
    private val fragment: EpubNavigatorFragment,
    private val publication: Publication,
    private val scope: CoroutineScope,
    private val fallback: InputListener,
    private val onImageTapped: (TappedImage) -> Unit
) : InputListener {

    override fun onTap(event: TapEvent): Boolean {
        scope.launch {
            val image = runCatching { detectTappedImage(event) }.getOrNull()
            if (image != null) onImageTapped(image) else fallback.onTap(event)
        }
        return true
    }

    /** [TapEvent.point] is in device pixels (the WebView bridge scales clientX/clientY by
     *  devicePixelRatio before handing it to Kotlin), but elementFromPoint expects CSS pixels --
     *  divide back out by the same factor to land on the actual tapped element. */
    private suspend fun detectTappedImage(event: TapEvent): TappedImage? {
        val script = """
            (function() {
                var el = document.elementFromPoint(${event.point.x} / window.devicePixelRatio, ${event.point.y} / window.devicePixelRatio);
                if (!el) return null;
                var img = el.closest('img, image');
                if (!img) return null;
                var raw = img.tagName.toLowerCase() === 'image'
                    ? (img.getAttribute('xlink:href') || img.getAttributeNS('http://www.w3.org/1999/xlink', 'href') || img.getAttribute('href'))
                    : (img.currentSrc || img.src);
                if (!raw) return null;
                // Calibre-style cover pages wrap the cover in <svg><image xlink:href="..."/> whose
                // href attribute is left relative (unlike <img>.currentSrc/src, which the browser
                // always resolves to an absolute URL already) -- resolve it against the document's
                // own base URI so it lands in the same absolute-URL shape either branch above hands
                // back, same as the website's getClickedImage.ts does for the SVG case.
                var src = new URL(raw, document.baseURI).toString();
                return JSON.stringify({ src: src, alt: img.getAttribute('alt') || img.getAttribute('aria-label') || '' });
            })();
        """.trimIndent()

        val raw = fragment.evaluateJavascript(script) ?: return null
        // WebView.evaluateJavascript's callback value is itself a JSON-encoded representation of
        // the JS result -- a string return comes back JSON-quoted (e.g. "\"{...}\""), so unwrap it
        // once with JSONTokener before parsing the inner JSON object.
        val jsonString = (JSONTokener(raw).nextValue() as? String)?.takeIf { it.isNotBlank() } ?: return null
        val json = JSONObject(jsonString)
        val src = json.optString("src").takeIf { it.isNotBlank() } ?: return null
        val alt = json.optString("alt")

        // Every EPUB resource the navigator's WebView renders is served from this fixed internal
        // base (WebViewServer.publicationBaseHref, not public) -- relativizing against it turns an
        // absolute currentSrc/xlink:href back into the resource's real path within the publication.
        val base = AbsoluteUrl(PUBLICATION_BASE_HREF) ?: return null
        val absolute = AbsoluteUrl(src) ?: return null
        val relative = base.relativize(absolute)
        val resource = publication.get(relative) ?: return null
        val bytes = resource.read().getOrNull() ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return null
        return TappedImage(bitmap, alt)
    }

    private companion object {
        const val PUBLICATION_BASE_HREF = "https://readium/publication/"
    }
}
