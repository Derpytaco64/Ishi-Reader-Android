package com.ishireader.app.ui.bookdetail

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.model.percentFromLocator
import com.ishireader.app.ui.reader.NoteEditorDialog
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    book: Book,
    viewModel: BookDetailViewModel,
    onBackClick: () -> Unit,
    onReadClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var editingNote by remember { mutableStateOf<StoredNote?>(null) }

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
                        .aspectRatio(2f / 3f)
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
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

                    state.percentRead?.let { percent ->
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            ProgressDial(percent = percent)
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

            MetadataChips(book = book)

            if (book.tags.isNotEmpty()) {
                ChipSection(title = "Genres") { book.tags.forEach { tag -> Chip(tag) } }
            }

            book.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Description", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }

            // CLAUDE-ADDED: Point-in-time snapshot of this book's own reading pace, matching
            // StatefulBookSheet.tsx's "Reading Timer" section -- not the reader's own live ticking
            // counter (see BookDetailViewModel.refresh's own comment). Hidden entirely if there's
            // no reading time logged yet, same as the site (readingStats.totalSeconds > 0 gate).
            if ((state.totalReadingSeconds ?: 0.0) > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Reading Timer", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Time read: ${formatDuration(state.totalReadingSeconds!!)}")
                    state.wpm?.let { Chip("Pace: $it wpm") }
                    state.secondsLeft?.let { Chip("Time left: ${formatEstimatedTime(it)}") }
                }
            }

            // CLAUDE-ADDED: The most recent entry from the reader's own Completed tab, matching
            // StatefulBookSheet.tsx's "Completed Read" card -- only the single latest run, not the
            // full history list the reader's Completed tab shows.
            state.lastCompletedRead?.let { completedRead ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Completed Read", style = MaterialTheme.typography.titleSmall)
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

            if (state.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.notes.sortedByDescending { it.createdAt }.forEach { note ->
                        NoteCard(note = note, onClick = { editingNote = note })
                    }
                }
            }

            if (book.publisher != null) {
                ChipSection(title = "Publisher") { Chip(book.publisher) }
            }

            if (book.isbn != null || book.calibreId != null || book.uuid != null) {
                ChipSection(title = "Identifiers") {
                    book.isbn?.let { Chip("ISBN: $it") }
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

        editingNote?.let { note ->
            NoteEditorDialog(
                initialText = note.text,
                isNew = false,
                onSave = { text ->
                    viewModel.updateNoteText(note.id, text)
                    editingNote = null
                },
                onDelete = {
                    viewModel.deleteNote(note.id)
                    editingNote = null
                },
                onDismiss = { editingNote = null }
            )
        }
    }
}

@Composable
private fun ProgressDial(percent: Double) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        CircularProgressIndicator(
            progress = { (percent / 100.0).toFloat() },
            modifier = Modifier.fillMaxSize()
        )
        Text("${percent}%", style = MaterialTheme.typography.labelSmall)
    }
}

/** One day's reading within a completed run's dailyHistory -- mirrors StatefulBookSheet.tsx's
 *  sessionsList rows (date/duration/pace/percent), just with this screen's Chip styling instead of
 *  that panel's baseline-aligned grid. wpm/percent are derived here, never stored precomputed. */
@Composable
private fun DailyHistoryRow(bucket: DailyReadingBucket) {
    val wpm = if (bucket.seconds > 0) (bucket.words / (bucket.seconds / 60)).toInt() else null
    val percent = (bucket.progressionDelta * 100).takeIf { it.isFinite() }?.toInt() ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDateOnly(bucket.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(formatDuration(bucket.seconds), style = MaterialTheme.typography.labelSmall)
        Text(wpm?.let { "$it wpm" } ?: "—", style = MaterialTheme.typography.labelSmall)
        Text("$percent%", style = MaterialTheme.typography.labelSmall)
    }
}

/** One note, matching the site's per-note rendering: the highlighted passage it was attached to
 *  (if any), the note's own text, then chapter/percent/timestamp chips. Tapping it opens the same
 *  edit/delete NoteEditorDialog the reader's own annotations panel uses -- mirrors
 *  StatefulBookSheet.tsx's inline "Edit note" affordance on this same read-only list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(note: StoredNote, onClick: () -> Unit) {
    val quote = (note.locator?.jsonObject
        ?.get("text")?.jsonObject
        ?.get("highlight") as? JsonPrimitive)?.contentOrNull
    val percent = percentFromLocator(note.locator)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            quote?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                note.chapterTitle?.let { Chip(it) }
                percent?.let { Chip("$it%") }
                Chip(formatTimestamp(note.createdAt))
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataChips(book: Book) {
    val chips = buildList {
        book.addedAt?.let { add("Added" to DateFormat.getDateInstance().format(Date(it.toLong()))) }
        book.modified?.let { add("Modified" to it) }
        book.published?.let { add("Published" to it) }
        book.fileSize?.let { add("Size" to it) }
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
