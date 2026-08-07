package com.ishireader.app.audiobook

import com.ishireader.app.data.model.StoredCompletedListen
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.ListeningTimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

data class ListeningTimeUiState(
    val loading: Boolean = true,
    val accumulatedSeconds: Double = 0.0,
    val completedListens: List<StoredCompletedListen> = emptyList()
)

/**
 * Ports the website's useListeningTimer/listeningTimeReducer to a plain controller driven by the
 * player's own play/pause events instead of Redux actions -- see [onPlayingChanged], called from
 * AudiobookPlayerViewModel's Player.Listener. Elapsed time is measured by wall-clock delta between
 * ticks (not "+1 per tick"), the same reasoning the website's own comment gives: a backgrounded
 * process still needs correct elapsed time even if the ticker itself gets throttled or coalesced.
 */
class ListeningTimeTracker(
    private val scope: CoroutineScope,
    private val repository: ListeningTimeRepository
) {
    private companion object {
        const val PERSIST_INTERVAL_MS = 30_000L
    }

    var state: ListeningTimeUiState = ListeningTimeUiState()
        private set

    private lateinit var manifestUrl: String
    private var startedAt: Double? = null
    private var lastAccountedAtMs: Long? = null
    private var lastPersistMs: Long = 0L

    suspend fun start(manifestUrl: String) {
        this.manifestUrl = manifestUrl
        val listeningTime = repository.getListeningTime(manifestUrl).dataOrNull()
        val completedListens = repository.getCompletedListens(manifestUrl).dataOrNull() ?: emptyList()
        startedAt = listeningTime?.startedAt
        state = ListeningTimeUiState(
            loading = false,
            accumulatedSeconds = listeningTime?.accumulatedSeconds ?: 0.0,
            completedListens = completedListens.sortedByDescending { it.completedAt }
        )
    }

    /** Call on every tick while the player is alive (a couple times a second is plenty) -- only
     *  actually accounts elapsed time while [isPlaying], mirroring the website's playerStatus gate. */
    fun onTick(isPlaying: Boolean) {
        if (!isPlaying) {
            lastAccountedAtMs = null
            return
        }
        if (!::manifestUrl.isInitialized) return

        ensureListenStarted()

        val now = System.currentTimeMillis()
        val last = lastAccountedAtMs
        lastAccountedAtMs = now
        if (last == null) return

        val elapsedSeconds = (now - last) / 1000.0
        if (elapsedSeconds <= 0) return
        state = state.copy(accumulatedSeconds = state.accumulatedSeconds + elapsedSeconds)

        if (now - lastPersistMs >= PERSIST_INTERVAL_MS) {
            lastPersistMs = now
            flush()
        }
    }

    /** No-op once a listen-through is already marked started -- safe to call on every play event. */
    private fun ensureListenStarted() {
        if (startedAt != null) return
        startedAt = System.currentTimeMillis().toDouble()
        flush()
    }

    fun flush() {
        if (!::manifestUrl.isInitialized) return
        val seconds = state.accumulatedSeconds
        val started = startedAt
        scope.launch { repository.setListeningTime(manifestUrl, seconds, started) }
    }

    /** Archives the current listen-through as a completed listen (mirrors completeListen in the
     *  website's listeningTimeReducer) -- called both automatically when playback reaches the end
     *  of the track and manually from a "Mark as Finished" affordance. */
    fun completeListen() {
        if (!::manifestUrl.isInitialized) return
        val started = startedAt ?: System.currentTimeMillis().toDouble()
        val item = StoredCompletedListen(
            id = UUID.randomUUID().toString(),
            startedAt = started,
            completedAt = System.currentTimeMillis().toDouble()
        )
        state = state.copy(completedListens = listOf(item) + state.completedListens)
        startedAt = null
        scope.launch {
            repository.saveCompletedListen(manifestUrl, item)
            repository.setListeningTime(manifestUrl, state.accumulatedSeconds, null)
        }
    }

    fun deleteCompletedListen(id: String) {
        if (!::manifestUrl.isInitialized) return
        state = state.copy(completedListens = state.completedListens.filterNot { it.id == id })
        scope.launch { repository.deleteCompletedListen(manifestUrl, id) }
    }
}
