package com.ishireader.app.ui.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.imageLoader
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.R
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.model.WeeklyBookTypeStats
import com.ishireader.app.data.model.buildNotesMarkdown
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.model.notesExportFilename
import com.ishireader.app.data.repository.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.NotesRepository
import com.ishireader.app.data.repository.StatsRepository
import com.ishireader.app.ui.common.BookCoverCard
import com.ishireader.app.ui.common.LocalDownloadedOnlyFilter
import com.ishireader.app.ui.common.filterDownloadedOnly
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.series.SeriesScreen
import com.ishireader.app.ui.series.SeriesViewModel
import com.ishireader.app.ui.series.seriesKey
import com.ishireader.app.ui.settings.LocalAppSettings
import com.ishireader.app.ui.settings.SettingsDrawerContent
import com.ishireader.app.ui.settings.SettingsViewModel
import com.ishireader.app.ui.shelves.ShelfFormDialog
import com.ishireader.app.ui.shelves.ShelvesScreen
import com.ishireader.app.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch

private val TabTitles = listOf("Home", "Library", "Series", "Shelves")
private val LogoSize = 56.dp
private val AvatarSize = 40.dp
private val DownloadRingSize = 48.dp
private val AvatarRingsSize = 62.dp
private val RingStrokeWidth = 3.dp

