package com.ishireader.app.audiobook

import kotlinx.serialization.Serializable

/** One chapter entry, flattened out of the Readium Web Publication Manifest's `toc` -- an
 *  audiobook's chapters are timed-fragment links into its single track (`book.m4b#t=123.45`),
 *  not separate resources, so all this needs is a title and a start offset. */
data class AudiobookChapter(val title: String, val startSeconds: Double)

/** Everything [AudiobookRepository.fetchManifestInfo] needs out of the RWPM manifest.json to
 *  drive playback: the chapter list, and the single audio track's own href/type (needed to build
 *  a position locator shaped exactly like the one the website's Readium JS would save, so
 *  cross-device resume keeps working -- see AudiobookLocator.kt). */
data class AudiobookManifestInfo(
    val chapters: List<AudiobookChapter>,
    val trackHref: String?,
    val trackType: String?,
    val trackDurationSeconds: Double?
)

@Serializable
data class RwpmManifest(
    val readingOrder: List<RwpmLink> = emptyList(),
    val toc: List<RwpmTocItem> = emptyList()
)

@Serializable
data class RwpmLink(
    val href: String,
    val type: String? = null,
    val duration: Double? = null
)

@Serializable
data class RwpmTocItem(
    val href: String,
    val title: String? = null,
    val children: List<RwpmTocItem> = emptyList()
)

/** Depth-first flatten -- an audiobook's toc is normally flat already (one entry per chapter),
 *  but this tolerates a nested one instead of silently dropping children. */
fun flattenToc(items: List<RwpmTocItem>): List<RwpmTocItem> =
    items.flatMap { listOf(it) + flattenToc(it.children) }

/** Readium's audiobook toc encodes each chapter's start as a `#t=<seconds>` media fragment on the
 *  href -- see the website's ThAudioProgress/Timeline usage of the same convention. */
fun parseTimeFragmentSeconds(href: String): Double? {
    val marker = "#t="
    val idx = href.indexOf(marker)
    if (idx == -1) return null
    val rest = href.substring(idx + marker.length)
    val value = rest.takeWhile { it.isDigit() || it == '.' }
    return value.toDoubleOrNull()
}
