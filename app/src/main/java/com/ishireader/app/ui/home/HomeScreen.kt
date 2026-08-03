package com.ishireader.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.Book
import com.ishireader.app.ui.common.BookCoverCard

private val ShelfItemWidth = 110.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBookClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isEmpty = state.continueReading.isEmpty() && state.lastSeriesRead.isEmpty() &&
        state.recentlyAdded.isEmpty() && state.myLibrary.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && isEmpty -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && isEmpty -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        if (state.continueReading.isNotEmpty()) {
                            ShelfGrid(title = "Continue Reading", items = state.continueReading) { item ->
                                ContinueReadingCard(
                                    item = item,
                                    onClick = { onBookClick(item.book) },
                                    onDismiss = { viewModel.dismissFromContinueReading(item.book) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (state.lastSeriesRead.isNotEmpty()) {
                            ShelfCarousel(title = "Last Series Read", books = state.lastSeriesRead, onBookClick = onBookClick)
                        }

                        if (state.recentlyAdded.isNotEmpty()) {
                            ShelfCarousel(title = "Recently Added", books = state.recentlyAdded, onBookClick = onBookClick)
                        }

                        if (state.myLibrary.isNotEmpty()) {
                            ShelfGrid(title = "My Library", items = state.myLibrary) { book ->
                                BookCoverCard(book = book, onClick = { onBookClick(book) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCarousel(title: String, books: List<Book>, onBookClick: (Book) -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books) { book ->
            BookCoverCard(book = book, onClick = { onBookClick(book) }, modifier = Modifier.width(ShelfItemWidth))
        }
    }
}

/** Rows of exactly [columns] items, each stretched to an equal share of the width -- same cover
 *  size as LibraryScreen's grid produces on a typical phone, filling the row instead of wrapping
 *  fixed-width items (which is what FlowRow gave us and left uneven trailing gaps). Plain Column/
 *  Row rather than LazyVerticalGrid since this already lives inside HomeScreen's own outer
 *  verticalScroll -- nesting two vertically-scrolling lazy layouts needs a bounded height that
 *  isn't naturally available here. */
@Composable
private fun <T> ShelfGrid(
    title: String,
    items: List<T>,
    columns: Int = 3,
    itemContent: @Composable RowScope.(T) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { item -> itemContent(item) }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    item: ContinueReadingItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BookCoverCard(book = item.book, onClick = onClick, modifier = Modifier.fillMaxWidth())
        item.percent?.let { percent ->
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
            Text("Remove", style = MaterialTheme.typography.labelSmall)
        }
    }
}
