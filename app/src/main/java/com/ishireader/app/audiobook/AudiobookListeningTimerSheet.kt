package com.ishireader.app.audiobook

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.StoredCompletedListen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LISTEN_TIMER_TABS = listOf("Timer", "Completed")

/** Audio counterpart to the reader's ReadingTimerSheet -- a live hh:mm:ss total for this book
 *  (lifetime, never reset, see ListeningTimeTracker's doc comment) with a "Mark as Finished"
 *  button instead of Reset/Discard-or-Save (there's nothing to discard: listened time always
 *  counts), and a Completed tab of archived listen-throughs, delete-only like its reading
 *  counterpart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookListeningTimerSheet(
    state: ListeningTimeUiState,
    onMarkFinished: () -> Unit,
    onDeleteCompleted: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Listening Timer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TabRow(selectedTabIndex = tabIndex) {
                LISTEN_TIMER_TABS.forEachIndexed { index, label ->
                    Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(label) })
                }
            }

            when (tabIndex) {
                0 -> TimerTab(state = state, onMarkFinished = onMarkFinished)
                1 -> CompletedTab(items = state.completedListens, onDeleteClick = { pendingDeleteId = it })
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete completed listen?") },
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
private fun TimerTab(state: ListeningTimeUiState, onMarkFinished: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = formatHms(state.accumulatedSeconds),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Total time listened",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onMarkFinished, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Mark as Finished")
        }
    }
}

@Composable
private fun CompletedTab(items: List<StoredCompletedListen>, onDeleteClick: (String) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No completed listens yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(formatDuration(item.completedAt - item.startedAt), style = MaterialTheme.typography.bodyLarge)
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

private fun formatDuration(totalMillis: Double): String {
    val total = (totalMillis / 1000).toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${total}s"
    }
}

private val COMPLETED_LISTEN_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

private fun formatCompletedDate(epochMillis: Double): String =
    Instant.ofEpochMilli(epochMillis.toLong()).atZone(ZoneId.systemDefault()).format(COMPLETED_LISTEN_DATE_FORMAT)
