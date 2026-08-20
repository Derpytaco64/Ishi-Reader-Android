package com.ishireader.app.ui.bookdetail

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.DailyListeningBucket
import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.formatPercent
import com.ishireader.app.data.model.isComic
import com.ishireader.app.reader.AnnotationsUiState
import com.ishireader.app.reader.ReadingTimerUiState
import com.ishireader.app.reader.TappedImage
import com.ishireader.app.ui.reader.AnnotationRowItem
import com.ishireader.app.ui.reader.AnnotationTab
import com.ishireader.app.ui.reader.AnnotationType
import com.ishireader.app.ui.reader.ImageViewerOverlay
import com.ishireader.app.ui.reader.ReadingTimerSheet
import com.ishireader.app.ui.reader.buildRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    book: Book,
    viewModel: BookDetailViewModel,
    onBackClick: () -> Unit,
    onReadClick: () -> Unit,
    onJumpToLocator: (Locator) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isComic = book.isComic
    // CLAUDE-ADDED: Page-rate "time left" for a comic -- mirrors the website's
    // StatefulBookSheet.tsx comicSecondsLeft, a one-off pagesRead/timeSpent ratio against this
    // screen's point-in-time percentRead/totalReadingSeconds snapshot (state.wpm/secondsLeft are
    // wordCount-derived and always null for a comic, see BookDetailViewModel/ReadingTimerTracker's
    // isComic gate -- there's no pace to show, just a page count and elapsed time).
    val comicSecondsLeft = if (!isComic) null else {
        val pageCount = state.pageCount
        val percent = state.percentRead
        val totalSeconds = state.totalReadingSeconds
        if (pageCount == null || pageCount <= 0 || percent == null || totalSeconds == null || totalSeconds <= 0) {
            null
        } else {
            val pagesRead = (percent / 100.0) * pageCount
            if (pagesRead <= 0) null else {
                val pagesRemaining = pageCount - pagesRead
                if (pagesRemaining <= 0) 0.0 else (pagesRemaining / pagesRead) * totalSeconds
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    var annotationTab by remember { mutableStateOf(AnnotationTab.ALL) }
    var annotationsDescending by remember { mutableStateOf(false) }
    var annotationsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var coverImage by remember { mutableStateOf<TappedImage?>(null) }
    var loadingCover by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showTrackingSheet by remember { mutableStateOf(false) }
    val app = context.applicationContext as IshiReaderApp
    val trackingViewModel: TrackingViewModel = viewModel(
        factory = TrackingViewModel.Factory(app.aniListRepository, app.libraryPrefsRepository)
    )
    val trackingState by trackingViewModel.uiState.collectAsState()
    LaunchedEffect(showTrackingSheet) {
        if (showTrackingSheet) trackingViewModel.start(book)
    }
    // Lightweight, sheet-independent lookup so the "Track on AniList" entry point below can already
    // show its linked/checkmark state for a series linked from a *different* volume, without making
    // the user open the sheet first just to find out -- see TrackingViewModel.checkLinked's own doc.
    LaunchedEffect(book) {
        if (isComic) trackingViewModel.checkLinked(book)
    }
    var pendingDeleteCompletedReadId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCompletedListenId by remember { mutableStateOf<String?>(null) }

    // Returning from ReaderActivity resumes this same Activity/composition rather than
    // navigating back into it, so nothing else would otherwise re-trigger a reload -- without
    // this, the progress ring would keep showing whatever it read on this screen's first visit.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Wraps the whole body (not just the description) in a SelectionContainer so any text on
        // this screen -- metadata chips, notes, annotation excerpts -- can be long-pressed and
        // copied, same as the reader itself already allows for in-book text.
        SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = book.cover,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        // Audiobook cover art is conventionally square (like an album/podcast
                        // cover), unlike the portrait 2:3 book-jacket ratio used otherwise --
                        // see BookCoverCard's matching treatment in the library grid.
                        .aspectRatio(if (book.isAudiobook) 1f else 2f / 3f)
                        .clickable(enabled = !loadingCover) {
                            coroutineScope.launch {
                                loadingCover = true
                                coverImage = loadCoverAsTappedImage(context, book)
                                loadingCover = false
                            }
                        }
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
                    book.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (book.author.isNotBlank()) {
                        Text(book.author, style = MaterialTheme.typography.bodyMedium)
                    }
                    book.series?.let { series ->
                        val label = if (series.position != null) "${series.name} #${series.position}" else series.name
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                    if (book.isAudiobook) {
                        Text("Audiobook", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    // CLAUDE-ADDED: Current-read stats sit beside the dial rather than in their own
                    // section further down the page, so the "how far in" and "how it's going"
                    // numbers read together at a glance. The Manage entry point (reset current
                    // read / delete completed history) sits right beside them as a small icon
                    // button rather than its own titled section further down the page.
                    state.percentRead?.let { percent ->
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProgressDial(percent = percent)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (book.isAudiobook) {
                                    // CLAUDE-ADDED: accumulatedSeconds is a lifetime total (see
                                    // ListeningTimeData), unlike totalReadingSeconds -- no wpm
                                    // equivalent for audio, but unlike text's pace-estimated
                                    // secondsLeft, "time remaining" is exact here since the track's
                                    // own duration is known outright (no words-per-minute guess needed).
                                    state.totalListeningSeconds?.takeIf { it > 0 }?.let {
                                        Text("Time listened: ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    val totalDuration = state.totalListeningDurationSeconds
                                    if (totalDuration != null && totalDuration > 0) {
                                        val remaining = (totalDuration * (1.0 - (percent / 100.0))).coerceAtLeast(0.0)
                                        Text("Time remaining: ${formatDuration(remaining)}", style = MaterialTheme.typography.labelSmall)
                                        Text("Length: ${formatDuration(totalDuration)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    state.totalReadingSeconds?.takeIf { it > 0 }?.let {
                                        Text("Time read: ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    // CLAUDE-ADDED: A comic has no words -- state.wpm/secondsLeft are
                                    // always null for it (wordCount-derived, see
                                    // BookDetailViewModel/ReadingTimerTracker's isComic gate), so this
                                    // shows the page-rate comicSecondsLeft instead of a pace stat.
                                    if (isComic) {
                                        comicSecondsLeft?.let {
                                            Text("Time left: ${formatEstimatedTime(it)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        state.wpm?.let {
                                            Text("Pace: $it wpm", style = MaterialTheme.typography.labelSmall)
                                        }
                                        state.secondsLeft?.let {
                                            Text("Time left: ${formatEstimatedTime(it)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            if (!book.isAudiobook && ((state.totalReadingSeconds ?: 0.0) > 0 || state.completedReads.isNotEmpty())) {
                                IconButton(onClick = { showTimerSheet = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Manage reading timer",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onReadClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (book.isAudiobook) "Play" else "Read")
            }

            Spacer(modifier = Modifier.height(16.dp))

            MetadataChips(book = book, pageCount = state.pageCount)

            // CLAUDE-ADDED: Manga-only AniList tracking entry point -- see TrackingSheet's own doc
            // comment. Light novels/prose EPUBs aren't in scope for AniList sync. trackingState.link
            // is populated eagerly by the checkLinked() effect above (not just when the sheet is
            // open), so a series linked from a different volume already shows as tracked here.
            if (isComic) {
                Spacer(modifier = Modifier.height(12.dp))
                if (trackingState.link != null) {
                    FilterChip(
                        selected = true,
                        onClick = { showTrackingSheet = true },
                        label = { Text("Tracking on AniList") },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) }
                    )
                    // CLAUDE-ADDED: Status/chapter/score/date summary -- populated by checkLinked's
                    // eager entry fetch (TrackingViewModel.kt), not just the sheet's own start(), so
                    // this shows without the user opening the sheet. Null entry (linked but AniList
                    // has no list entry for it yet, e.g. a fresh link the user hasn't set a status on)
                    // shows nothing here rather than a row of "Not set" placeholders. Reuses the same
                    // pill-shaped Chip() as MetadataChips/ChipSection below for a consistent look
                    // rather than plain text.
                    trackingState.media?.mediaListEntry?.let { entry ->
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Chip(statusLabel(entry.status))
                            val totalChapters = trackingState.media?.chapters
                            Chip("Ch. ${entry.progress}" + (totalChapters?.let { "/$it" } ?: ""))
                            if (entry.score > 0) Chip("★ ${entry.score.formatScore()}")
                            if (entry.startedAt?.year != null) Chip("Started ${entry.startedAt.label()}")
                            if (entry.completedAt?.year != null) Chip("Finished ${entry.completedAt.label()}")
                        }
                    }
                } else {
                    TextButton(onClick = { showTrackingSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Track on AniList")
                    }
                }
            }

            if (book.tags.isNotEmpty()) {
                ChipSection(title = "Genres") { book.tags.forEach { tag -> Chip(tag) } }
            }

            if (book.narrators.isNotEmpty()) {
                ChipSection(title = "Narrators") { Chip(book.narrators.joinToString(", ")) }
            }

            book.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Description", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }

            // CLAUDE-ADDED: Ports the website's book detail annotations list -- same all/highlights/
            // bookmarks/notes filter and book-order sort as the in-reader AnnotationsPanelSheet
            // (shared row logic lives in AnnotationRows.kt). Tapping a row opens the reader at that
            // exact locator rather than just the saved reading position. Collapsed by default --
            // this list can get long, and most visits to this screen don't need it open.
            if (state.highlights.isNotEmpty() || state.bookmarks.isNotEmpty() || state.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { annotationsExpanded = !annotationsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Annotations", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (annotationsExpanded) {
                            IconButton(onClick = { annotationsDescending = !annotationsDescending }) {
                                Icon(
                                    if (annotationsDescending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                    contentDescription = "Sort order"
                                )
                            }
                        }
                        Icon(
                            if (annotationsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (annotationsExpanded) "Collapse annotations" else "Expand annotations"
                        )
                    }
                }

                if (annotationsExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnotationTab.entries.forEach { t ->
                            FilterChip(
                                selected = annotationTab == t,
                                onClick = { annotationTab = t },
                                label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val annotationsState = AnnotationsUiState(
                        loading = false,
                        highlights = state.highlights,
                        bookmarks = state.bookmarks,
                        notes = state.notes
                    )
                    val rows = buildRows(annotationsState, annotationTab, annotationsDescending)
                    if (rows.isEmpty()) {
                        Text(
                            "No annotations in this filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column {
                            rows.forEach { row ->
                                AnnotationRowItem(
                                    row = row,
                                    totalPositions = null,
                                    onJump = { row.locator?.let(onJumpToLocator) },
                                    onDelete = {
                                        when (row.type) {
                                            AnnotationType.HIGHLIGHT -> viewModel.deleteHighlight(row.id)
                                            AnnotationType.BOOKMARK -> viewModel.deleteBookmark(row.id)
                                            AnnotationType.NOTE -> viewModel.deleteNote(row.id)
                                        }
                                    },
                                    onEditNote = { text -> viewModel.updateNoteText(row.id, text) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // CLAUDE-ADDED: The current (not-yet-completed) read's own day-by-day breakdown --
            // same buckets a "Save" reset would archive onto a fresh Completed Reads entry (see
            // completedRead.dailyHistory below), just shown live instead of requiring a reset
            // first. Mirrors the reader's own ReadingTimerSheet "Timer" tab. Sits directly above
            // Completed Reads since it's the same kind of breakdown, just for the read in progress.
            state.currentDailyHistory.takeIf { it.isNotEmpty() }?.let { history ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Current Read's Sessions", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.sortedByDescending { it.date }.forEach { bucket -> DailyHistoryRow(bucket) }
                }
            }

            // CLAUDE-ADDED: The most recent entry from the reader's own Completed tab, matching
            // StatefulBookSheet.tsx's "Completed Read" card -- only the single latest run, not the
            // full history list the reader's own Manage sheet's Completed tab shows. Titled
            // plural to match that sheet (and state.completedReads) now that the Manage entry
            // point itself moved up beside the progress dial instead of heading its own section here.
            // Sits below Annotations rather than above it.
            state.lastCompletedRead?.let { completedRead ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Completed Reads", style = MaterialTheme.typography.titleSmall)
                    IconButton(
                        onClick = { pendingDeleteCompletedReadId = completedRead.id },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete completed read",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Completed: ${formatTimestamp(completedRead.completedAt)}")
                    Chip("Duration: ${formatDuration(completedRead.seconds)}")
                }
                completedRead.dailyHistory?.takeIf { it.isNotEmpty() }?.let { history ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        history.sortedByDescending { it.date }.forEach { bucket -> DailyHistoryRow(bucket) }
                    }
                }
            }

            // CLAUDE-ADDED: Audiobook counterpart of "Current Read's Sessions" above -- same
            // day-by-day breakdown, just time listened + percent listened instead of duration/wpm/
            // percent read (audio has no reading-speed equivalent, see DailyListeningBucket).
            state.currentListeningDailyHistory.takeIf { it.isNotEmpty() }?.let { history ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Current Listen's Sessions", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.sortedByDescending { it.date }.forEach { bucket -> DailyListeningHistoryRow(bucket) }
                }
            }

            // CLAUDE-ADDED: Audiobook counterpart of "Completed Reads" above -- only the single
            // latest listen-through, same as that section's own single-card treatment.
            state.lastCompletedListen?.let { completedListen ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Completed Listens", style = MaterialTheme.typography.titleSmall)
                    IconButton(
                        onClick = { pendingDeleteCompletedListenId = completedListen.id },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete completed listen",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Completed: ${formatTimestamp(completedListen.completedAt)}")
                    // CLAUDE-ADDED: There's no per-listen "seconds" field the way StoredCompletedReadTime
                    // has (accumulatedSeconds is a lifetime total, never reset per-listen) -- summing this
                    // run's own daily buckets gives actual time spent listening, unlike completedAt minus
                    // startedAt, which would count idle/paused wall-clock time too.
                    val listenedSeconds = completedListen.dailyHistory?.sumOf { it.seconds } ?: 0.0
                    Chip("Duration: ${formatDuration(listenedSeconds)}")
                }
                completedListen.dailyHistory?.takeIf { it.isNotEmpty() }?.let { history ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        history.sortedByDescending { it.date }.forEach { bucket -> DailyListeningHistoryRow(bucket) }
                    }
                }
            }

            if (book.publisher != null) {
                ChipSection(title = "Publisher") { Chip(book.publisher) }
            }

            if (book.isbn != null || book.asin != null || book.calibreId != null || book.uuid != null) {
                ChipSection(title = "Identifiers") {
                    book.isbn?.let { Chip("ISBN: $it") }
                    book.asin?.let { Chip("ASIN: $it") }
                    book.calibreId?.let { Chip("Calibre ID: $it") }
                    book.uuid?.let { uuid ->
                        Chip("UUID: $uuid", trailing = {
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(uuid)) },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        })
                    }
                }
            }

            if (book.language != null) {
                ChipSection(title = "Language") { Chip(book.language) }
            }
        }
        }

        coverImage?.let { image ->
            ImageViewerOverlay(image = image, onClose = { coverImage = null })
        }

        if (showTimerSheet) {
            ReadingTimerSheet(
                state = ReadingTimerUiState(
                    loading = false,
                    accumulatedSeconds = state.totalReadingSeconds ?: 0.0,
                    wpm = state.wpm,
                    secondsLeft = state.secondsLeft,
                    completedReads = state.completedReads,
                    isComic = isComic
                ),
                onReset = { save -> viewModel.resetCurrentRead(save) },
                onDeleteCompleted = { id -> viewModel.deleteCompletedRead(id) },
                onDismiss = { showTimerSheet = false },
                comicSecondsLeft = comicSecondsLeft
            )
        }

        if (showTrackingSheet) {
            TrackingSheet(
                state = trackingState,
                onSearchQueryChange = trackingViewModel::onSearchQueryChange,
                onSearch = trackingViewModel::search,
                onLink = trackingViewModel::link,
                onUnlink = trackingViewModel::unlink,
                onToggleSync = trackingViewModel::toggleSync,
                onStatusChange = trackingViewModel::setStatus,
                onScoreChange = trackingViewModel::setScore,
                onProgressChange = trackingViewModel::setProgress,
                onRepeatChange = trackingViewModel::setRepeat,
                onStartedAtChange = trackingViewModel::setStartedAt,
                onCompletedAtChange = trackingViewModel::setCompletedAt,
                onDismiss = { showTrackingSheet = false }
            )
        }

        pendingDeleteCompletedReadId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteCompletedReadId = null },
                title = { Text("Delete completed read?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCompletedRead(id)
                        pendingDeleteCompletedReadId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteCompletedReadId = null }) { Text("Cancel") }
                }
            )
        }

        pendingDeleteCompletedListenId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteCompletedListenId = null },
                title = { Text("Delete completed listen?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCompletedListen(id)
                        pendingDeleteCompletedListenId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteCompletedListenId = null }) { Text("Cancel") }
                }
            )
        }

    }
}

/** Fetches the book's cover art through Coil's own cookie-jarred loader (same one AsyncImage above
 *  already uses, see IshiReaderApp.newImageLoader) rather than a bare HTTP client, since cover URLs
 *  require the app's session cookie -- and decodes it to a plain [Bitmap] so it can be shown in the
 *  same [ImageViewerOverlay] the reader uses for in-book images. */
private suspend fun loadCoverAsTappedImage(context: Context, book: Book): TappedImage? =
    withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context).data(book.cover).allowHardware(false).build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
            TappedImage(bitmap, book.title)
        } catch (e: Exception) {
            null
        }
    }

@Composable
private fun ProgressDial(percent: Double) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        CircularProgressIndicator(
            progress = { (percent / 100.0).toFloat() },
            modifier = Modifier.fillMaxSize()
        )
        Text(formatPercent(percent / 100.0), style = MaterialTheme.typography.labelSmall)
    }
}

