package com.ishireader.app.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.flatten
import org.readium.r2.shared.publication.indexOfFirstWithHref

/**
 * The title of the chapter/section containing [locator] -- the last table-of-contents entry (in
 * flattened document order, so nested sub-sections are considered too) whose target resource
 * doesn't come after the locator's own resource in the reading order. That's "which TOC entry's
 * span of the book contains this position," which is what most reader UIs mean by "chapter title"
 * even when the TOC nests sub-headings under a parent chapter link.
 */
fun Publication.chapterTitleFor(locator: Locator): String? {
    val locatorIndex = readingOrder.indexOfFirstWithHref(locator.href) ?: return null

    var best: Link? = null
    var bestIndex = -1
    for (link in tableOfContents.flatten()) {
        val idx = readingOrder.indexOfFirstWithHref(link.url()) ?: continue
        if (idx in bestIndex..locatorIndex) {
            bestIndex = idx
            best = link
        }
    }
    return best?.title?.takeUnless { it.isBlank() }
}
