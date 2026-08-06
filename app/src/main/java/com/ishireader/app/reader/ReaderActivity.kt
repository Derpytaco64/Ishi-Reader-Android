@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.reader

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.R
import com.ishireader.app.data.model.ReaderSettings
import com.ishireader.app.data.model.toEpubPreferences
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.ui.reader.AnnotationsPanelSheet
import com.ishireader.app.ui.reader.HighlightEditDialog
import com.ishireader.app.ui.reader.NoteEditorDialog
import com.ishireader.app.ui.reader.ReaderSettingsSheet
import com.ishireader.app.ui.reader.ReadingTimerSheet
import com.ishireader.app.ui.theme.IshiReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/**
 * Downloads a book from the Ishi-Read server (see BookDownloadRepository -- the Go readium
 * server only streams an exploded manifest, it has no raw-file endpoint of its own, so the
 * actual bytes come from the Next.js app's /api/books/download route) into local storage, then
 * renders it with the Readium Kotlin toolkit's EPUB navigator from that local file. Opening a
 * local asset is the well-tested path in the toolkit; streaming a remote RWPM manifest directly
 * (the previous approach here) is fragile and has no offline support. Reading position still
 * syncs back to /api/userdata/position, keyed by the original manifestUrl string.
 *
 * Navigator setup (EpubNavigatorFactory -> createFragmentFactory -> instantiate) matches the
 * documented 3.1.x pattern at https://readium.org/kotlin-toolkit/3.1.2/guides/navigator/preferences/ --
 * confirmed against the docs site since the toolkit's 3.0 release replaced the old
 * EpubNavigatorFragment.createFactory() API with this two-step factory. AssetRetriever/
 * PublicationOpener signatures were not independently re-verified against 3.1.1's exact release
 * notes, so if either of those throws an unresolved-reference error, check
 * https://github.com/readium/kotlin-toolkit/blob/develop/docs/migration-guide.md for what changed.
 */
class ReaderActivity : FragmentActivity() {

    companion object {
        const val EXTRA_MANIFEST_URL = "manifest_url"
        const val EXTRA_TITLE = "title"
        private const val NAVIGATOR_FRAGMENT_TAG = "epub_navigator"
    }

    private val app: IshiReaderApp by lazy { application as IshiReaderApp }

    private lateinit var progressOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var composeOverlay: ComposeView

    /** Plain (non-remember) Compose state so it can be mutated from outside the composition --
     *  see setUpSettingsOverlay/applyReaderSettings. Compose's snapshot system observes writes to
     *  this the same way it would a remembered one. */
    private val readerSettingsState = mutableStateOf(ReaderSettings())
    private var navigatorFragment: EpubNavigatorFragment? = null

    private val readingTimerTracker: ReadingTimerTracker by lazy {
        ReadingTimerTracker(lifecycleScope, app.readingTimerRepository, app.completedReadsRepository)
    }
    private val annotationsController: AnnotationsController by lazy {
        AnnotationsController(lifecycleScope, app.annotationsRepository, app.notesRepository)
    }

    /** Plain mutableStateOf, same reasoning as readerSettingsState -- these are written from
     *  outside the composition (the selection ActionMode callback, the decoration-tap listener). */
    private val pendingNewNoteLocator = mutableStateOf<Locator?>(null)
    private val activeHighlightEditId = mutableStateOf<String?>(null)
    private val activeNoteEditId = mutableStateOf<String?>(null)

    /** Guards applyPreferencesPreservingPosition against rapid repeated preference submissions
     *  (e.g. dragging a slider) racing each other -- mirrors the website's submitGenerationRef. */
    private var preferencesApplyGeneration = 0

    /** Moon+ Reader-style immersive reading: hidden by default, revealed by a center tap (see
     *  ChromeTapInputListener) -- also drives whether the system bars are shown. */
    private val chromeVisible = mutableStateOf(false)

