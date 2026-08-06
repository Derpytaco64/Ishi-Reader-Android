@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.data.model

import kotlinx.serialization.Serializable
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Matches the website's 7-theme palette (src/preferences/models/theme.ts) exactly, including hex
 * values. Deliberately NOT mapped to Readium's own `Theme` enum (LIGHT/DARK/SEPIA only) -- the
 * website itself never sets that field either, driving every theme including its own "light"/
 * "dark"/"sepia" purely through backgroundColor/textColor preference overrides so all 7 themes go
 * through one uniform mechanism. See [toReadiumColors].
 */
enum class ReaderTheme(val backgroundHex: String, val textHex: String) {
    LIGHT("#FFFFFF", "#121212"),
    DARK("#000000", "#FEFEFE"),
    SEPIA("#e9ddc8", "#000000"),
    PAPER("#faf4e8", "#121212"),
    CONTRAST1("#000000", "#ffff00"),
    CONTRAST2("#181842", "#ffffff"),
    CONTRAST3("#c5e7cd", "#000000")
}

enum class ReaderFontFamily { SERIF, SANS_SERIF, MONOSPACE, OPEN_DYSLEXIC }

enum class ReaderTextAlign { START, JUSTIFY }

enum class ReaderLineHeight(val value: Double) { COMPACT(1.35), NORMAL(1.5), RELAXED(1.75) }

enum class ReaderLayout { PAGINATED, SCROLLED }

/**
 * In-book reading preferences (font/spacing/theme/layout) -- distinct from [AppSettings], which
 * is the app's own chrome theme/accent/shelf config, not anything about how a book's text renders.
 *
 * This is a curated subset of the website's much larger Epub settings panel (its settings.ts has
 * ~20 fields across font/spacing/theme/layout/UI-visibility), restricted to what Readium's Kotlin
 * toolkit actually exposes as native EpubPreferences fields -- porting the rest would mean
 * reimplementing pieces of it outside Readium's preference system entirely. Deliberately left out:
 * font weight (flagged "unstable"/WIP on the site itself), ligatures/noRuby/textNormalization
 * (niche, CJK/typography-specific), column count (Readium 3.1.1's ColumnCount enum only has AUTO
 * in this toolkit version, nothing to choose from), and per-language font curation. All of
 * Readium's nullable fields keep their "use the publication's own styling" meaning here too --
 * there's no single "publisher default" enum value that would work uniformly across every field,
 * so it's spelled as null per-field instead.
 *
 * [pageMargins] uses Readium's native EpubPreferences.pageMargins Double preference rather than
 * the website's raw-CSS-injection approach (the website deliberately avoids Readium's own margin
 * preference because, per its own code comments, submitting it fed Readium's internal "Auto"
 * column-count heuristic and had no visible effect in paginated mode there). The Kotlin toolkit's
 * ColumnCount enum only has AUTO in this version (no manual 1/2-column choice to fight over), and
 * this toolkit exposes no public CSS/JS-injection hook for its WebView-rendered spreads, so the
 * website's workaround isn't portable here -- using the native preference is the only available
 * mechanism. Exact documented bounds for pageMargins could not be confirmed (not in the Kotlin
 * toolkit's prose docs, not in readium-css's own preference docs, and unextractable via
 * strings-on-bytecode since it's a constant-pool double literal); 0.5-4.0 default 1.0 is chosen
 * from the toolkit docs' own usage example (`pageMargins = 1.5` shown as a valid value) and
 * readium-css's general convention of expressing this as a multiplier of the base gutter (the
 * upper bound was doubled from an initial 2.0 cap at user request for more room on large screens).
 */
@Serializable
data class ReaderSettings(
    val theme: ReaderTheme? = null,
    val fontFamily: ReaderFontFamily? = null,
    val fontSize: Double = 1.0,
    val textAlign: ReaderTextAlign? = null,
    val lineHeight: ReaderLineHeight? = null,
    val paragraphSpacing: Double? = null,
    val paragraphIndent: Double? = null,
    val wordSpacing: Double? = null,
    val letterSpacing: Double? = null,
    val hyphens: Boolean? = null,
    val publisherStyles: Boolean = false,
    val layout: ReaderLayout = ReaderLayout.PAGINATED,
    val pageMargins: Double? = null
)

fun ReaderSettings.toEpubPreferences(): EpubPreferences {
    val (backgroundColor, textColor) = theme?.toReadiumColors() ?: (null to null)
    return EpubPreferences(
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = fontFamily?.toReadium(),
        fontSize = fontSize,
        textAlign = textAlign?.toReadium(),
        lineHeight = lineHeight?.value,
        paragraphSpacing = paragraphSpacing,
        paragraphIndent = paragraphIndent,
        wordSpacing = wordSpacing,
        letterSpacing = letterSpacing,
        hyphens = hyphens,
        publisherStyles = publisherStyles,
        scroll = layout == ReaderLayout.SCROLLED,
        pageMargins = pageMargins
    )
}

/** Readium's Color is a Kotlin inline value class wrapping a plain @ColorInt Int, not
 *  androidx.compose.ui.graphics.Color -- construct via android.graphics.Color.parseColor. */
private fun ReaderTheme.toReadiumColors(): Pair<ReadiumColor, ReadiumColor> =
    ReadiumColor(android.graphics.Color.parseColor(backgroundHex)) to
        ReadiumColor(android.graphics.Color.parseColor(textHex))

private fun ReaderFontFamily.toReadium(): FontFamily = when (this) {
    ReaderFontFamily.SERIF -> FontFamily.SERIF
    ReaderFontFamily.SANS_SERIF -> FontFamily.SANS_SERIF
    ReaderFontFamily.MONOSPACE -> FontFamily.MONOSPACE
    ReaderFontFamily.OPEN_DYSLEXIC -> FontFamily.OPEN_DYSLEXIC
}

private fun ReaderTextAlign.toReadium(): TextAlign = when (this) {
    ReaderTextAlign.START -> TextAlign.START
    ReaderTextAlign.JUSTIFY -> TextAlign.JUSTIFY
}
