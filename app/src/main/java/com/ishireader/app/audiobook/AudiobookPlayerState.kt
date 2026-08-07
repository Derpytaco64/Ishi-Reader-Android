package com.ishireader.app.audiobook

/** Mirrors the website's sleep timer affordance -- a fixed duration, or a boundary the ticker
 *  watches for (see AudiobookPlayerActivity's checkSleepTimerBoundary). */
enum class SleepTimerMode { OFF, DURATION, END_OF_CHAPTER, END_OF_BOOK }

data class AudiobookPlayerUiState(
    val loading: Boolean = true,
    val loadingMessage: String = "Loading…",
    val error: String? = null,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val chapters: List<AudiobookChapter> = emptyList(),
    val currentChapterIndex: Int = -1,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val skipIntervalSeconds: Int = 10,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF,
    val sleepTimerRemainingMs: Long? = null,
    val listeningTime: ListeningTimeUiState = ListeningTimeUiState()
) {
    val currentChapterTitle: String?
        get() = chapters.getOrNull(currentChapterIndex)?.title
}

val SKIP_INTERVAL_OPTIONS_SECONDS = listOf(5, 10, 15, 30, 45, 60)
val PLAYBACK_SPEED_PRESETS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
val SLEEP_TIMER_PRESET_MINUTES = listOf(5, 10, 15, 30, 45, 60)
