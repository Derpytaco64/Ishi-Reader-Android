package com.ishireader.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.formatPercent

/**
 * Two-step book picker (source, then destination -- exact manifestUrl match with the source is
 * blocked) followed by an explicit overwrite confirmation, for the "Migrate Book Data" user-menu
 * entry. Mirrors the website's MigrateBookDataDialog step-for-step; state lives in
 * [MigrateBookDataViewModel], this is purely presentational.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateBookDataSheet(
    state: MigrateBookDataUiState,
    onPickSource: (Book) -> Unit,
    onChangeSource: () -> Unit,
    onContinueFromSource: () -> Unit,
    onPickDest: (Book) -> Unit,
    onChangeDest: () -> Unit,
    onContinueFromDest: () -> Unit,
    onBackToDest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Migrate Book Data",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when {
                state.isLoadingBooks -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))

                state.step == MigrateStep.SOURCE && state.sourceBook == null -> {
                    Text(
                        text = "Choose the book to migrate progress and annotations from.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BookPickerList(books = state.books, excludeUrl = null, onPick = onPickSource)
                }

                state.step == MigrateStep.SOURCE && state.sourceBook != null -> {
                    SelectedBookRow(
                        book = state.sourceBook,
                        summary = state.sourceSummary,
                        isLoadingSummary = state.isLoadingSourceSummary,
                        onChange = onChangeSource
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onContinueFromSource,
                        enabled = !state.isLoadingSourceSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue") }
                }

                state.step == MigrateStep.DEST && state.destBook == null -> {
                    Text(
                        text = "Choose the book to migrate that data to.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BookPickerList(books = state.books, excludeUrl = state.sourceBook?.url, onPick = onPickDest)
                }

                state.step == MigrateStep.DEST && state.destBook != null -> {
                    SelectedBookRow(
                        book = state.destBook,
                        summary = state.destSummary,
                        isLoadingSummary = state.isLoadingDestSummary,
                        onChange = onChangeDest
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onContinueFromDest,
                        enabled = !state.isLoadingDestSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue") }
                }

                state.step == MigrateStep.CONFIRM && state.sourceBook != null && state.destBook != null -> {
                    Text(
                        text = "This will overwrite \"${state.destBook.title}\"'s progress, reading time, " +
                            "completed reads, and annotations with the data from \"${state.sourceBook.title}\". " +
                            "This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    state.submitError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBackToDest, enabled = !state.isSubmitting) { Text("Back") }
                        Button(onClick = onConfirm, enabled = !state.isSubmitting) {
                            Text(if (state.isSubmitting) "Migrating…" else "Overwrite and Migrate")
                        }
                    }
                }

                state.step == MigrateStep.DONE -> {
                    Text(
                        text = "Done -- ${state.destBook?.title} now has ${state.sourceBook?.title}'s data.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun BookPickerList(books: List<Book>, excludeUrl: String?, onPick: (Book) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(books, excludeUrl, query) {
        books.filter { book ->
            (excludeUrl == null || book.url != excludeUrl) &&
                (query.isBlank() || book.title.contains(query, ignoreCase = true) || book.author.contains(query, ignoreCase = true))
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search your library…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
        items(filtered, key = { it.url }) { book ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(book) }
                    .padding(vertical = 4.dp)
            ) {
                BookThumbnail(book)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (filtered.isEmpty()) {
            item { Text("No books found", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }
}

@Composable
private fun SelectedBookRow(book: Book, summary: BookSummary?, isLoadingSummary: Boolean, onChange: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BookThumbnail(book)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            isLoadingSummary -> Text("Loading stats…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary != null -> SummaryChips(summary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryChips(summary: BookSummary) {
    val chips = buildList {
        add(summary.percent?.let { "Progress: ${formatPercent(it / 100.0)}" } ?: "Progress: Not started")
        if (summary.isAudiobook) {
            if ((summary.listeningSeconds ?: 0.0) > 0.0) add("Time Listened: ${formatFullReadingTime(summary.listeningSeconds!!)}")
            add("Completed Listens: ${summary.completedListens}")
        } else {
            add("Notes: ${summary.notes}")
            add("Highlights: ${summary.highlights}")
            add("Bookmarks: ${summary.bookmarks}")
            if ((summary.readingSeconds ?: 0.0) > 0.0) add("Time Read: ${formatFullReadingTime(summary.readingSeconds!!)}")
            add("Completed Reads: ${summary.completedReads}")
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = chip,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun BookThumbnail(book: Book) {
    AsyncImage(
        model = book.cover,
        contentDescription = book.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 36.dp, height = 54.dp)
    )
}
