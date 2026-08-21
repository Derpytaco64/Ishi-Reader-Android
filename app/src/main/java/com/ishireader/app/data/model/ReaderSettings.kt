@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.data.model

import kotlinx.serialization.Serializable
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression as ReadiumReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * The first 7 entries match the website's theme palette (src/preferences/models/theme.ts)
 * exactly, including hex values. Deliberately NOT mapped to Readium's own `Theme` enum
 * (LIGHT/DARK/SEPIA only) -- the website itself never sets that field either, driving every theme
 * including its own "light"/"dark"/"sepia" purely through backgroundColor/textColor preference
 * overrides, which is also the only per-theme mechanism Readium's EpubPreferences actually
 * exposes (theme.ts's own link/hover/select/etc. roles are never submitted anywhere, web or
 * Android). See [ReaderSettings.effectiveBackgroundHex]/[ReaderSettings.effectiveTextHex]. CUSTOM
 * is Android-only, with no website counterpart.
 */
enum class ReaderTheme(val backgroundHex: String, val textHex: String) {
    LIGHT("#FFFFFF", "#121212"),
    DARK("#000000", "#FEFEFE"),
    SEPIA("#e9ddc8", "#000000"),
    PAPER("#faf4e8", "#121212"),
    CONTRAST1("#000000", "#ffff00"),
    CONTRAST2("#181842", "#ffffff"),
    CONTRAST3("#c5e7cd", "#000000"),

    /** User-picked background/text pair (see ReaderSettings.customBackgroundHex/customTextHex) --
     *  this entry's own hex values are never actually rendered, just a placeholder so [label]/enum
     *  iteration have something to show before the user has picked a color; [ReaderSettings.
     *  effectiveBackgroundHex]/[effectiveTextHex] always resolve CUSTOM through those two fields
     *  instead of reading backgroundHex/textHex directly. */
    CUSTOM("#FFFFFF", "#121212")
}

enum class ReaderFontFamily { SERIF, SANS_SERIF, MONOSPACE, OPEN_DYSLEXIC }

enum class ReaderTextAlign { START, JUSTIFY }

enum class ReaderLineHeight(val value: Double) { COMPACT(1.35), NORMAL(1.5), RELAXED(1.75) }

enum class ReaderLayout { PAGINATED, SCROLLED }

/** Comic-only page-turn direction (see [ReaderSettings.comicReadingDirection]/[ReaderSettingsSheet]'s
 *  Reading Direction row, gated on isComic same as the Theme row). [AUTO] follows whatever the
 *  server detected from the CBZ's own ComicInfo.xml `<Manga>` tag (`YesAndRightToLeft` -> rtl, see
 *  Ishi-Read's comicInfo.ts) -- Android's own local CBZ parser never reads ComicInfo.xml, so that
 *  detection only exists server-side, fetched via ApiService.getReadingProgression and threaded
 *  through [ReaderSettings.toEpubPreferences]'s comicServerReadingProgression parameter. [LTR]/[RTL]
 *  are an explicit user override, e.g. for a CBZ with no ComicInfo.xml (or one incorrectly tagged)
 *  that the reader still wants to read manga-style. */
enum class ComicReadingDirection { AUTO, LTR, RTL }

/** What the bottom position indicator shows, if anything. [PAGE] mirrors Readium's own
 *  Locator.Locations.position (1-based index into Publication.positions()); [PERCENT] mirrors
 *  the exact rounding the library screen uses for its progress percentage
 *  (see percentFromLocator); [PAGE_PERCENT] combines both, matching the "8 of 100 (8%)" example
 *  given for it -- a whole-number percent in parentheses, distinct from [PERCENT]'s own
 *  one-decimal format used everywhere else in the app. */
enum class PositionDisplayMode { NONE, PAGE, PERCENT, PAGE_PERCENT }

