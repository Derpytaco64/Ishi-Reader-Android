package com.ishireader.app.ui.bookdetail

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.ishireader.app.data.model.AniListFuzzyDate
import com.ishireader.app.data.model.AniListSearchResult
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

private val STATUS_OPTIONS = listOf("CURRENT", "PLANNING", "COMPLETED", "DROPPED", "PAUSED", "REPEATING")

// Not file-private -- BookDetailScreen's own tracking summary row reuses these two for the same
// status wording and fuzzy-date formatting as this sheet, so the collapsed row and the sheet never
// disagree on how a status/date is worded.
fun statusLabel(status: String) = when (status) {
    "CURRENT" -> "Reading"
    "PLANNING" -> "Planning"
    "COMPLETED" -> "Completed"
    "DROPPED" -> "Dropped"
    "PAUSED" -> "Paused"
    "REPEATING" -> "Rereading"
    else -> status
}

fun AniListFuzzyDate?.label(): String {
    if (this?.year == null) return "Not set"
    val month = month?.toString()?.padStart(2, '0') ?: "??"
    val day = day?.toString()?.padStart(2, '0') ?: "??"
    return "$year-$month-$day"
}

private fun today(): AniListFuzzyDate {
    val cal = Calendar.getInstance()
    return AniListFuzzyDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

/** DatePicker's selectedDateMillis is UTC-midnight-anchored -- converting through a UTC calendar
 *  on both ends keeps the picked y/m/d stable regardless of the device's own timezone offset. */
private fun AniListFuzzyDate.toUtcMillis(): Long? {
    val y = year ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(y, (month ?: 1) - 1, day ?: 1)
    return cal.timeInMillis
}

private fun Long.toFuzzyDate(): AniListFuzzyDate {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = this
    return AniListFuzzyDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

/**
 * Manga-only AniList tracking sheet -- Tachiyomi-style status/score/progress/dates/rereads, opened
 * from BookDetailScreen. Structurally mirrors ReadingTimerSheet/EditUserSheet's own ModalBottomSheet
 * shape. Shows a search/link picker when the book's series isn't linked yet, otherwise the tracking
 * fields themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingSheet(
    state: TrackingUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLink: (AniListSearchResult) -> Unit,
    onUnlink: () -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onScoreChange: (Double) -> Unit,
    onProgressChange: (Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onStartedAtChange: (AniListFuzzyDate?) -> Unit,
    onCompletedAtChange: (AniListFuzzyDate?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(text = "AniList Tracking", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

            when {
                state.isLoading -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                state.link == null -> LinkPickerSection(state, onSearchQueryChange, onSearch, onLink)
                else -> TrackingFieldsSection(
                    state, onToggleSync, onStatusChange, onScoreChange, onProgressChange,
                    onRepeatChange, onStartedAtChange, onCompletedAtChange, onUnlink
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun LinkPickerSection(
    state: TrackingUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLink: (AniListSearchResult) -> Unit
) {
    Text("Link this series to an AniList entry to start syncing progress.", style = MaterialTheme.typography.bodySmall)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search AniList") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onSearch, enabled = !state.isSearching, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (state.isSearching) "…" else "Search")
        }
    }

    if (state.searchResults.isNotEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            items(state.searchResults) { result ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = result.coverImage?.medium,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(36.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(result.title.english ?: result.title.romaji ?: "Untitled", style = MaterialTheme.typography.bodyMedium)
                        val details = listOfNotNull(result.format, result.chapters?.let { "$it ch" }).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onLink(result) }) { Text("Link") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TrackingFieldsSection(
    state: TrackingUiState,
    onToggleSync: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onScoreChange: (Double) -> Unit,
    onProgressChange: (Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onStartedAtChange: (AniListFuzzyDate?) -> Unit,
    onCompletedAtChange: (AniListFuzzyDate?) -> Unit,
    onUnlink: () -> Unit
) {
    val entry = state.media?.mediaListEntry
    val title = state.media?.title?.let { it.english ?: it.romaji } ?: "Linked series"
    Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Sync progress while reading")
        Switch(checked = state.link?.syncEnabled ?: true, onCheckedChange = onToggleSync)
    }

    var statusMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedButton(onClick = { statusMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(statusLabel(entry?.status ?: "PLANNING"))
        }
        DropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
            STATUS_OPTIONS.forEach { status ->
                DropdownMenuItem(text = { Text(statusLabel(status)) }, onClick = {
                    statusMenuExpanded = false
                    onStatusChange(status)
                })
            }
        }
    }

    // CLAUDE-ADDED: Chapter/score/rereads edit together as one Tachiyomi-style group of vertical
    // scroll wheels (Android's own NumberPicker -- swiping up increases the value, down decreases,
    // same direction convention as a time/date picker) sharing a single Save button below, instead
    // of three separate text fields each with their own Save -- see WheelNumberPicker/ScoreWheelPicker.
    var progressDraft by remember(entry?.progress) { mutableStateOf(entry?.progress ?: 0) }
    var scoreDraft by remember(entry?.score) { mutableStateOf(entry?.score ?: 0.0) }
    var repeatDraft by remember(entry?.repeat) { mutableStateOf(entry?.repeat ?: 0) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        WheelNumberPicker(
            label = "Chapter" + (state.media?.chapters?.let { " / $it" } ?: ""),
            value = progressDraft,
            onValueChange = { progressDraft = it },
            min = 0,
            max = maxOf(state.media?.chapters ?: 9999, progressDraft, 1)
        )
        ScoreWheelPicker(
            scoreFormat = state.scoreFormat,
            value = scoreDraft,
            onValueChange = { scoreDraft = it }
        )
        WheelNumberPicker(
            label = "Rereads",
            value = repeatDraft,
            onValueChange = { repeatDraft = it },
            min = 0,
            max = maxOf(repeatDraft, 999)
        )
    }
    Button(
        onClick = {
            onProgressChange(progressDraft)
            onScoreChange(scoreDraft)
            onRepeatChange(repeatDraft)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) { Text("Save") }

    DateRow("Start date", entry?.startedAt, onStartedAtChange)
    DateRow("Completed date", entry?.completedAt, onCompletedAtChange)

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    TextButton(onClick = onUnlink) { Text("Unlink from AniList") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(label: String, value: AniListFuzzyDate?, onChange: (AniListFuzzyDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.label(), style = MaterialTheme.typography.bodySmall)
        }
        Row {
            TextButton(onClick = { showPicker = true }) { Text("Pick") }
            TextButton(onClick = { onChange(today()) }) { Text("Today") }
            TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(it.toFuzzyDate()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** AniList score formats are either a plain integer range (POINT_100/POINT_10/POINT_5/POINT_3) or
 *  one decimal place (POINT_10_DECIMAL, e.g. "7.5") -- [WheelNumberPicker] itself only scrolls
 *  whole numbers, so POINT_10_DECIMAL is driven as tenths internally (0..100) with [displayedValues]
 *  relabeling each tick as its real "x.x" value, then divided back down to a real score on change. A
 *  null/unrecognized format (AniList account not yet loaded, or a future format this app doesn't
 *  know about) falls back to the same 0-10 range POINT_10 uses -- a reasonable middle ground rather
 *  than guessing 0-100. */
@Composable
private fun ScoreWheelPicker(scoreFormat: String?, value: Double, onValueChange: (Double) -> Unit) {
    when (scoreFormat) {
        "POINT_100" -> WheelNumberPicker(
            label = "Score", value = value.roundToInt(), onValueChange = { onValueChange(it.toDouble()) },
            min = 0, max = 100
        )
        "POINT_10_DECIMAL" -> WheelNumberPicker(
            label = "Score", value = (value * 10).roundToInt().coerceIn(0, 100),
            onValueChange = { onValueChange(it / 10.0) },
            min = 0, max = 100,
            displayedValues = remember { Array(101) { "%.1f".format(it / 10.0) } }
        )
        "POINT_5" -> WheelNumberPicker(
            label = "Score", value = value.roundToInt(), onValueChange = { onValueChange(it.toDouble()) },
            min = 0, max = 5
        )
        "POINT_3" -> WheelNumberPicker(
            label = "Score", value = value.roundToInt(), onValueChange = { onValueChange(it.toDouble()) },
            min = 0, max = 3
        )
        else -> WheelNumberPicker(
            label = "Score", value = value.roundToInt(), onValueChange = { onValueChange(it.toDouble()) },
            min = 0, max = 10
        )
    }
}

/** Tachiyomi-style vertical scroll wheel for a bounded integer -- wraps the platform's own
 *  [NumberPicker] (swipe up increases the value, down decreases, same convention as a time/date
 *  spinner) rather than a hand-rolled Compose equivalent, since AndroidView interop gets the
 *  fling/snap physics for free. [min]/[max]/[displayedValues] are only read at creation (the
 *  factory lambda runs once per call site, not per recomposition) -- fine here since every caller's
 *  bounds are effectively fixed for the lifetime of one sheet open; only [value] is kept in sync on
 *  every recomposition, via [NumberPicker.update]'s own value-changed callback flowing back out
 *  through [onValueChange]. */
@Composable
private fun WheelNumberPicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    displayedValues: Array<String>? = null
) {
    val coercedValue = value.coerceIn(min, max)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        AndroidView(
            modifier = Modifier.width(96.dp),
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    minValue = min
                    maxValue = max
                    displayedValues?.let { this.displayedValues = it }
                    wrapSelectorWheel = false
                    this.value = coercedValue
                    setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
                }
            },
            update = { picker -> if (picker.value != coercedValue) picker.value = coercedValue }
        )
    }
}
