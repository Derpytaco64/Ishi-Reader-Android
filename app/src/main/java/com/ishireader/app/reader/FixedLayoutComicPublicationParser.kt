package com.ishireader.app.reader

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.logging.WarningLogger
import org.readium.r2.streamer.parser.PublicationParser

/**
 * Readium's [org.readium.r2.streamer.parser.image.ImageParser] (which parses CBZ/manga) never sets
 * `metadata.presentation.layout` on the [Publication.Builder] it produces -- confirmed by reading
 * its 3.1.1 source (`finalizeParsing` only sets `conformsTo`/`localizedTitle`). Readium's own
 * EPUB navigator (`EpubNavigatorFragment.resetResourcePagerAdapter`) treats a null layout as
 * REFLOWABLE, not FIXED -- so every comic page was rendering through the plain reflowable
 * `R2EpubPageFragment`/raw-WebView path (loading each page image directly via `webView.loadUrl`,
 * with no Readium JS injected into that raw image response) instead of the purpose-built,
 * single-image `R2FXLPageFragment`/`R2FXLLayout` fixed-layout pager. That explains three
 * previously-unexplained comic-reader bugs at once: pinch-zoom/double-tap being Android's native
 * WebView zoom (not any Readium/app code at all, so nothing in readium-navigator could ever fix
 * it), and the chrome toggle only working in the title/page-count strip (the only place a tap
 * lands outside the WebView's own inset bounds, where the fragment root's plain OnClickListener
 * fallback -- meant only for "before the page is ready" -- is the sole thing still listening).
 *
 * This decorates whatever [PublicationParser] the app already uses and, only for DIVINA
 * (comic/manga) publications, stamps `presentation.layout = "fixed"` into the manifest's
 * `otherMetadata` before the [Publication.Builder] is finalized -- the same JSON shape
 * `Presentation.toJSON()`/`Metadata.presentation`'s `fromJSON` round-trip through, per
 * `org.readium.r2.shared.publication.presentation.Presentation`. Mutating `builder.manifest`
 * in place (it's a `var`) rather than rebuilding a whole new [Publication] afterward keeps
 * whatever `container`/`servicesBuilder` the underlying parser already configured (e.g.
 * ImageParser's per-resource [org.readium.r2.shared.publication.services.PositionsService])
 * untouched, so page-count/position tracking for comics isn't put at risk by this.
 */
class FixedLayoutComicPublicationParser(
    private val delegate: PublicationParser,
) : PublicationParser {

    override suspend fun parse(
        asset: Asset,
        warnings: WarningLogger?,
    ): Try<Publication.Builder, PublicationParser.ParseError> =
        delegate.parse(asset, warnings).map { builder ->
            if (builder.manifest.metadata.conformsTo.contains(Publication.Profile.DIVINA)) {
                builder.manifest = builder.manifest.copy(
                    metadata = builder.manifest.metadata.copy(
                        otherMetadata = builder.manifest.metadata.otherMetadata +
                            ("presentation" to mapOf("layout" to "fixed"))
                    )
                )
            }
            builder
        }
}