enum class PositionDisplayAlignment { LEFT, CENTER, RIGHT }

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
 *
 * [verticalMargin] has no Readium counterpart at all -- EpubPreferences.pageMargins is
 * documented as "Factor applied to horizontal margins" only, and the toolkit exposes no vertical
 * equivalent or CSS-injection hook to fake one. It's applied outside Readium entirely, as plain
 * View padding around the navigator's container in ReaderActivity, in dp rather than a factor
 * since there's no toolkit-side base value to multiply.
 *
 * [showChapterTitle]/[positionDisplayMode]/[positionDisplayAlignment] drive the reading-frame
 * overlays (current TOC chapter title up top, page/percent indicator at the bottom) -- both are
 * app-level Compose UI, not Readium preferences, computed from the live Locator/Publication in
 * ReaderActivity rather than submitted to the navigator.
 */
@Serializable
data class ReaderSettings(
    val theme: ReaderTheme? = null,
    /** Only meaningful when [theme] is [ReaderTheme.CUSTOM] -- "#RRGGBB" hex strings, same
     *  convention as [com.ishireader.app.data.model.AppSettings.accentColor], picked via
     *  ColorWheelPicker. Null (before the user has picked yet) falls back to CUSTOM's own
     *  placeholder hex -- see [effectiveBackgroundHex]/[effectiveTextHex]. */
    val customBackgroundHex: String? = null,
    val customTextHex: String? = null,
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
    val comicReadingDirection: ComicReadingDirection = ComicReadingDirection.AUTO,
    val pageMargins: Double? = null,
    val verticalMargin: Double = 0.0,
    val showChapterTitle: Boolean = false,
    val positionDisplayMode: PositionDisplayMode = PositionDisplayMode.NONE,
    val positionDisplayAlignment: PositionDisplayAlignment = PositionDisplayAlignment.CENTER,
    /** When on, hardware volume-down/volume-up turn pages forward/backward instead of adjusting
     *  the device's media volume -- handled entirely in ReaderActivity's dispatchKeyEvent, not a
     *  Readium/EpubPreferences concept. */
    val volumeButtonsPageTurn: Boolean = false,
    /** A flattened ComponentName (`ComponentName.flattenToString()`) of the activity to send
     *  selected text to for lookup -- e.g. a dictionary or translator app that supports Android's
     *  standard ACTION_PROCESS_TEXT "Process text" intent. Null means no "Dictionary" action is
     *  offered on the selection toolbar (see AnnotationSelectionActionModeCallback). Stored as a
     *  plain string rather than a ComponentName since this class is @Serializable and
     *  ComponentName isn't. */
    val dictionaryAppComponent: String? = null
)

/** Resolves [ReaderSettings.theme] to the background hex actually rendered -- routes
 *  [ReaderTheme.CUSTOM] through [ReaderSettings.customBackgroundHex] instead of that enum entry's
 *  own placeholder value. Null means "Auto" (no override, let Readium/the publisher decide), same
 *  as a null theme always has. */
fun ReaderSettings.effectiveBackgroundHex(): String? = when (theme) {
    null -> null
    ReaderTheme.CUSTOM -> customBackgroundHex ?: ReaderTheme.CUSTOM.backgroundHex
    else -> theme.backgroundHex
}

/** Text-color counterpart to [effectiveBackgroundHex]. */
fun ReaderSettings.effectiveTextHex(): String? = when (theme) {
    null -> null
    ReaderTheme.CUSTOM -> customTextHex ?: ReaderTheme.CUSTOM.textHex
    else -> theme.textHex
}

/** A comic (CBZ/Divina) page is a raw bitmap with no publisher CSS to theme, and Sepia/Paper/
 *  Contrast/Custom don't read well against photographic or inked art -- so rendering is restricted
 *  to plain Light or Dark, defaulting to Dark whenever the persisted theme isn't already one of
 *  those two. [ReaderSettings.verticalMargin] is a text-reading-comfort setting with no comic
 *  equivalent; left on, it pads the navigator's container above/below the page (see
 *  applyContainerAppearance) and shows as solid black/white bars around fixed-layout comic pages,
 *  so it's forced to zero here too. Only used to compute what's actually *rendered* (navigator
 *  preferences, container padding/background); the persisted [ReaderSettings] itself is left
 *  untouched, so an explicit Light/Dark pick or margin made while reading a comic still persists
 *  as a normal settings change, same as any other book. */
