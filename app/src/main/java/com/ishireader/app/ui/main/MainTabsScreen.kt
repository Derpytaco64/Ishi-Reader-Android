package com.ishireader.app.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ishireader.app.R
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.model.buildNotesMarkdown
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.notesExportFilename
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.StatsRepository
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.series.SeriesScreen
import com.ishireader.app.ui.series.SeriesViewModel
import com.ishireader.app.ui.series.seriesKey
import com.ishireader.app.ui.settings.SettingsDrawerContent
import com.ishireader.app.ui.settings.SettingsViewModel
import com.ishireader.app.ui.shelves.ShelfFormDialog
import com.ishireader.app.ui.shelves.ShelvesScreen
import com.ishireader.app.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch

private val TabTitles = listOf("Home", "Library", "Series", "Shelves")
private val LogoSize = 56.dp
private val AvatarSize = 40.dp

/** Home/Library/Series as swipeable pages under one tab strip, instead of separate pushed
 *  destinations -- each keeps its own ViewModel (scoped to this composable's back stack entry,
 *  same as before) so state survives swiping away and back. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    seriesViewModel: SeriesViewModel,
    shelvesViewModel: ShelvesViewModel,
    topBarViewModel: TopBarViewModel,
    settingsViewModel: SettingsViewModel,
    notesRepository: NotesRepository,
    statsRepository: StatsRepository,
    avatarBaseUrl: String?,
    onBookClick: (Book) -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TabTitles.size })
    val scope = rememberCoroutineScope()
    val user by topBarViewModel.user.collectAsState()
    val shelvesState by shelvesViewModel.uiState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var userMenuExpanded by remember { mutableStateOf(false) }
    var isStatsOpen by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<UserStats?>(null) }
    val context = LocalContext.current

    LaunchedEffect(isStatsOpen) {
        if (isStatsOpen) {
            stats = null
            when (val result = statsRepository.getStats()) {
                is ApiResult.Success -> stats = result.data
                is ApiResult.Failure -> {}
            }
        }
    }

    var contextMenuBook by remember { mutableStateOf<Book?>(null) }
    var pendingExportMarkdown by remember { mutableStateOf<String?>(null) }
    val createNotesDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val content = pendingExportMarkdown
        pendingExportMarkdown = null
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
    }

    fun goToSeries(book: Book) {
        val series = book.series ?: return
        seriesViewModel.selectSeries(seriesKey(series.name, book.isAudiobook))
        scope.launch { pagerState.animateScrollToPage(2) }
    }

    fun exportNotes(book: Book) {
        scope.launch {
            when (val result = notesRepository.getNotes(book.manifestUrl())) {
                is ApiResult.Success -> {
                    pendingExportMarkdown = buildNotesMarkdown(book.title, book.author, result.data)
                    createNotesDocument.launch(notesExportFilename(book.title))
                }
                // Decorative export action -- a failed fetch just means nothing happens, not worth
                // its own error UI.
                is ApiResult.Failure -> {}
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawerContent(
                settings = settings,
                onThemeChange = settingsViewModel::setTheme,
                onAccentColorChange = settingsViewModel::setAccentColor,
                onCoverSizeChange = settingsViewModel::setCoverSize,
                onShelfVisibleChange = settingsViewModel::setShelfVisible,
                onMoveShelf = settingsViewModel::moveShelf
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Filled with the app's own surface color and drawn behind the status bar/camera cutout
            // (background painted before statusBarsPadding shrinks the content inset), so the logo and
            // avatar are never obscured by it. Each page's own TopAppBar has its status bar inset
            // zeroed out (see HomeScreen/LibraryScreen/SeriesScreen) since neither it nor this header
            // ever sits at the true top of the window.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "Settings",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(LogoSize)
                        .clip(CircleShape)
                        .clickable { scope.launch { drawerState.open() } }
                )
                Box {
                    Box(modifier = Modifier.clickable { userMenuExpanded = true }) {
                        UserAvatar(user = user, baseUrl = avatarBaseUrl)
                    }
                    DropdownMenu(expanded = userMenuExpanded, onDismissRequest = { userMenuExpanded = false }) {
                        Text(
                            text = user?.name ?: user?.username.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        if (user?.name != null) {
                            Text(
                                text = "@${user?.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Stats") },
                            onClick = {
                                userMenuExpanded = false
                                isStatsOpen = true
                            }
                        )
                        if (user?.isAdmin == true) {
                            DropdownMenuItem(
                                text = { Text("Admin Settings") },
                                onClick = {
                                    userMenuExpanded = false
                                    onOpenAdmin()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Log out") },
                            leadingIcon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                            onClick = {
                                userMenuExpanded = false
                                onLogout()
                            }
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = pagerState.currentPage) {
                TabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> HomeScreen(viewModel = homeViewModel, onBookClick = onBookClick, onBookLongClick = { contextMenuBook = it })
                    1 -> LibraryScreen(viewModel = libraryViewModel, onBookClick = onBookClick, onBookLongClick = { contextMenuBook = it })
                    2 -> SeriesScreen(viewModel = seriesViewModel, onBookClick = onBookClick, onBookLongClick = { contextMenuBook = it })
                    else -> ShelvesScreen(viewModel = shelvesViewModel, onBookClick = onBookClick, onBookLongClick = { contextMenuBook = it })
                }
            }
        }

        contextMenuBook?.let { book ->
            BookContextMenuSheet(
                book = book,
                shelves = shelvesState.shelves,
                // Simplified from the site's exact condition (started, not finished, not already
                // dismissed) since checking "finished"/"dismissed" here would mean an extra network
                // round trip just to decide whether to show a menu item -- worst case this is a no-op
                // re-dismiss of a book not currently shown in Continue Reading.
                canRemoveFromContinueReading = book.lastReadAt != null,
                onDismiss = { contextMenuBook = null },
                onGoToSeries = { goToSeries(book) },
                onExportNotes = { exportNotes(book) },
                onToggleShelf = { shelf -> shelvesViewModel.toggleBookInShelf(shelf.id, book) },
                onCreateShelf = { shelvesViewModel.openCreateModal(addBookUrl = book.url) },
                onRemoveFromContinueReading = { homeViewModel.dismissFromContinueReading(book) }
            )
        }

        shelvesState.modal?.let { modal ->
            ShelfFormDialog(modal = modal, viewModel = shelvesViewModel)
        }

        if (shelvesState.pendingDeleteShelfId != null) {
            AlertDialog(
                onDismissRequest = shelvesViewModel::cancelDelete,
                title = { Text("Delete shelf?") },
                text = { Text("This can't be undone.") },
                confirmButton = { TextButton(onClick = shelvesViewModel::confirmDelete) { Text("Delete") } },
                dismissButton = { TextButton(onClick = shelvesViewModel::cancelDelete) { Text("Cancel") } }
            )
        }

        if (isStatsOpen) {
            StatsDialog(stats = stats, onDismiss = { isStatsOpen = false })
        }
    }
}

/** Mirrors the website's AvatarCircle: the user's uploaded avatar if they have one, otherwise a
 *  circle with their name's first letter (avatarUrl is server-relative, so it needs [baseUrl]
 *  prepended before Coil can load it). */