    private fun setChromeVisible(visible: Boolean) {
        chromeVisible.value = visible
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onResume() {
        super.onResume()
        readingTimerTracker.onResumed()
    }

    override fun onPause() {
        super.onPause()
        readingTimerTracker.onPaused()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        title = intent.getStringExtra(EXTRA_TITLE)

        progressOverlay = findViewById(R.id.reader_progress_overlay)
        progressBar = findViewById(R.id.reader_progress_bar)
        progressText = findViewById(R.id.reader_progress_text)
        composeOverlay = findViewById(R.id.reader_compose_overlay)

        val manifestUrl = intent.getStringExtra(EXTRA_MANIFEST_URL)
        if (manifestUrl == null) {
            Toast.makeText(this, "Missing book manifest URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (savedInstanceState == null) {
            openBook(manifestUrl)
        }
    }

    private fun openBook(manifestUrl: String) {
        showSyncingOverlay()
        lifecycleScope.launch {
            readerSettingsState.value = app.readerPreferencesStore.settings.first()
            val localFile = ensureDownloaded(manifestUrl) ?: return@launch
            showSyncingOverlay()
            openPublication(localFile, manifestUrl)
        }
    }

    /** Returns the local file for this book, downloading it first if it isn't already cached.
     *  Returns null (and finishes the activity) if the download fails. */
    private suspend fun ensureDownloaded(manifestUrl: String): File? {
        app.bookDownloadRepository.localFileFor(manifestUrl)?.let { return it }

        showDownloadProgress()
        val result = app.bookDownloadRepository.download(manifestUrl) { bytesRead, totalBytes ->
            runOnUiThread { updateDownloadProgress(bytesRead, totalBytes) }
        }

        return when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                toastAndFinish(result.message)
                null
            }
        }
    }

    private fun showDownloadProgress() {
        progressOverlay.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressText.setText(R.string.reader_downloading)
    }

    private fun showSyncingOverlay() {
        progressOverlay.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressText.setText(R.string.reader_syncing)
    }

    private fun updateDownloadProgress(bytesRead: Long, totalBytes: Long) {
        if (totalBytes <= 0) {
            progressBar.isIndeterminate = true
            return
        }
        progressBar.isIndeterminate = false
        val percent = ((bytesRead * 100) / totalBytes).toInt()
        progressBar.progress = percent
        progressText.text = getString(R.string.reader_downloading) + " $percent%"
    }

    private suspend fun openPublication(localFile: File, manifestUrl: String) {
        val url = localFile.toUrl()

        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(contentResolver, httpClient)
        val publicationParser = DefaultPublicationParser(
            context = this,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
        val publicationOpener = PublicationOpener(publicationParser)

        val asset = when (val result = assetRetriever.retrieve(url)) {
            is Try.Success -> result.value
            is Try.Failure -> return toastAndFinish("Couldn't open book: ${result.value}")
        }

        val publication = when (val result = publicationOpener.open(asset, allowUserInteraction = false)) {
            is Try.Success -> result.value
            is Try.Failure -> return toastAndFinish("Couldn't open book: ${result.value}")
        }

        val initialLocator = fetchSavedLocator(manifestUrl)
        showNavigator(publication, initialLocator, manifestUrl)
    }

    /** Reads the best-known position (PositionRepository tries a quick server refresh first, then
     *  falls back to Room -- safe offline) and bridges its kotlinx.serialization JSON into the
     *  org.json shape Locator.fromJSON expects, since Readium's own model classes parse from
     *  org.json. */
    private suspend fun fetchSavedLocator(manifestUrl: String): Locator? {
        val locatorJson = app.positionRepository.getPosition(manifestUrl) ?: return null
        return runCatching { Locator.fromJSON(JSONObject(locatorJson.toString())) }.getOrNull()
    }

    private fun showNavigator(publication: Publication, initialLocator: Locator?, manifestUrl: String) {
        progressOverlay.visibility = View.GONE

        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) != null) return

        val selectionCallback = AnnotationSelectionActionModeCallback(
            scope = lifecycleScope,
            navigatorProvider = { navigatorFragment as? SelectableNavigator },
            onHighlight = { locator -> annotationsController.addHighlight(locator) },
            onNote = { locator -> pendingNewNoteLocator.value = locator }
        )

