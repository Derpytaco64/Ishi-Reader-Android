package com.ishireader.app.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage

/**
 * Audible-style "now playing" screen for a downloaded audiobook -- cover art, transport controls,
 * a chapter-ticked scrub bar, playback speed/skip-interval/volume, and sheets for the chapter
 * list and sleep timer. Mirrors the website's StatefulPlayer, minus the remote-cast affordance
 * (no Chromecast/AirPlay equivalent on this platform) and the iOS-only volume-hiding quirk
 * (Android always has a usable in-app volume slider).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    state: AudiobookPlayerUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSkipIntervalChange: (Int) -> Unit,
    onChapterSelected: (Int) -> Unit,
    onSleepTimerDuration: (Long) -> Unit,
    onSleepTimerBoundary: (SleepTimerMode) -> Unit,
    onSleepTimerCancel: () -> Unit,
    onMarkFinished: () -> Unit,
    onDeleteCompletedListen: (String) -> Unit
) {
    var chapterSheetOpen by remember { mutableStateOf(false) }
    var sleepTimerSheetOpen by remember { mutableStateOf(false) }
    var listeningTimerSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { listeningTimerSheetOpen = true }) {
                        Icon(Icons.Filled.Timer, contentDescription = "Listening timer")
                    }
                    IconButton(onClick = { sleepTimerSheetOpen = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerMode != SleepTimerMode.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { chapterSheetOpen = true }, enabled = state.chapters.isNotEmpty()) {
                        Icon(Icons.Filled.List, contentDescription = "Chapters")
                    }
                }
            )
        }
    ) { padding ->
        if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    TextButton(onClick = onBack) { Text("Go back") }
                }
            }
            return@Scaffold
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(state.loadingMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = state.bookTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                state.bookTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.bookAuthor.isNotBlank()) {
                Text(
                    state.bookAuthor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.currentChapterTitle?.let { chapterTitle ->
                Text(
                    chapterTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            ProgressSection(state = state, onSeekFraction = onSeekFraction)

            Spacer(Modifier.height(12.dp))

            TransportRow(
                state = state,
                onPlayPause = onPlayPause,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter
            )

            Spacer(Modifier.height(16.dp))

            SecondaryControlsRow(
                state = state,
                onSpeedChange = onSpeedChange,
                onVolumeChange = onVolumeChange,
                onSkipIntervalChange = onSkipIntervalChange
            )
        }
    }

    if (chapterSheetOpen) {
        ChapterListSheet(
            chapters = state.chapters,
            currentIndex = state.currentChapterIndex,
            onSelect = { index -> onChapterSelected(index); chapterSheetOpen = false },
            onDismiss = { chapterSheetOpen = false }
        )
    }

    if (sleepTimerSheetOpen) {
        SleepTimerSheet(
            state = state,
            onDuration = onSleepTimerDuration,
            onBoundary = onSleepTimerBoundary,
            onCancel = onSleepTimerCancel,
            onDismiss = { sleepTimerSheetOpen = false }
        )
    }

    if (listeningTimerSheetOpen) {
        AudiobookListeningTimerSheet(
            state = state.listeningTime,
            onMarkFinished = onMarkFinished,
            onDeleteCompleted = onDeleteCompletedListen,
            onDismiss = { listeningTimerSheetOpen = false }
        )
    }
}

@Composable
private fun ProgressSection(state: AudiobookPlayerUiState, onSeekFraction: (Float) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val liveFraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val fraction = dragFraction ?: liveFraction

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            if (state.durationMs > 0 && state.chapters.size > 1) {
                Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    val trackColor = Color.Gray.copy(alpha = 0.6f)
                    state.chapters.drop(1).forEach { chapter ->
                        val x = ((chapter.startSeconds * 1000.0 / state.durationMs).toFloat().coerceIn(0f, 1f)) * size.width
                        drawLine(color = trackColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 3f)
                    }
                }
            }
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let(onSeekFraction)
                    dragFraction = null
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(
                "-" + formatClock((state.durationMs - state.positionMs).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: AudiobookPlayerUiState,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousChapter, enabled = state.chapters.isNotEmpty()) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = onSkipBack) {
            Icon(Icons.Filled.FastRewind, contentDescription = "Skip back ${state.skipIntervalSeconds}s", modifier = Modifier.size(28.dp))
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        IconButton(onClick = onSkipForward) {
            Icon(Icons.Filled.FastForward, contentDescription = "Skip forward ${state.skipIntervalSeconds}s", modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onNextChapter, enabled = state.chapters.isNotEmpty()) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SecondaryControlsRow(
    state: AudiobookPlayerUiState,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSkipIntervalChange: (Int) -> Unit
) {
    var speedMenuOpen by remember { mutableStateOf(false) }
    var skipMenuOpen by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { speedMenuOpen = true }) { Text("${formatSpeed(state.playbackSpeed)}x") }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                PLAYBACK_SPEED_PRESETS.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("${formatSpeed(speed)}x") },
                        onClick = { onSpeedChange(speed); speedMenuOpen = false }
                    )
                }
            }
        }

        Box {
            TextButton(onClick = { skipMenuOpen = true }) { Text("${state.skipIntervalSeconds}s skip") }
            DropdownMenu(expanded = skipMenuOpen, onDismissRequest = { skipMenuOpen = false }) {
                SKIP_INTERVAL_OPTIONS_SECONDS.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text("${seconds}s") },
                        onClick = { onSkipIntervalChange(seconds); skipMenuOpen = false }
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = state.volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListSheet(
    chapters: List<AudiobookChapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        chapter.title,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Text(
                        formatClock((chapter.startSeconds * 1000).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    state: AudiobookPlayerUiState,
    onDuration: (Long) -> Unit,
    onBoundary: (SleepTimerMode) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Sleep Timer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.sleepTimerMode != SleepTimerMode.OFF) {
                Text(
                    when (state.sleepTimerMode) {
                        SleepTimerMode.DURATION -> "Stopping in ${formatClock(state.sleepTimerRemainingMs ?: 0L)}"
                        SleepTimerMode.END_OF_CHAPTER -> "Stopping at the end of this chapter"
                        SleepTimerMode.END_OF_BOOK -> "Stopping at the end of the book"
                        SleepTimerMode.OFF -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { onCancel(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel Timer")
                }
                Spacer(Modifier.height(8.dp))
            }

            SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
                TextButton(onClick = { onDuration(minutes * 60_000L); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("$minutes minutes", modifier = Modifier.fillMaxWidth())
                }
            }
            TextButton(
                onClick = { onBoundary(SleepTimerMode.END_OF_CHAPTER); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.chapters.isNotEmpty()
            ) { Text("End of chapter", modifier = Modifier.fillMaxWidth()) }
            TextButton(onClick = { onBoundary(SleepTimerMode.END_OF_BOOK); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("End of book", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) speed.toLong().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.')

/** h:mm:ss, or m:ss under an hour -- same shape as the reader's own formatDuration functions. */
private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
