package com.ishireader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

private data class TocRow(val link: Link, val depth: Int, val page: Int?)

/**
 * Ports the website's table-of-contents panel (TocContent.tsx) -- a searchable, indented list of
 * TOC entries, tap to jump. Skips the website's collapsible-tree affordance (react-aria Tree with
 * expand/collapse chevrons) in favor of a flat indented list -- simpler, and EPUB TOCs are rarely
 * deep enough to need collapsing on a phone screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocPanelSheet(
    publication: Publication,
    positions: List<Locator>?,
    // CLAUDE-ADDED: Same real, layout-aware page numbers the reader's own footer shows (see
    // DynamicPageCountTracker) -- mirrors the website's applyExactPositions, remapping each
    // entry's coarse positions()-derived page onto the dynamic one so the TOC agrees with the
    // footer. Null (or missing an entry) falls back to the coarse number, same as the website
    // falls back to its raw tree when useExactPageCount has no data yet.
    dynamicStartPages: Map<String, Int>?,
    onJump: (Locator) -> Unit,
    onDismiss: () -> Unit,
    // CLAUDE-ADDED: A comic's real publication.tableOfContents is always empty -- the local CBZ
    // parser never reads ComicInfo.xml -- so ReaderActivity passes in a synthesized list (see
    // com.ishireader.app.reader.buildComicToc) here instead. Null (the EPUB/default case) falls
    // back to the publication's own TOC as before.
    toc: List<Link>? = null
) {
    var filter by remember { mutableStateOf("") }
    val effectiveToc = toc ?: publication.tableOfContents
    val rows = remember(publication, effectiveToc, positions, dynamicStartPages) {
        flattenToc(effectiveToc, positions = positions, dynamicStartPages = dynamicStartPages)
    }
    val filtered = if (filter.isBlank()) {
        rows
    } else {
        rows.filter { it.link.title?.contains(filter, ignoreCase = true) == true }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Text(
                "Table of Contents",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No entries found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.height(400.dp).padding(vertical = 8.dp)) {
                    items(filtered) { row ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { publication.locatorFromLink(row.link)?.let(onJump) }
                                .padding(start = (16 + row.depth * 16).dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                        ) {
                            Text(
                                text = row.link.title?.takeUnless { it.isBlank() } ?: row.link.href.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            row.page?.let { page ->
                                Text(
                                    text = page.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flattenToc(links: List<Link>, depth: Int = 0, positions: List<Locator>?, dynamicStartPages: Map<String, Int>?): List<TocRow> =
    links.flatMap { link ->
        listOf(TocRow(link, depth, pageNumberFor(link, positions, dynamicStartPages))) +
            flattenToc(link.children, depth + 1, positions, dynamicStartPages)
    }

/** Mirrors the website's buildTocTree.ts/applyExactPositions: prefers the resource's dynamic
 *  (real, layout-aware) start page when available, falling back to the page number of the first
 *  entry in [positions] whose href matches this link's (fragment stripped -- positions/dynamic
 *  pages are per-resource, not per-fragment). */
private fun pageNumberFor(link: Link, positions: List<Locator>?, dynamicStartPages: Map<String, Int>?): Int? {
    val bareHref = link.href.toString().substringBefore("#")
    dynamicStartPages?.get(bareHref)?.let { return it }
    if (positions == null) return null
    return positions.firstOrNull { it.href.toString().substringBefore("#") == bareHref }?.locations?.position
}
