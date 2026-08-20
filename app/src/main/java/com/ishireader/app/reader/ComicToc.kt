package com.ishireader.app.reader

import android.net.Uri
import com.ishireader.app.data.model.CBZPageBookmark
import org.readium.r2.shared.publication.Link

/**
 * Synthesizes a comic's table of contents, since the local CBZ/DIVINA parser (readium-streamer's
 * ImageParser) never reads ComicInfo.xml -- Publication.tableOfContents is always empty for a
 * locally-opened comic. Mirrors the website's own buildDivinaTocFromBookmarks/buildDivinaToc
 * (usePublication.ts) exactly, so a comic's TOC looks the same on both platforms:
 *
 * 1. If the server found ComicInfo.xml `<Pages>` chapter bookmarks (see [CBZPageBookmark],
 *    fetched via ApiService.getReadingProgression), each becomes one TOC entry pointing at that
 *    bookmark's page -- these are the file's real chapter titles ("Chapter 1 - The Still House"),
 *    not a guess.
 * 2. Otherwise, falls back to grouping consecutive [readingOrder] entries that share the same
 *    parent archive folder into one entry per group (titled with the folder path) -- many
 *    scanlation releases are packaged with one folder per chapter/volume even without ComicInfo.xml.
 *    Yields nothing if the whole archive is just one flat folder (a single group -- no real
 *    structure to show).
 */
fun buildComicToc(readingOrder: List<Link>, bookmarks: List<CBZPageBookmark>): List<Link> {
    val fromBookmarks = bookmarks.mapNotNull { bookmark ->
        readingOrder.getOrNull(bookmark.pageIndex)?.copy(title = bookmark.title)
    }
    if (fromBookmarks.isNotEmpty()) return fromBookmarks
    return buildFolderToc(readingOrder)
}

private fun buildFolderToc(readingOrder: List<Link>): List<Link> {
    val groups = mutableListOf<Pair<String, Link>>()
    var lastFolder: String? = null
    for (link in readingOrder) {
        val href = link.href.toString()
        val decoded = runCatching { Uri.decode(href) }.getOrDefault(href)
        val slashIndex = decoded.lastIndexOf("/")
        val folder = if (slashIndex >= 0) decoded.substring(0, slashIndex) else ""
        if (folder != lastFolder) {
            groups += folder to link
            lastFolder = folder
        }
    }
    if (groups.size <= 1) return emptyList()
    return groups.map { (folder, link) -> link.copy(title = folder.ifBlank { "…" }) }
}