/** One day's reading within a completed run's dailyHistory -- mirrors StatefulBookSheet.tsx's
 *  sessionsList rows (date/duration/pace/percent), just with this screen's Chip styling instead of
 *  that panel's baseline-aligned grid. wpm/percent are derived here, never stored precomputed. */
@Composable
private fun DailyHistoryRow(bucket: DailyReadingBucket) {
    val wpm = if (bucket.seconds > 0) (bucket.words / (bucket.seconds / 60)).toInt() else null
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDateOnly(bucket.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(formatDuration(bucket.seconds), style = MaterialTheme.typography.labelSmall)
        Text(wpm?.let { "$it wpm" } ?: "—", style = MaterialTheme.typography.labelSmall)
        Text(formatPercent(bucket.progressionDelta), style = MaterialTheme.typography.labelSmall)
    }
}

/** Audiobook counterpart of [DailyHistoryRow] -- same date + percent columns, but time listened
 *  instead of duration and no wpm/pace column (see DailyListeningBucket -- there's no reading-speed
 *  equivalent for audio, progress is already exact via the position locator's totalProgression). */
@Composable
private fun DailyListeningHistoryRow(bucket: DailyListeningBucket) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDateOnly(bucket.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(formatDuration(bucket.seconds), style = MaterialTheme.typography.labelSmall)
        Text(formatPercent(bucket.progressionDelta), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatTimestamp(epochMillis: Double): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(epochMillis.toLong()))

private fun formatDateOnly(dateStr: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parsed!!)
} catch (e: Exception) {
    dateStr
}