@Composable
private fun UserAvatar(user: PublicUser?, baseUrl: String?) {
    val avatarUrl = user?.avatarUrl?.let { path -> baseUrl?.let { it + path } }
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = user?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = user?.name?.take(1)?.uppercase().orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Mirrors StatefulUserMenu.tsx's stats dialog -- same four sections/fields, read from the same
 *  /api/userdata/stats endpoint, just laid out as label/value rows instead of a tile grid. */
@Composable
private fun StatsDialog(stats: UserStats?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(text = "Stats", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                if (stats == null) {
                    Text(text = "Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    StatSection("Library") {
                        StatRow("Books in Library", stats.booksInLibrary.toString())
                        StatRow("Books Started", stats.booksStarted.toString())
                        StatRow("Books Finished", stats.booksFinished.toString())
                    }
                    StatSection("Reading") {
                        StatRow("Time Reading", formatFullReadingTime(stats.totalReadingSeconds))
                        StatRow("Average Pace", stats.averageWpm?.let { "$it wpm" } ?: "—")
                        StatRow("Words Read", stats.totalWordsRead.toString())
                        StatRow("Day Streak", stats.currentStreakDays.toString())
                    }
                    StatSection("Audiobooks") {
                        StatRow("Audiobooks in Library", stats.audiobooksInLibrary.toString())
                        StatRow("Audiobooks Started", stats.audiobooksStarted.toString())
                        StatRow("Audiobooks Finished", stats.audiobooksFinished.toString())
                        StatRow("Time Listened", formatFullReadingTime(stats.totalListeningSeconds))
                    }
                    StatSection("Annotations") {
                        StatRow("Highlights", stats.highlightsCount.toString())
                        StatRow("Bookmarks", stats.bookmarksCount.toString())
                        StatRow("Notes", stats.notesCount.toString())
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    Column(content = content)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Same h/m/s breakdown as the site's formatFullReadingTime (formatReadingTime.ts) -- every unit
 *  below the largest present one is always shown (no rounding to a single unit). */
private fun formatFullReadingTime(totalSeconds: Double): String {
    val whole = totalSeconds.toLong()
    val hours = whole / 3600
    val minutes = (whole % 3600) / 60
    val seconds = whole % 60

    val parts = mutableListOf<String>()
    if (hours > 0) parts.add("${hours}h")
    if (hours > 0 || minutes > 0) parts.add("${minutes}m")
    parts.add("${seconds}s")
    return parts.joinToString(" ")
}
