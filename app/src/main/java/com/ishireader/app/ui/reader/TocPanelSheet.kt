package com.ishireader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private data class TocRow(val link: Link, val depth: Int)

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
    onJump: (Locator) -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf("") }
    val rows = remember(publication) { flattenToc(publication.tableOfContents) }
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
                        Text(
                            text = row.link.title?.takeUnless { it.isBlank() } ?: row.link.href.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { publication.locatorFromLink(row.link)?.let(onJump) }
                                .padding(start = (16 + row.depth * 16).dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun flattenToc(links: List<Link>, depth: Int = 0): List<TocRow> =
    links.flatMap { link -> listOf(TocRow(link, depth)) + flattenToc(link.children, depth + 1) }
