package com.ishireader.app.ui.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CustomShelf
import com.ishireader.app.ui.common.BookCoverCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelvesScreen(
    viewModel: ShelvesViewModel,
    onBookClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val selectedShelf = state.selectedShelf

    if (state.selectedShelfId != null) {
        BackHandler(onBack = viewModel::clearSelection)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedShelf == null) "Shelves" else "${selectedShelf.icon} ${selectedShelf.name}") },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    if (selectedShelf != null) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Shelves")
                        }
                    }
                },
                actions = {
                    if (selectedShelf == null) {
                        TextButton(onClick = viewModel::openCreateModal) { Text("+ Shelf") }
                    } else {
                        TextButton(onClick = viewModel::toggleManagingBooks) {
                            Text(if (state.isManagingBooks) "Done" else "Add Books")
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.shelves.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && state.shelves.isEmpty() -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                selectedShelf == null -> ShelfOverviewList(shelves = state.shelves, viewModel = viewModel)
                state.isManagingBooks -> ManageShelfBooksGrid(shelf = selectedShelf, allBooks = state.allBooks, viewModel = viewModel)
                else -> ShelfDetailGrid(books = state.selectedShelfBooks, onBookClick = onBookClick, viewModel = viewModel)
            }
        }
    }

    state.modal?.let { modal ->
        ShelfFormDialog(modal = modal, viewModel = viewModel)
    }

    if (state.pendingDeleteShelfId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete shelf?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShelfOverviewList(shelves: List<CustomShelf>, viewModel: ShelvesViewModel) {
    if (shelves.isEmpty()) {
        Text(
            text = "No shelves yet. Tap \"+ Shelf\" to create one.",
            modifier = Modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(shelves, key = { it.id }) { shelf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectShelf(shelf.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shelf.icon, fontSize = 22.sp)
                Text(
                    text = shelf.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
                Text(
                    text = "${shelf.books.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                TextButton(onClick = { viewModel.openEditModal(shelf.id) }) { Text("Edit") }
                TextButton(onClick = { viewModel.requestDelete(shelf.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ShelfDetailGrid(books: List<Book>, onBookClick: (Book) -> Unit, viewModel: ShelvesViewModel) {
    if (books.isEmpty()) {
        Text(
            text = "No books on this shelf yet. Tap \"Add Books\" to add some.",
            modifier = Modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.url }) { book ->
            Box(modifier = Modifier.fillMaxWidth()) {
                BookCoverCard(book = book, onClick = { onBookClick(book) }, modifier = Modifier.fillMaxWidth())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { viewModel.removeBookFromSelectedShelf(book.url) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ManageShelfBooksGrid(shelf: CustomShelf, allBooks: List<Book>, viewModel: ShelvesViewModel) {
    val memberUrls = shelf.books.map { it.url }.toSet()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(allBooks, key = { it.url }) { book ->
            val inShelf = book.url in memberUrls
            Box(modifier = Modifier.fillMaxWidth()) {
                BookCoverCard(book = book, onClick = { viewModel.toggleBookInShelf(shelf.id, book) }, modifier = Modifier.fillMaxWidth())
                if (inShelf) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfFormDialog(modal: ShelfModalState, viewModel: ShelvesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeModal,
        title = { Text(if (modal.editingShelfId == null) "New Shelf" else "Edit Shelf") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = modal.name,
                    onValueChange = viewModel::onModalNameChange,
                    label = { Text("Shelf name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShelfIcons.forEach { icon ->
                        val selected = icon == modal.icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { viewModel.onModalIconChange(icon) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submitModal, enabled = modal.name.isNotBlank()) {
                Text(if (modal.editingShelfId == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeModal) { Text("Cancel") }
        }
    )
}
