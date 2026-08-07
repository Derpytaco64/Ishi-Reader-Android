@file:OptIn(UnstableApi::class)

package com.ishireader.app.audiobook

import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.ui.theme.IshiReaderTheme
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream

/**
 * Audible-style player for a downloaded audiobook -- the audio counterpart to ReaderActivity, but
 * plain Compose end-to-end (no Fragment/WebView navigator involved) driving an ExoPlayer hosted in
 * [AudiobookPlaybackService] via a [MediaController]. All state lives directly on the Activity
 * (mutableStateOf fields, same as ReaderActivity) rather than a ViewModel, since most of it needs
 * either lifecycleScope or a Context anyway (the MediaController connection, Coil artwork
 * fetching) -- see the manifest's matching configChanges on this activity, which is what makes
 * that safe across rotation the same way it already is for ReaderActivity.
 */
class AudiobookPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MANIFEST_URL = "manifest_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_COVER_URL = "cover_url"

        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val PROGRESS_TICK_MS = 250L
    }

    private val app: IshiReaderApp by lazy { application as IshiReaderApp }
    private val audiobookRepository = AudiobookRepository()
    private val listeningTimeTracker by lazy { ListeningTimeTracker(lifecycleScope, app.listeningTimeRepository) }

    private val uiState = mutableStateOf(AudiobookPlayerUiState())

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var trackHref: String = ""
    private var trackType: String? = null
    private var positionRestored = false
    private var lastPositionSaveMs = 0L
    private var lastChapterIndexForSleepTimer = -1

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manifestUrl = intent.getStringExtra(EXTRA_MANIFEST_URL)
        if (manifestUrl == null) {
            Toast.makeText(this, "Missing book manifest URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        uiState.value = uiState.value.copy(
            bookTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            bookAuthor = intent.getStringExtra(EXTRA_AUTHOR).orEmpty(),
            coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        )

        setContent {
            IshiReaderTheme {
                val state by uiState
                AudiobookPlayerScreen(
                    state = state,
                    onBack = { finish() },
                    onPlayPause = ::togglePlayPause,
                    onSkipBack = { skip(-state.skipIntervalSeconds * 1000L) },
                    onSkipForward = { skip(state.skipIntervalSeconds * 1000L) },
                    onPreviousChapter = ::previousChapter,
                    onNextChapter = ::nextChapter,
                    onSeekFraction = ::seekToFraction,
                    onSpeedChange = ::setSpeed,
                    onVolumeChange = ::setVolume,
                    onSkipIntervalChange = { seconds -> uiState.value = uiState.value.copy(skipIntervalSeconds = seconds) },
                    onChapterSelected = ::seekToChapter,
                    onSleepTimerDuration = ::setSleepTimerDuration,
                    onSleepTimerBoundary = ::setSleepTimerBoundary,
                    onSleepTimerCancel = ::cancelSleepTimer,
                    onMarkFinished = ::markFinished,
                    onDeleteCompletedListen = { id -> listeningTimeTracker.deleteCompletedListen(id); syncListeningState() }
                )
            }
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch { openBook(manifestUrl) }
        }
    }

    private suspend fun openBook(manifestUrl: String) {
        uiState.value = uiState.value.copy(loading = true, loadingMessage = "Downloading…")

        val localFile = app.bookDownloadRepository.localFileFor(manifestUrl) ?: run {
            val result = app.bookDownloadRepository.download(manifestUrl) { bytesRead, totalBytes ->
                if (totalBytes > 0) {
                    val percent = ((bytesRead * 100) / totalBytes).toInt()
                    uiState.value = uiState.value.copy(loadingMessage = "Downloading… $percent%")
                }
            }
            when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    uiState.value = uiState.value.copy(loading = false, error = result.message)
                    return
                }
            }
        }

        uiState.value = uiState.value.copy(loadingMessage = "Loading…")

        val manifestInfo = audiobookRepository.fetchManifestInfo(manifestUrl)
        trackHref = manifestInfo?.trackHref ?: manifestUrl.substringAfterLast('/')
        trackType = manifestInfo?.trackType ?: "audio/mp4"
        uiState.value = uiState.value.copy(chapters = manifestInfo?.chapters ?: emptyList())

        val artworkBytes = loadCoverArtworkBytes(uiState.value.coverUrl)
        val mediaController = connectController()
        controller = mediaController

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(localFile))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(uiState.value.bookTitle)
                    .setArtist(uiState.value.bookAuthor)
                    .apply {
                        if (artworkBytes != null) setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    .build()
            )
            .build()

        mediaController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                uiState.value = uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val duration = mediaController.duration.coerceAtLeast(0L)
                        uiState.value = uiState.value.copy(durationMs = duration, loading = false)
                        if (!positionRestored) {
                            positionRestored = true
                            lifecycleScope.launch { restoreSavedPosition(mediaController, manifestUrl, duration) }
                        }
                    }
                    Player.STATE_ENDED -> {
                        listeningTimeTracker.completeListen()
                        syncListeningState()
                        if (uiState.value.sleepTimerMode == SleepTimerMode.END_OF_BOOK) {
                            uiState.value = uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF)
                        }
                    }
                }
            }
        })

        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()

        lifecycleScope.launch {
            listeningTimeTracker.start(manifestUrl)
            syncListeningState()
        }

        startTicker(manifestUrl)
    }

    private suspend fun restoreSavedPosition(controller: MediaController, manifestUrl: String, durationMs: Long) {
        val locatorJson = app.positionRepository.getPosition(manifestUrl)
        val seconds = resumeSecondsFrom(locatorJson, durationMs / 1000.0) ?: return
        val targetMs = (seconds * 1000).toLong().coerceIn(0L, durationMs)
        if (targetMs > 0) controller.seekTo(targetMs)
    }

    private suspend fun loadCoverArtworkBytes(url: String?): ByteArray? {
        if (url == null) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(applicationContext).data(url).allowHardware(false).build()
                val result = applicationContext.imageLoader.execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
                ByteArrayOutputStream().use { stream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun connectController(): MediaController = suspendCancellableCoroutine { cont ->
        val token = SessionToken(this, ComponentName(this, AudiobookPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (cont.isActive) {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }, MoreExecutors.directExecutor())
        cont.invokeOnCancellation { future.cancel(false) }
    }

    private fun startTicker(manifestUrl: String) {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                delay(PROGRESS_TICK_MS)
                val c = controller ?: continue

                val positionMs = c.currentPosition.coerceAtLeast(0L)
                val isPlaying = c.isPlaying
                val chapters = uiState.value.chapters
                val chapterIndex = currentChapterIndexFor(positionMs / 1000.0, chapters)
                uiState.value = uiState.value.copy(positionMs = positionMs, currentChapterIndex = chapterIndex)

                if (uiState.value.sleepTimerMode == SleepTimerMode.END_OF_CHAPTER &&
                    lastChapterIndexForSleepTimer >= 0 && chapterIndex != lastChapterIndexForSleepTimer
                ) {
                    c.pause()
                    uiState.value = uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF)
                }
                lastChapterIndexForSleepTimer = chapterIndex

                listeningTimeTracker.onTick(isPlaying)
                syncListeningState()

                if (isPlaying) {
                    val now = System.currentTimeMillis()
                    if (now - lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS) {
                        lastPositionSaveMs = now
                        savePosition(manifestUrl, positionMs)
                    }
                }
            }
        }
    }

    private fun currentChapterIndexFor(positionSeconds: Double, chapters: List<AudiobookChapter>): Int {
        if (chapters.isEmpty()) return -1
        var idx = 0
        for (i in chapters.indices) {
            if (chapters[i].startSeconds <= positionSeconds) idx = i else break
        }
        return idx
    }

    private fun savePosition(manifestUrl: String, positionMs: Long) {
        val duration = uiState.value.durationMs
        if (duration <= 0) return
        val locator = buildPositionLocator(
            trackHref = trackHref,
            trackType = trackType,
            chapterTitle = uiState.value.currentChapterTitle,
            positionSeconds = positionMs / 1000.0,
            durationSeconds = duration / 1000.0
        )
        lifecycleScope.launch { app.positionRepository.setPosition(manifestUrl, locator) }
    }

    private fun syncListeningState() {
        uiState.value = uiState.value.copy(listeningTime = listeningTimeTracker.state)
    }

    private fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    private fun skip(deltaMs: Long) {
        val c = controller ?: return
        val duration = uiState.value.durationMs.coerceAtLeast(0L)
        val target = (c.currentPosition + deltaMs).coerceIn(0L, duration)
        c.seekTo(target)
        uiState.value = uiState.value.copy(positionMs = target)
    }

    private fun previousChapter() {
        val c = controller ?: return
        val chapters = uiState.value.chapters
        if (chapters.isEmpty()) {
            c.seekTo(0L)
            uiState.value = uiState.value.copy(positionMs = 0L)
            return
        }
        val idx = uiState.value.currentChapterIndex.coerceAtLeast(0)
        val currentStart = chapters.getOrNull(idx)?.startSeconds ?: 0.0
        val positionSeconds = c.currentPosition / 1000.0
        // More than 3s into the current chapter restarts it (Audible/podcast-app convention);
        // otherwise it jumps to the previous one.
        val targetIndex = if (positionSeconds - currentStart > 3.0) idx else (idx - 1).coerceAtLeast(0)
        seekToChapter(targetIndex)
    }

    private fun nextChapter() {
        val chapters = uiState.value.chapters
        val idx = uiState.value.currentChapterIndex
        if (chapters.isEmpty() || idx >= chapters.size - 1) {
            val c = controller ?: return
            val duration = uiState.value.durationMs
            c.seekTo(duration)
            uiState.value = uiState.value.copy(positionMs = duration)
            return
        }
        seekToChapter(idx + 1)
    }

    private fun seekToChapter(index: Int) {
        val c = controller ?: return
        val chapter = uiState.value.chapters.getOrNull(index) ?: return
        val targetMs = (chapter.startSeconds * 1000).toLong().coerceIn(0L, uiState.value.durationMs)
        c.seekTo(targetMs)
        uiState.value = uiState.value.copy(positionMs = targetMs, currentChapterIndex = index)
    }

    private fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = uiState.value.durationMs
        val target = (fraction * duration).toLong().coerceIn(0L, duration)
        c.seekTo(target)
        uiState.value = uiState.value.copy(positionMs = target)
    }

    private fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        uiState.value = uiState.value.copy(playbackSpeed = speed)
    }

    private fun setVolume(volume: Float) {
        controller?.volume = volume
        uiState.value = uiState.value.copy(volume = volume)
    }

    private fun setSleepTimerDuration(durationMs: Long) {
        sleepTimerJob?.cancel()
        uiState.value = uiState.value.copy(sleepTimerMode = SleepTimerMode.DURATION, sleepTimerRemainingMs = durationMs)
        sleepTimerJob = lifecycleScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                uiState.value = uiState.value.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L))
            }
            controller?.pause()
            uiState.value = uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF, sleepTimerRemainingMs = null)
        }
    }

    private fun setSleepTimerBoundary(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        lastChapterIndexForSleepTimer = uiState.value.currentChapterIndex
        uiState.value = uiState.value.copy(sleepTimerMode = mode, sleepTimerRemainingMs = null)
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        uiState.value = uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF, sleepTimerRemainingMs = null)
    }

    private fun markFinished() {
        listeningTimeTracker.completeListen()
        syncListeningState()
    }

    override fun onPause() {
        super.onPause()
        listeningTimeTracker.flush()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        sleepTimerJob?.cancel()
        listeningTimeTracker.flush()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