fun ReaderSettings.forComicRendering(isComic: Boolean): ReaderSettings {
    if (!isComic) return this
    val themed = if (theme == ReaderTheme.LIGHT || theme == ReaderTheme.DARK) this else copy(theme = ReaderTheme.DARK)
    return if (themed.verticalMargin == 0.0) themed else themed.copy(verticalMargin = 0.0)
}

/** [comicServerReadingProgression] is the raw `readingProgression` string from
 *  ApiService.getReadingProgression (only ever `"rtl"` or null -- see comicInfo.ts), consulted only
 *  when [ComicReadingDirection.AUTO] is in effect; ignored (and safe to omit) for non-comic books,
 *  which never fetch that endpoint.
 *
 *  [isComic] gates [Spread.AUTO] (two-page landscape spreads) -- left at its EpubPreferences
 *  default (null, which resolves to [Spread.NEVER]) for text EPUBs, which have no comic-style
 *  page-turn concept and shouldn't pick up dual-page rendering just because a fixed-layout EPUB
 *  happens to be open. AUTO's actual device-orientation check lives in EpubNavigatorFragment
 *  (readium-navigator-patched), which is what makes AUTO here mean "two pages in landscape, one
 *  in portrait" rather than upstream Readium's own dead AUTO (identical to NEVER in 3.1.1). */
fun ReaderSettings.toEpubPreferences(comicServerReadingProgression: String? = null, isComic: Boolean = false): EpubPreferences {
    val backgroundColor = effectiveBackgroundHex()?.toReadiumColor()
    val textColor = effectiveTextHex()?.toReadiumColor()
    val readingProgression = when (comicReadingDirection) {
        ComicReadingDirection.LTR -> ReadiumReadingProgression.LTR
        ComicReadingDirection.RTL -> ReadiumReadingProgression.RTL
        ComicReadingDirection.AUTO ->
            if (comicServerReadingProgression == "rtl") ReadiumReadingProgression.RTL else null
    }
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
        pageMargins = pageMargins,
        readingProgression = readingProgression,
        spread = if (isComic) Spread.AUTO else null
    )
}

/** Readium's Color is a Kotlin inline value class wrapping a plain @ColorInt Int, not
 *  androidx.compose.ui.graphics.Color -- construct via android.graphics.Color.parseColor. */
private fun String.toReadiumColor(): ReadiumColor = ReadiumColor(android.graphics.Color.parseColor(this))

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

/** Deterministic key for every layout-affecting input to page rendering, for caching a full-book
 *  exact page-count sweep (see PageCountSweeper/ExactPageCountRepository) across sessions that
 *  share it. Covers every field [toEpubPreferences] submits to Readium plus [verticalMargin]
 *  (applied outside Readium, see its own doc comment) and [contentWidthPx]/[contentHeightPx] --
 *  the actual laid-out pixel size of the navigator's container, which feeds Readium's own
 *  column-count "auto" heuristic and so is as much a layout input as any user-facing setting, even
 *  though the user doesn't set it directly. Deliberately excludes [theme] (colors don't reflow
 *  text) and anything about system chrome/inset visibility -- this app's content area doesn't
 *  resize when the status/nav bar show or hide (see ReaderActivity's chrome handling), so chrome
 *  state is not a layout input here. */
fun ReaderSettings.layoutFingerprint(contentWidthPx: Int, contentHeightPx: Int): String = listOf(
    fontFamily, fontSize, textAlign, lineHeight, paragraphSpacing, paragraphIndent,
    wordSpacing, letterSpacing, hyphens, publisherStyles, layout, pageMargins, verticalMargin,
    contentWidthPx, contentHeightPx
).joinToString("|")