        val navigatorFactory = EpubNavigatorFactory(publication = publication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = readerSettingsState.value.toEpubPreferences(),
            configuration = EpubNavigatorFragment.Configuration(
                selectionActionModeCallback = selectionCallback
            )
        )
        val fragment = fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)

        supportFragmentManager.commitNow {
            replace(R.id.reader_container, fragment, NAVIGATOR_FRAGMENT_TAG)
        }

        navigatorFragment = fragment as EpubNavigatorFragment
        navigatorFragment!!.currentLocator
            .onEach { locator ->
                savePosition(locator)
                readingTimerTracker.onLocatorChanged(locator)
            }
            .launchIn(lifecycleScope)

        val decorationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                when (event.group) {
                    ANNOTATIONS_GROUP_HIGHLIGHTS -> activeHighlightEditId.value = event.decoration.id
                    ANNOTATIONS_GROUP_NOTES -> activeNoteEditId.value = event.decoration.id
                    else -> return false
                }
                return true
            }
        }
        val decorableNavigator = navigatorFragment as DecorableNavigator
        decorableNavigator.addDecorationListener(ANNOTATIONS_GROUP_HIGHLIGHTS, decorationListener)
        decorableNavigator.addDecorationListener(ANNOTATIONS_GROUP_NOTES, decorationListener)

        val visualNavigator = navigatorFragment as VisualNavigator
        visualNavigator.addInputListener(
            ChromeTapInputListener(visualNavigator) { setChromeVisible(!chromeVisible.value) }
        )
        setChromeVisible(false)

        lifecycleScope.launch {
            readingTimerTracker.start(manifestUrl, publication)
            readingTimerTracker.onResumed()
        }
        lifecycleScope.launch { annotationsController.start(manifestUrl, decorableNavigator) }

        setUpSettingsOverlay()
    }

    /** Always-on-top gear/timer/annotations buttons, plus the sheets/dialogs they open; set up
     *  once the navigator fragment exists, since most actions here need to reach it (submitting
     *  preferences, jumping to a locator, reading the current selection). readerSettingsState and
     *  the pending-note/active-edit fields are all plain mutableStateOf (not remember{}) so
     *  they're the same objects read here and written from outside the composition -- one source
     *  of truth regardless of which side changes it. */
    private fun setUpSettingsOverlay() {
        composeOverlay.setContent {
            IshiReaderTheme {
                var settingsSheetOpen by remember { mutableStateOf(false) }
                var timerSheetOpen by remember { mutableStateOf(false) }
                var annotationsSheetOpen by remember { mutableStateOf(false) }
                val settings by readerSettingsState
                val timerState by readingTimerTracker.state.collectAsState()
                val annotationsState by annotationsController.state.collectAsState()
                val pendingNote by pendingNewNoteLocator
                val editingHighlightId by activeHighlightEditId
                val editingNoteId by activeNoteEditId
                val chromeShown by chromeVisible

                Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = chromeShown,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(8.dp)
                        ) {
                            IconButton(
                                onClick = { annotationsSheetOpen = true },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Bookmarks, contentDescription = "Annotations", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(
                                onClick = { timerSheetOpen = true },
                                modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = "Reading timer", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(
                                onClick = { settingsSheetOpen = true },
                                modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Reader settings", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                if (settingsSheetOpen) {
                    ReaderSettingsSheet(
                        settings = settings,
                        onSettingsChange = ::applyReaderSettings,
                        onDismiss = { settingsSheetOpen = false }
                    )
                }

                if (timerSheetOpen) {
                    ReadingTimerSheet(
                        state = timerState,
                        onReset = { save -> lifecycleScope.launch { readingTimerTracker.reset(save) } },
                        onDeleteCompleted = { id -> lifecycleScope.launch { readingTimerTracker.deleteCompletedRead(id) } },
                        onDismiss = { timerSheetOpen = false }
                    )
                }

                if (annotationsSheetOpen) {
                    AnnotationsPanelSheet(
                        state = annotationsState,
                        onJump = { locator ->
                            navigatorFragment?.go(locator, animated = true)
                            annotationsSheetOpen = false
                        },
                        onBookmarkThisPage = {
                            navigatorFragment?.currentLocator?.value?.let { annotationsController.addBookmark(it) }
                        },
                        onDeleteHighlight = annotationsController::deleteHighlight,
                        onDeleteBookmark = annotationsController::deleteBookmark,
                        onEditNote = annotationsController::updateNoteText,
                        onDeleteNote = annotationsController::deleteNote,
                        onDismiss = { annotationsSheetOpen = false }
                    )
                }

                pendingNote?.let { locator ->
                    NoteEditorDialog(
                        initialText = "",
                        isNew = true,
                        onSave = { text ->
                            annotationsController.addNote(locator, text)
                            pendingNewNoteLocator.value = null
                        },
                        onDelete = null,
                        onDismiss = { pendingNewNoteLocator.value = null }
                    )
                }

                editingHighlightId?.let { id ->
                    HighlightEditDialog(
                        onColorSelected = { color ->
                            annotationsController.updateHighlightColor(id, color)
                            activeHighlightEditId.value = null
                        },
                        onDelete = {
                            annotationsController.deleteHighlight(id)
                            activeHighlightEditId.value = null
                        },
                        onDismiss = { activeHighlightEditId.value = null }
                    )
                }

                editingNoteId?.let { id ->
                    val note = annotationsController.noteById(id)
                    NoteEditorDialog(
                        initialText = note?.text.orEmpty(),
                        isNew = false,
                        onSave = { text ->
                            annotationsController.updateNoteText(id, text)
                            activeNoteEditId.value = null
                        },
                        onDelete = {
                            annotationsController.deleteNote(id)
                            activeNoteEditId.value = null
                        },
                        onDismiss = { activeNoteEditId.value = null }
                    )
                }
            }
        }
    }

    /** Applies a settings change live to the navigator, then persists it -- in that order, so the
     *  reader responds instantly and a failed/slow write never delays what the user sees. */
    private fun applyReaderSettings(updated: ReaderSettings) {
        readerSettingsState.value = updated
        lifecycleScope.launch { applyPreferencesPreservingPosition(updated.toEpubPreferences()) }
        lifecycleScope.launch { app.readerPreferencesStore.save(updated) }
    }

    /**
     * Font size (and lineHeight/margins/spacing) changes reflow the page -- Readium's own
     * post-relayout recovery in EpubNavigatorFragment just clamps the previous *pixel* scroll
     * offset into the new layout, which can land far from the paragraph actually being read on a
     * large change (this is exactly the drift the website's useEpubNavigator.correctPositionAround
     * exists to fix, and Readium's own navigator doesn't do it automatically on either platform).
     * Ports that fix here: capture a content-anchored locator (firstVisibleElementLocator finds
     * the first visible block and anchors it with a DOM/text-quote locator, not a raw scroll
     * fraction) before submitting the new preferences, then re-navigate to it once the WebView has
     * had a moment to reflow. [preferencesApplyGeneration] discards a stale re-navigation if a
     * newer preference change (e.g. another slider tick) has since superseded this one.
     */
    private suspend fun applyPreferencesPreservingPosition(preferences: EpubPreferences) {
        val generation = ++preferencesApplyGeneration
        val anchor = (navigatorFragment as? VisualNavigator)?.firstVisibleElementLocator()
        navigatorFragment?.submitPreferences(preferences)
        if (anchor == null) return
        delay(150)
        if (preferencesApplyGeneration == generation) {
            navigatorFragment?.go(anchor, animated = false)
        }
    }

    private fun savePosition(locator: Locator) {
        val manifestUrl = intent.getStringExtra(EXTRA_MANIFEST_URL) ?: return
        lifecycleScope.launch {
            val locatorJson = Json.parseToJsonElement(locator.toJSON().toString())
            app.positionRepository.setPosition(manifestUrl, locatorJson)
        }
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}

private inline fun FragmentManager.commitNow(body: FragmentTransaction.() -> Unit) {
    beginTransaction().apply(body).commitNow()
}
