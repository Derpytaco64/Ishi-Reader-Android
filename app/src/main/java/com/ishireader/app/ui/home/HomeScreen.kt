package com.ishireader.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.ui.common.BookCoverCard
import com.ishireader.app.ui.settings.LocalAppSettings

private val ProgressBarHeight = 4.dp

/** Columns for the "wraps to a grid" shelves (Continue Reading, My Library) -- smaller covers
 *  mean more of them fit per row, same idea as the Library tab's GridCells.Adaptive but fixed to
 *  whole columns since this grid isn't lazy (see ShelfGrid's own doc comment for why). */
private val CoverSize.homeGridColumns: Int
    get() = when (this) {
        CoverSize.SMALL -> 4
        CoverSize.MEDIUM -> 3
        CoverSize.LARGE -> 2
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings = LocalAppSettings.current
    val isEmpty = state.continueReading.isEmpty() && state.lastSeriesRead.isEmpty() &&
        state.recentlyAdded.isEmpty() && state.myLibrary.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        // CLAUDE-ADDED: Replaces the old explicit refresh button -- dragging down now does the
        // reload, same as a mobile browser.
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
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
                        settings.shelfOrder.forEach { shelfId ->
                            if (!settings.isShelfVisible(shelfId)) return@forEach
                            when (shelfId) {
                                HomeShelfId.CONTINUE_READING -> if (state.continueReading.isNotEmpty()) {
                                    ShelfGrid(title = "Continue Reading", items = state.continueReading, columns = settings.coverSize.homeGridColumns) { item ->
                                        ContinueReadingCard(
                                            item = item,
                                            onClick = { onBookClick(item.book) },
                                            onLongClick = { onBookLongClick(item.book) },
                                            onDismiss = { viewModel.dismissFromContinueReading(item.book) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                HomeShelfId.LAST_SERIES_READ -> if (state.lastSeriesRead.isNotEmpty()) {
                                    ShelfCarousel(title = "Last Series Read", books = state.lastSeriesRead, itemWidth = settings.coverSize.minWidthDp.dp, onBookClick = onBookClick, onBookLongClick = onBookLongClick)
                                }
                                HomeShelfId.RECENTLY_ADDED -> if (state.recentlyAdded.isNotEmpty()) {
                                    ShelfCarousel(title = "Recently Added", books = state.recentlyAdded, itemWidth = settings.coverSize.minWidthDp.dp, onBookClick = onBookClick, onBookLongClick = onBookLongClick)
                                }
                                HomeShelfId.MY_LIBRARY -> if (state.myLibrary.isNotEmpty()) {
                                    ShelfGrid(title = "My Library", items = state.myLibrary, columns = settings.coverSize.homeGridColumns) { book ->
                                        BookCoverCard(
                                            book = book,
                                            onClick = { onBookClick(book) },
                                            modifier = Modifier.weight(1f),
                                            onLongClick = { onBookLongClick(book) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCarousel(title: String, books: List<Book>, itemWidth: Dp, onBookClick: (Book) -> Unit, onBookLongClick: (Book) -> Unit) {
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
            BookCoverCard(
                book = book,
                onClick = { onBookClick(book) },
                modifier = Modifier.width(itemWidth),
                onLongClick = { onBookLongClick(book) }
            )
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
    onLongClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BookCoverCard(book = item.book, onClick = onClick, modifier = Modifier.fillMaxWidth(), onLongClick = onLongClick)
        // Reserves the progress bar's space even when there's no percent yet, so "Remove" lands
        // in the same spot across every card in a row instead of shifting up for books without one.
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(ProgressBarHeight)) {
            item.percent?.let { percent ->
                LinearProgressIndicator(
                    progress = { (percent / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
            Text("Remove", style = MaterialTheme.typography.labelSmall)
        }
    }
}
