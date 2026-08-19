package com.ishireader.app.ui.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CustomShelf
import com.ishireader.app.ui.common.BookCoverCard
import com.ishireader.app.ui.common.filterDownloadedOnly
import com.ishireader.app.ui.settings.LocalAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelvesScreen(
    viewModel: ShelvesViewModel,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val selectedShelf = state.selectedShelf

    if (state.selectedShelfId != null) {
        BackHandler(onBack = viewModel::clearSelection)
    }

    Scaffold(
        topBar = {
            // CLAUDE-ADDED: No more plain "Shelves" title -- it just repeated the "Shelves" tab
            // directly above. "+ Shelf" takes over the now-empty (and, via CenterAlignedTopAppBar,
            // actually centered) title slot instead of sitting off to the side in actions, same
            // treatment as Library's sort menu.
            CenterAlignedTopAppBar(
                title = {
                    if (selectedShelf == null) {
                        TextButton(onClick = { viewModel.openCreateModal() }) { Text("+ Shelf") }
                    } else {
                        Text("${selectedShelf.icon} ${selectedShelf.name}")
                    }
                },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    if (selectedShelf != null) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Shelves")
                        }
                    }
                },
                actions = {
                    if (selectedShelf != null) {
                        TextButton(onClick = viewModel::toggleManagingBooks) {
                            Text(if (state.isManagingBooks) "Done" else "Add Books")
                        }
                    }
                }
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
                selectedShelf == null -> ShelfOverviewList(shelves = state.shelves, allBooks = state.allBooks, viewModel = viewModel)
                state.isManagingBooks -> ManageShelfBooksGrid(shelf = selectedShelf, allBooks = state.allBooks, viewModel = viewModel)
                else -> ShelfDetailGrid(
                    books = state.selectedShelfBooks.filterDownloadedOnly(),
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick
                )
            }
        }
    }

    // The create/edit modal and delete confirmation are rendered from MainTabsScreen instead of
    // here -- this composable only exists while the Shelves tab is the pager's current page, but
    // the book context menu's "+ Create new shelf" can open the create modal from any tab.
}

/**
 * Long-press-drag reordering, hand-rolled rather than pulling in a reorderable-list dependency
 * for one screen: [draggingId]/[dragOffset] track which row (by shelf id, stable across reorders
 * since LazyColumn is keyed on it) is being dragged and how far it's been displaced from its own
 * slot. On every drag tick, the dragged row's *visual* center (its laid-out offset plus
 * [dragOffset]) is compared against every other row's bounds via [LazyListState.layoutInfo]; once
 * it crosses into another row's bounds, [ShelvesViewModel.moveShelfLocally] swaps them in local
 * state immediately (cheap, no network) and [dragOffset] is corrected by exactly how far the
 * dragged row's own slot just moved, so the finger and the row stay glued together across the
 * swap instead of the row jumping. The reorder is only persisted once, in onDragEnd/onDragCancel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfOverviewList(shelves: List<CustomShelf>, allBooks: List<Book>, viewModel: ShelvesViewModel) {
    if (shelves.isEmpty()) {
        Text(
            text = "No shelves yet. Tap \"+ Shelf\" to create one.",
            modifier = Modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    // CLAUDE-ADDED: A shelf entry can outlive the book it points to (deleted/moved out of the
    // library without being explicitly removed from the shelf) -- join against what's actually in
    // the library so the counter matches selectedShelfBooks' own join instead of the raw stored
    // CustomShelf.books size.
    val libraryUrls = remember(allBooks) { allBooks.mapTo(mutableSetOf()) { it.url } }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(shelves, key = { it.id }) { shelf ->
            val isDragging = shelf.id == draggingId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .then(if (!isDragging) Modifier.animateItemPlacement() else Modifier)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                    )
                    .clickable { viewModel.selectShelf(shelf.id) }
                    // CLAUDE-ADDED: The whole row is the drag handle now -- there's no grip icon
                    // on the left to aim at anymore. Declared after clickable so this is the
                    // inner pointer node and wins the main pass once the long press fires; the
                    // tap only survives while nothing has consumed the gesture.
                    .pointerInput(shelf.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = shelf.id
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                viewModel.persistShelfOrder()
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y

                                val currentOrder = viewModel.uiState.value.shelves
                                val currentIndex = currentOrder.indexOfFirst { it.id == draggingId }
                                val draggedInfo = listState.layoutInfo.visibleItemsInfo
                                    .find { it.key == draggingId }
                                if (currentIndex == -1 || draggedInfo == null) return@detectDragGesturesAfterLongPress

                                val draggedCenter = draggedInfo.offset + draggedInfo.size / 2 + dragOffset
                                val target = listState.layoutInfo.visibleItemsInfo.find { info ->
                                    info.key != draggingId &&
                                        draggedCenter >= info.offset &&
                                        draggedCenter <= info.offset + info.size
                                } ?: return@detectDragGesturesAfterLongPress

                                val targetIndex = currentOrder.indexOfFirst { it.id == target.key }
                                if (targetIndex == -1 || targetIndex == currentIndex) return@detectDragGesturesAfterLongPress

                                dragOffset -= (target.offset - draggedInfo.offset).toFloat()
                                viewModel.moveShelfLocally(currentIndex, targetIndex)
                            }
                        )
                    }
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
                    text = "${shelf.books.count { it.url in libraryUrls }}",
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
private fun ShelfDetailGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    if (books.isEmpty()) {
        Text(
            text = "No books on this shelf yet. Tap \"Add Books\" to add some.",
            modifier = Modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    val coverSize = LocalAppSettings.current.coverSize
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.url }) { book ->
            // CLAUDE-ADDED: The per-cover "x" removal button is gone -- removing a book from a
            // shelf is already reachable from the long-press context menu (toggles the shelf off),
            // so this was a second, easy-to-fat-finger way to do the same thing.
            BookCoverCard(
                book = book,
                onClick = { onBookClick(book) },
                modifier = Modifier.fillMaxWidth(),
                onLongClick = { onBookLongClick(book) }
            )
        }
    }
}

@Composable
private fun ManageShelfBooksGrid(shelf: CustomShelf, allBooks: List<Book>, viewModel: ShelvesViewModel) {
    val memberUrls = shelf.books.map { it.url }.toSet()
    val coverSize = LocalAppSettings.current.coverSize

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
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

@Composable
fun ShelfFormDialog(modal: ShelfModalState, viewModel: ShelvesViewModel) {
    // Local, ephemeral like the site's iconSearch useState -- this composable is only in
    // composition while the modal is open, so it naturally resets on every open.
    var iconSearch by remember { mutableStateOf("") }
    val filteredIconChoices = remember(iconSearch) {
        val query = iconSearch.trim().lowercase()
        if (query.isEmpty()) SHELF_ICON_CHOICES else SHELF_ICON_CHOICES.filter { it.label.lowercase().contains(query) }
    }

    AlertDialog(
        onDismissRequest = viewModel::closeModal,
        title = { Text(if (modal.editingShelfId == null) "New Shelf" else "Edit Shelf") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                OutlinedTextField(
                    value = iconSearch,
                    onValueChange = { iconSearch = it },
                    placeholder = { Text("Search icons…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filteredIconChoices.isEmpty()) {
                    Text(
                        text = "No matching icons",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 260.dp)
                    ) {
                        items(filteredIconChoices, key = { it.icon }) { choice ->
                            val selected = choice.icon == modal.icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { viewModel.onModalIconChange(choice.icon) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(choice.icon, fontSize = 20.sp)
                            }
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
