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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.tab == LibraryTab.AUDIOBOOKS) "Audiobooks" else "Books") },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp),
                actions = {
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
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
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

            Box(modifier = Modifier.fillMaxSize()) {
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
                    state.books.isEmpty() -> {
                        Text(
                            text = if (state.tab == LibraryTab.AUDIOBOOKS) "No audiobooks yet" else "No books yet",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.books) { book ->
                                BookCoverCard(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    modifier = Modifier.fillMaxWidth(),
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
