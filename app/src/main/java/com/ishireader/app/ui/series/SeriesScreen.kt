package com.ishireader.app.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.data.model.Book
import com.ishireader.app.ui.common.BookCoverCard
import com.ishireader.app.ui.settings.LocalAppSettings

// CLAUDE-ADDED: Mirrors StatefulSeriesView.tsx's fan sizing ratios (fan height / offset / grid
// column width, all relative to a 110px reference cover), just scaled down to a cover width that
// actually fits multiple columns on a phone -- the site's own 110px reference cover only yields a
// single (very wide) column at typical mobile viewport widths.
private val FanCoverWidth = 84.dp
private val FanHeight = FanCoverWidth * (210f / 110f)
private val FanOffset = FanCoverWidth * (40f / 110f)
private val FanGridColumnWidth = FanCoverWidth * (200f / 110f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val coverSize = LocalAppSettings.current.coverSize
    val selectedSlot = state.selectedSlot

    if (state.selectedSeriesKey != null) {
        BackHandler(onBack = viewModel::clearSelection)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesTitle(selectedSlot)) },
                // Reserved once already by MainTabsScreen's tab strip -- this screen never sits at
                // the true top of the window (it's always a page inside that pager).
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    if (selectedSlot != null) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Series")
                        }
                    }
                },
                actions = {
                    if (selectedSlot != null) {
                        SortDirectionDropdown(
                            current = state.sortDirection,
                            onSelect = viewModel::onSortDirectionChange
                        )
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
                state.isLoading && state.slots.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && state.slots.isEmpty() -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                state.slots.isEmpty() -> {
                    Text(
                        text = "None of your books have series information yet.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                selectedSlot != null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.selectedBooks) { book ->
                            BookCoverCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                modifier = Modifier.fillMaxWidth(),
                                onLongClick = { onBookLongClick(book) }
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = FanGridColumnWidth),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.slots) { slot ->
                            SeriesFanCard(slot = slot, onClick = { viewModel.selectSeries(slot.key) })
                        }
                    }
                }
            }
        }
    }
}

private fun seriesTitle(selectedSlot: SeriesSlot?): String =
    if (selectedSlot == null) "Series" else selectedSlot.name + if (selectedSlot.isAudiobook) " (Audiobook)" else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDirectionDropdown(current: SeriesSortDirection, onSelect: (SeriesSortDirection) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SeriesSortDirection.entries.forEach { direction ->
                DropdownMenuItem(
                    text = { Text(direction.label) },
                    onClick = {
                        onSelect(direction)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SeriesFanCard(slot: SeriesSlot, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FanHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        slot.left?.let { book ->
            AsyncImage(
                model = book.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -FanOffset, y = (-4).dp)
                    .width(FanCoverWidth)
                    .aspectRatio(2f / 3f)
                    .rotate(-20f)
            )
        }
        slot.right?.let { book ->
            AsyncImage(
                model = book.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = FanOffset, y = (-4).dp)
                    .width(FanCoverWidth)
                    .aspectRatio(2f / 3f)
                    .rotate(20f)
            )
        }
        Box(modifier = Modifier.align(Alignment.Center).width(FanCoverWidth).aspectRatio(2f / 3f)) {
            AsyncImage(
                model = slot.center.cover,
                contentDescription = slot.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = slot.name + if (slot.isAudiobook) " (Audiobook)" else "",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
