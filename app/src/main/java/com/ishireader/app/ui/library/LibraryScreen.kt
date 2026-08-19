package com.ishireader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.SortMode
import com.ishireader.app.ui.common.BookCoverCard
import com.ishireader.app.ui.common.filterDownloadedOnly
import com.ishireader.app.ui.settings.LocalAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val coverSize = LocalAppSettings.current.coverSize
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val books = state.books.filterDownloadedOnly()

    Scaffold(
        topBar = {
            // CLAUDE-ADDED: No more title here -- it used to just repeat "Books"/"Audiobooks",
            // the same text already shown by the tab row directly below. The sort menu takes over
            // the now-empty (and, via CenterAlignedTopAppBar, actually centered) title slot instead
            // of sitting off to the side in actions.
            CenterAlignedTopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { sortMenuExpanded = true }) {
                            Text(state.sortMode.label)
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        viewModel.onSortModeChange(mode)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == LibraryTab.BOOKS,
                    onClick = { viewModel.onTabSelected(LibraryTab.BOOKS) },
                    text = { Text("Books") }
                )
                Tab(
                    selected = state.tab == LibraryTab.AUDIOBOOKS,
                    onClick = { viewModel.onTabSelected(LibraryTab.AUDIOBOOKS) },
                    text = { Text("Audiobooks") }
                )
            }

            // CLAUDE-ADDED: Replaces the old explicit refresh button -- dragging down now does the
            // reload, same as a mobile browser.
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading && state.books.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null && state.books.isEmpty() -> {
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                    books.isEmpty() -> {
                        Text(
                            text = if (state.tab == LibraryTab.AUDIOBOOKS) "No audiobooks yet" else "No books yet",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(books) { book ->
                                BookCoverCard(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    modifier = Modifier.fillMaxWidth(),
                                    onLongClick = { onBookLongClick(book) },
                                    // CLAUDE-ADDED: Every card on this tab is a square audiobook
                                    // cover, so there's no taller neighbour to line up with --
                                    // reserving the portrait slot here would just band each row
                                    // with empty space. Mixed grids (Home, shelves, search) keep
                                    // the default.
                                    uniformCoverSlot = state.tab != LibraryTab.AUDIOBOOKS
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