/** Home/Library/Series as swipeable pages under one tab strip, instead of separate pushed
 *  destinations -- each keeps its own ViewModel (scoped to this composable's back stack entry,
 *  same as before) so state survives swiping away and back. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
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
    val homeState by homeViewModel.uiState.collectAsState()
    val user by topBarViewModel.user.collectAsState()
    val shelvesState by shelvesViewModel.uiState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var userMenuExpanded by remember { mutableStateOf(false) }
    var isStatsOpen by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<UserStats?>(null) }
    var weeklyStats by remember { mutableStateOf<WeeklyBookTypeStats?>(null) }
    var weekOffset by remember { mutableStateOf(0) }
    var isMigrateOpen by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isEditUserOpen by remember { mutableStateOf(false) }
    var isAniListOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as IshiReaderApp
    val isOffline by app.libraryRepository.isOffline.collectAsState()
    val editUserViewModel: EditUserViewModel = viewModel(factory = EditUserViewModel.Factory(app.authRepository))
    val editUserState by editUserViewModel.uiState.collectAsState()
    val aniListAccountViewModel: AniListAccountViewModel = viewModel(factory = AniListAccountViewModel.Factory(app.aniListRepository))
    val aniListAccountState by aniListAccountViewModel.uiState.collectAsState()
    val migrateBookDataViewModel: MigrateBookDataViewModel = viewModel(
        factory = MigrateBookDataViewModel.Factory(
            app.libraryRepository,
            app.positionRepository,
            app.notesRepository,
            app.annotationsRepository,
            app.completedReadsRepository,
            app.readingTimerRepository,
            app.listeningTimeRepository,
            app.bookMigrationRepository
        )
    )
    val migrateBookDataState by migrateBookDataViewModel.uiState.collectAsState()
    val downloadsVersion by app.bookDownloadRepository.downloadsVersion.collectAsState()
    val coverSize = LocalAppSettings.current.coverSize
    val activeDownloads by app.bookDownloadRepository.activeDownloads.collectAsState()
    val isSyncingFlow = remember { app.syncScheduler.isSyncingFlow() }
    val isSyncing by isSyncingFlow.collectAsState(initial = false)
    val showDownloadedOnly by app.preferences.showDownloadedOnly.collectAsState(initial = false)

    // CLAUDE-ADDED: Mirrors the website's StatefulLibrarySearch -- a plain client-side filter over
    // the whole already-fetched library (both ebooks and audiobooks, from Home's own My Library
    // shelf, see HomeViewModel's "whole library" comment), not a server search endpoint. Matches
    // page.tsx's predicate exactly: case-insensitive substring against title/author/series.name/any
    // tag, OR-combined.
    var searchQuery by remember { mutableStateOf("") }
    val trimmedSearchQuery = searchQuery.trim()
    val searchResults = if (trimmedSearchQuery.isBlank()) {
        emptyList()
    } else {
        homeState.myLibrary.filter { book ->
            book.title.contains(trimmedSearchQuery, ignoreCase = true) ||
                book.author.contains(trimmedSearchQuery, ignoreCase = true) ||
                book.series?.name?.contains(trimmedSearchQuery, ignoreCase = true) == true ||
                book.tags.any { it.contains(trimmedSearchQuery, ignoreCase = true) }
        }
    }.filterDownloadedOnly()

    // MainTabsScreen's composition is torn down and rebuilt fresh every time bookDetail is popped
    // back to this destination, so this fires on the very first visit and again on every return --
    // including right after reading a book, so Continue Reading/Last Series Read/My Library catch
    // up on whatever position was just synced (see PositionRepository.getPosition's reconcile).
    LaunchedEffect(Unit) { homeViewModel.refresh() }

    LaunchedEffect(isStatsOpen) {
        if (isStatsOpen) {
            stats = null
            weekOffset = 0
            when (val result = statsRepository.getStats()) {
                is ApiResult.Success -> stats = result.data
                is ApiResult.Failure -> {}
            }
        }
    }

    // CLAUDE-ADDED: Separate from the effect above -- keyed on weekOffset too, so tapping the chart's
    // prev/next-week arrows (which only change weekOffset) re-fetches just the weekly graph without
    // re-fetching the rest of the stats dialog. Resetting weekOffset to 0 on open (above) means this
    // also fires once on the initial open in the common case (offset unchanged from last close, so
    // isStatsOpen alone wouldn't retrigger it) since isStatsOpen is a key here too.
    LaunchedEffect(isStatsOpen, weekOffset) {
        if (isStatsOpen) {
            weeklyStats = null
            when (val result = statsRepository.getWeeklyStats(weekOffset)) {
                is ApiResult.Success -> weeklyStats = result.data
                is ApiResult.Failure -> {}
            }
        }
    }

    LaunchedEffect(isMigrateOpen) {
        if (isMigrateOpen) migrateBookDataViewModel.start()
    }

    LaunchedEffect(isEditUserOpen) {
        if (isEditUserOpen) editUserViewModel.start(user)
    }

    LaunchedEffect(isAniListOpen) {
        if (isAniListOpen) aniListAccountViewModel.start(user)
    }

    // CLAUDE-ADDED: Guards against the logo's drawer-open tap and a book cover's detail-navigation
    // tap firing together (e.g. two quick taps landing close in time) -- without this, navigating
    // to bookDetail can tear this composable (and its ModalNavigationDrawer/drawerState) down
    // mid-open-animation, leaving a stuck blank frame. Only one of the two actions is allowed to
    // start until it finishes. The lock releases itself after a short delay rather than relying on
    // navigating away to dispose this composable (predictive-back keeps the destination's
    // composition alive, so that disposal isn't guaranteed) -- otherwise a single book tap would
    // wedge the lock on permanently and silently disable the logo/every other book afterwards.
    var interactionLocked by remember { mutableStateOf(false) }
    LaunchedEffect(interactionLocked) {
        if (interactionLocked) {
            delay(500)
            interactionLocked = false
        }
    }
    val guardedOnBookClick: (Book) -> Unit = { book ->
        if (!interactionLocked) {
            interactionLocked = true
            onBookClick(book)
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

    fun downloadBook(book: Book) {
        scope.launch {
            when (val result = app.bookDownloadRepository.download(book.manifestUrl())) {
                is ApiResult.Success -> {
                    Toast.makeText(context, "Downloaded \"${book.title}\"", Toast.LENGTH_SHORT).show()
                    // CLAUDE-ADDED: So this book's time/pace/sessions/annotations are already
                    // cached the moment it's downloaded, not just after it's opened once online --
                    // see LibraryMetadataPrefetcher's doc comment.
                    launch { app.libraryMetadataPrefetcher.prefetchOne(book) }
                }
                is ApiResult.Failure -> Toast.makeText(context, "Couldn't download: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteAllDownloads() {
        scope.launch {
            withContext(Dispatchers.IO) { app.bookDownloadRepository.deleteAll() }
            Toast.makeText(context, "Deleted all downloaded files", Toast.LENGTH_SHORT).show()
        }
    }

    // CLAUDE-ADDED: Mirrors the website's "Refresh Manifest Cache" user-menu action, plus the
    // local half a browser's own HTTP cache would otherwise cover -- Coil's memory/disk cache can
    // keep serving a bad cover bitmap even after the server-side manifest cache (title/author/cover
    // resolution) is cleared, since it's a different cache keyed by a different thing. Disabled
    // while offline: clearing local covers with nothing to re-fetch them from would just blank the
    // library instead of fixing it.
    fun clearManifestAndImageCache() {
        if (isClearingCache || isOffline) return
        isClearingCache = true
        scope.launch {
            val result = app.libraryRepository.clearManifestCache()
            withContext(Dispatchers.IO) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
            homeViewModel.refresh()
            libraryViewModel.refresh()
            seriesViewModel.refresh()
            shelvesViewModel.refresh()
            isClearingCache = false
            when (result) {
                is ApiResult.Success -> Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                is ApiResult.Failure -> Toast.makeText(context, "Couldn't clear server cache: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                onMoveShelf = settingsViewModel::moveShelf,
                showDownloadedOnly = showDownloadedOnly,
                onShowDownloadedOnlyChange = { value ->
                    scope.launch { app.preferences.setShowDownloadedOnly(value) }
                },
                bookDownloadRepository = app.bookDownloadRepository,
                downloadsVersion = downloadsVersion,
                onDeleteAllDownloads = ::deleteAllDownloads
            )
        }
    ) {
      CompositionLocalProvider(LocalDownloadedOnlyFilter provides showDownloadedOnly) {
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
                        .clickable {
                            if (!interactionLocked) {
                                interactionLocked = true
                                scope.launch { drawerState.open() }
                            }
                        }
                )
                LibrarySearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Box {
                    Box(
                        modifier = Modifier.size(AvatarRingsSize),
                        contentAlignment = Alignment.Center
                    ) {
                        // CLAUDE-ADDED: Outer ring circles while a background sync (position or
                        // library-prefs) is running, then fills solid and fades out on completion.
                        SyncProgressRing(isSyncing = isSyncing, modifier = Modifier.size(AvatarRingsSize))
                        // CLAUDE-ADDED: Inner ring fills clockwise with download progress, one arc
                        // segment per concurrently-downloading book, each segment's share of the
                        // ring weighted by that book's file size.
                        DownloadProgressRing(downloads = activeDownloads.values.toList(), modifier = Modifier.size(DownloadRingSize))
                        Box(modifier = Modifier.clickable { userMenuExpanded = true }) {
                            UserAvatar(user = user, baseUrl = avatarBaseUrl)
                        }
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
                            text = { Text("Edit User") },
                            onClick = {
                                userMenuExpanded = false
                                isEditUserOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("AniList") },
                            onClick = {
                                userMenuExpanded = false
                                isAniListOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Stats") },
                            onClick = {
                                userMenuExpanded = false
                                isStatsOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Migrate Book Data") },
                            onClick = {
                                userMenuExpanded = false
                                isMigrateOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isClearingCache) "Clearing…" else "Clear Manifest/Image Cache") },
                            enabled = !isOffline && !isClearingCache,
                            onClick = {
                                userMenuExpanded = false
                                clearManifestAndImageCache()
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

            // CLAUDE-ADDED: "Search takes over" pattern, same as the website (StatefulLibrarySearch's
            // own comment) -- a non-empty query replaces the entire tab strip/pager with a flat
            // results grid regardless of which tab was active, rather than filtering within it.
            if (trimmedSearchQuery.isNotEmpty()) {
                // CLAUDE-ADDED: Matches the tab pages' own background -- each of those renders
                // inside its own Scaffold, which paints MaterialTheme.colorScheme.background (and,
                // just as importantly, provides a matching LocalContentColor -- IshiReaderTheme
                // itself is a bare MaterialTheme with no Surface, so without one here book titles
                // fell back to Compose's hardcoded default content color instead of following the
                // app's Light/Dark/accent-color theme setting) by default; this sits directly in the
                // outer Column instead (no Scaffold of its own).
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No books match \"$trimmedSearchQuery\".",
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchResults) { book ->
                                BookCoverCard(
                                    book = book,
                                    onClick = { guardedOnBookClick(book) },
                                    modifier = Modifier.fillMaxWidth(),
                                    onLongClick = { contextMenuBook = book }
                                )
                            }
                        }
                    }
                }
            } else {
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
                        0 -> HomeScreen(viewModel = homeViewModel, onBookClick = guardedOnBookClick, onBookLongClick = { contextMenuBook = it })
                        1 -> LibraryScreen(viewModel = libraryViewModel, onBookClick = guardedOnBookClick, onBookLongClick = { contextMenuBook = it })
                        2 -> SeriesScreen(viewModel = seriesViewModel, onBookClick = guardedOnBookClick, onBookLongClick = { contextMenuBook = it })
                        else -> ShelvesScreen(viewModel = shelvesViewModel, onBookClick = guardedOnBookClick, onBookLongClick = { contextMenuBook = it })
                    }
                }
            }
        }

        contextMenuBook?.let { book ->
            // Keyed on downloadsVersion too (not just book) so reopening the menu after a
            // download/delete from elsewhere -- or from this same sheet -- reflects the change
            // immediately instead of whatever was true the first time this book was long-pressed.
            // CLAUDE-ADDED: isDownloaded() hits the filesystem (File.listFiles()) -- run it off the
            // main thread instead of inline in remember{}, which was blocking composition on a
            // synchronous disk scan.
            var isDownloaded by remember(book, downloadsVersion) { mutableStateOf(false) }
            LaunchedEffect(book, downloadsVersion) {
                isDownloaded = withContext(Dispatchers.IO) { app.bookDownloadRepository.isDownloaded(book.manifestUrl()) }
            }
            BookContextMenuSheet(
                book = book,
                shelves = shelvesState.shelves,
                // Exact match against Home's own already-loaded state (no extra network round trip
                // needed for this -- homeState is already collected above) rather than a lastReadAt
                // heuristic, which used to miss books only read locally-offline so far (no server
                // lastReadAt yet, see HomeViewModel.effectiveLastReadAt) and could also show this
                // for books that were technically "started" but already finished or dismissed.
                canRemoveFromContinueReading = homeState.continueReading.any { it.book.url == book.url },
                isDownloaded = isDownloaded,
                onDismiss = { contextMenuBook = null },
                onGoToSeries = { goToSeries(book) },
                onExportNotes = { exportNotes(book) },
                onToggleShelf = { shelf -> shelvesViewModel.toggleBookInShelf(shelf.id, book) },
                onCreateShelf = { shelvesViewModel.openCreateModal(addBookUrl = book.url) },
                onRemoveFromContinueReading = { homeViewModel.dismissFromContinueReading(book) },
                onDownloadBook = { downloadBook(book) },
                onDeleteDownload = { app.bookDownloadRepository.delete(book.manifestUrl()) }
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
            StatsDialog(
                stats = stats,
                weeklyStats = weeklyStats,
                canGoToNextWeek = weekOffset < 0,
                onPreviousWeek = { weekOffset -= 1 },
                onNextWeek = { weekOffset += 1 },
                onDismiss = { isStatsOpen = false }
            )
        }

        if (isEditUserOpen) {
            EditUserSheet(
                state = editUserState,
                avatarBaseUrl = avatarBaseUrl,
                onNameChange = editUserViewModel::onNameChange,
                onCommitName = editUserViewModel::commitName,
                onPickAvatar = editUserViewModel::uploadAvatar,
                onCurrentPasswordChange = editUserViewModel::onCurrentPasswordChange,
                onNewPasswordChange = editUserViewModel::onNewPasswordChange,
                onConfirmPasswordChange = editUserViewModel::onConfirmPasswordChange,
                onSubmitPasswordChange = editUserViewModel::submitPasswordChange,
                onDismiss = {
                    isEditUserOpen = false
                    // CLAUDE-ADDED: Picks up a display-name/avatar change made in the sheet -- not
                    // observed live while the sheet is open since that's EditUserViewModel's own
                    // separate copy of the user (avoids a ViewModel-depends-on-ViewModel wiring).
                    topBarViewModel.refresh()
                }
            )
        }

        if (isAniListOpen) {
            AniListAccountSheet(
                state = aniListAccountState,
                onPinCodeChange = aniListAccountViewModel::onPinCodeChange,
                onConnect = aniListAccountViewModel::connect,
                onDisconnect = aniListAccountViewModel::disconnect,
                onDismiss = {
                    isAniListOpen = false
                    // Same reasoning as EditUserSheet's onDismiss -- picks up the connected/
                    // disconnected flag this sheet's own ViewModel tracked separately.
                    topBarViewModel.refresh()
                }
            )
        }

        if (isMigrateOpen) {
            MigrateBookDataSheet(
                state = migrateBookDataState,
                onPickSource = migrateBookDataViewModel::pickSource,
                onChangeSource = migrateBookDataViewModel::changeSource,
                onContinueFromSource = migrateBookDataViewModel::goToDestStep,
                onPickDest = migrateBookDataViewModel::pickDest,
                onChangeDest = migrateBookDataViewModel::changeDest,
                onContinueFromDest = migrateBookDataViewModel::goToConfirmStep,
                onBackToDest = migrateBookDataViewModel::backToDestStep,
                onConfirm = migrateBookDataViewModel::confirmMigration,
                onDismiss = { isMigrateOpen = false }
            )
        }
      }
    }
}

/** Mirrors the website's StatefulLibrarySearch/ThFormSearchField -- a pill-shaped field with a
 *  leading search icon (hidden once there's text, same as the site) and a trailing clear button
 *  (shown only when non-empty). Filtering itself happens in the caller (see MainTabsScreen's own
 *  searchResults) -- this is just the input. */
