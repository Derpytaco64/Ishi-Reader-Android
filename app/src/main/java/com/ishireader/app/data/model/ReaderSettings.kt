@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.data.model

import kotlinx.serialization.Serializable
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

enum class ReaderTheme { LIGHT, DARK, SEPIA }

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
 * (niche, CJK/typography-specific), margin (the site applies it as a raw inline CSS style, not a
 * Readium preference, specifically to dodge Readium's own column-count math), column count
 * (Readium 3.1.1's ColumnCount enum only has AUTO in this toolkit version, nothing to choose from),
 * the extended theme palette (paper/contrast1-3 beyond light/dark/sepia), and per-language font
 * curation. All of Readium's nullable fields keep their "use the publication's own styling"
 * meaning here too -- there's no single "publisher default" enum value that would work uniformly
 * across every field, so it's spelled as null per-field instead.
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
    val layout: ReaderLayout = ReaderLayout.PAGINATED
)

fun ReaderSettings.toEpubPreferences(): EpubPreferences = EpubPreferences(
    theme = theme?.toReadium(),
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
    scroll = layout == ReaderLayout.SCROLLED
)

private fun ReaderTheme.toReadium(): Theme = when (this) {
    ReaderTheme.LIGHT -> Theme.LIGHT
    ReaderTheme.DARK -> Theme.DARK
    ReaderTheme.SEPIA -> Theme.SEPIA
}

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