/** Same h/m/s breakdown as the site's formatFullReadingTime -- every unit below the largest present
 *  one is always shown (no rounding to a single unit). */
private fun formatDuration(totalSeconds: Double): String {
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

/** h/m only, rounded to the nearest minute -- matches the site's formatEstimatedTime. Unlike
 *  [formatDuration]'s exact elapsed-time readouts, "time left in book" is inherently approximate
 *  (derived from a WPM estimate), so showing seconds would imply false precision. */
private fun formatEstimatedTime(totalSeconds: Double): String {
    val totalMinutes = Math.round(totalSeconds / 60.0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0L) "${minutes}m" else "${hours}h ${minutes}m"
}

/** Drops a trailing ".0" for a whole-number score (AniList's POINT_100/POINT_10/POINT_5/POINT_3
 *  formats are always whole numbers already) but keeps one decimal place for POINT_10_DECIMAL. */
private fun Double.formatScore(): String =
    if (this == Math.floor(this)) toInt().toString() else "%.1f".format(this)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataChips(book: Book, pageCount: Int?) {
    val chips = buildList {
        book.addedAt?.let { add("Added" to DateFormat.getDateInstance().format(Date(it.toLong()))) }
        book.modified?.let { add("Modified" to it) }
        book.published?.let { add("Published" to it) }
        book.fileSize?.let { add("Size" to it) }
        // CLAUDE-ADDED: 0 is a real but uninformative result for a pathological empty-text book --
        // excluded the same way the website's own !!pageCount check drops it.
        pageCount?.takeIf { it > 0 }?.let { add("# of pages" to it.toString()) }
        // CLAUDE-ADDED: Only ever populated for audiobooks (see Book.duration) -- shown here
        // immediately from the library list response, unlike the dial-adjacent "Length" line which
        // waits on a separate manifest round trip (see BookDetailViewModel.totalListeningDurationSeconds).
        book.duration?.takeIf { it > 0 }?.let { add("Length" to formatDuration(it)) }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (label, value) -> Chip("$label: $value") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun Chip(text: String, trailing: (@Composable () -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
            trailing?.invoke()
        }
    }
}
