package com.ishireader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.reader.ReadingTimerUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMER_TABS = listOf("Timer", "Completed")

/**
 * Ports the website's StatefulReadingTimerContainer -- a Timer tab (live hh:mm:ss + WPM + time
 * left + a single Reset button gated behind a Discard/Save confirmation) and a Completed tab
 * (archived runs, delete-only, no edit -- the server has no PUT for these either).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingTimerSheet(
    state: ReadingTimerUiState,
    onReset: (save: Boolean) -> Unit,
    onDeleteCompleted: (id: String) -> Unit,
    onDismiss: () -> Unit,
    /** Page-rate ("pagesRead/timeSpent" ratio, this book only, no rolling sample buffer) time-left
     *  for a comic -- see ReaderActivity/BookDetailScreen's own comicSecondsLeft. Only shown when
     *  [ReadingTimerUiState.isComic], replacing the wpm/word-count-based stat block. */
    comicSecondsLeft: Double? = null
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Reading Timer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TabRow(selectedTabIndex = tabIndex) {
                TIMER_TABS.forEachIndexed { index, label ->
                    Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(label) })
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (tabIndex) {
                0 -> TimerTab(state = state, comicSecondsLeft = comicSecondsLeft, onResetClick = { showResetConfirm = true })
                1 -> CompletedTab(items = state.completedReads, onDeleteClick = { pendingDeleteId = it })
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset timer") },
            text = { Text("Save this reading session before resetting, or discard it?") },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; onReset(true) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false; onReset(false) }) { Text("Discard") }
            }
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete completed read?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteCompleted(id); pendingDeleteId = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TimerTab(state: ReadingTimerUiState, comicSecondsLeft: Double?, onResetClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = formatHms(state.accumulatedSeconds),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // CLAUDE-ADDED: A comic has no words -- state.wpm/secondsLeft are pace-from-wordCount
            // figures that don't apply to it (see ReadingTimerTracker.start's isComic gate), so this
            // shows the page-rate time-left instead of a pace stat, mirroring the website's
            // StatefulReadingTimerContainer isComic branch.
            if (state.isComic) {
                StatBlock(label = "Time left", value = comicSecondsLeft?.let { formatDuration(it) } ?: "--")
            } else {
                StatBlock(label = "WPM", value = state.wpm?.toString() ?: "--")
                StatBlock(label = "Time left", value = state.secondsLeft?.let { formatDuration(it) } ?: "--")
            }
        }
        OutlinedButton(onClick = onResetClick, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Reset")
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompletedTab(items: List<StoredCompletedReadTime>, onDeleteClick: (String) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No completed reading sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.height(320.dp)) {
        items(items, key = { it.id }) { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(formatDuration(item.seconds), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        formatCompletedDate(item.completedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDeleteClick(item.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            HorizontalDivider()
        }
    }
}

private fun formatHms(totalSeconds: Double): String {
    val total = totalSeconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatDuration(totalSeconds: Double): String {
    val total = totalSeconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${total}s"
    }
}

private val COMPLETED_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

private fun formatCompletedDate(epochMillis: Double): String =
    Instant.ofEpochMilli(epochMillis.toLong()).atZone(ZoneId.systemDefault()).format(COMPLETED_DATE_FORMAT)