@Composable
private fun LibrarySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Search title, author, genre, series", style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        leadingIcon = if (value.isEmpty()) {
            { Icon(Icons.Filled.Search, contentDescription = null) }
        } else null,
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        } else null
    )
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

/** Inner ring around the avatar: one arc segment per concurrently-downloading book, each
 *  segment's share of the circle weighted by that book's total byte size (so a 400MB audiobook
 *  claims proportionally more of the ring than a 2MB epub), filling clockwise from 12 o'clock as
 *  that book's bytesRead/totalBytes grows. Draws nothing when nothing is downloading. */
@Composable
private fun DownloadProgressRing(downloads: List<DownloadProgress>, modifier: Modifier = Modifier) {
    if (downloads.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val trackColor = color.copy(alpha = 0.25f)
    val gapDegrees = if (downloads.size > 1) 6f else 0f
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = RingStrokeWidth.toPx(), cap = StrokeCap.Round)
        val totalBytes = downloads.sumOf { it.totalBytes.coerceAtLeast(1L) }.toFloat()
        var startAngle = -90f
        downloads.forEach { download ->
            val segmentBytes = download.totalBytes.coerceAtLeast(1L).toFloat()
            val segmentSweep = 360f * (segmentBytes / totalBytes)
            val drawSweep = (segmentSweep - gapDegrees).coerceAtLeast(0f)
            val drawStart = startAngle + gapDegrees / 2f
            val progress = (download.bytesRead.toFloat() / segmentBytes).coerceIn(0f, 1f)
            drawArc(color = trackColor, startAngle = drawStart, sweepAngle = drawSweep, useCenter = false, style = stroke)
            drawArc(color = color, startAngle = drawStart, sweepAngle = drawSweep * progress, useCenter = false, style = stroke)
            startAngle += segmentSweep
        }
    }
}

/** Outer ring, surrounding the download ring: circles continuously (an indeterminate arc, like
 *  Material's spinner) while [isSyncing] is true, then -- on the transition back to false -- fills
 *  to a complete ring and fades out, giving a clear "sync just finished" confirmation instead of
 *  just silently vanishing. Draws nothing once idle. */
@Composable
private fun SyncProgressRing(isSyncing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    var wasSyncing by remember { mutableStateOf(false) }
    val completionAlpha = remember { Animatable(0f) }
    LaunchedEffect(isSyncing) {
        if (!isSyncing && wasSyncing) {
            completionAlpha.snapTo(1f)
            delay(300)
            completionAlpha.animateTo(0f, animationSpec = tween(500))
        }
        wasSyncing = isSyncing
    }
    val infiniteTransition = rememberInfiniteTransition(label = "syncRingRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "syncRingRotationAngle"
    )
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = RingStrokeWidth.toPx(), cap = StrokeCap.Round)
        if (isSyncing) {
            rotate(rotation) {
                drawArc(color = color, startAngle = -90f, sweepAngle = 110f, useCenter = false, style = stroke)
            }
        } else if (completionAlpha.value > 0f) {
            drawArc(color = color.copy(alpha = completionAlpha.value), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
        }
    }
}

/** Mirrors StatefulUserMenu.tsx's stats dialog -- same four sections/fields, read from the same
 *  /api/userdata/stats endpoint, just laid out as label/value rows instead of a tile grid. */
@Composable
private fun StatsDialog(
    stats: UserStats?,
    weeklyStats: WeeklyBookTypeStats?,
    canGoToNextWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDismiss: () -> Unit
) {
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

                weeklyStats?.days?.let { days ->
                    WeeklyReadingChart(
                        days = days,
                        canGoToNextWeek = canGoToNextWeek,
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

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
fun formatFullReadingTime(totalSeconds: Double): String {
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
