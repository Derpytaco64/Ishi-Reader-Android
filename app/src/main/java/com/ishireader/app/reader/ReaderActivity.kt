@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.reader

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ishireader.app.data.model.PositionDisplayAlignment
import com.ishireader.app.data.model.PositionDisplayMode
import com.ishireader.app.data.model.ReaderSettings
import com.ishireader.app.data.model.toEpubPreferences
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.ui.reader.AnnotationsPanelSheet
import com.ishireader.app.ui.reader.HighlightColorPopover
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
import org.readium.r2.navigator.Selection
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Drives HighlightColorPopover -- [anchorRect] is already converted to the ComposeView's local
 *  coordinate space (see ReaderActivity.overlayRectFor), null falls back to centering the popover.
 *  [existingHighlightId] is null when picking a color for a brand-new selection, non-null when
 *  re-coloring/deleting a highlight the user tapped (mirrors the website's SelectionPopover, which
 *  reuses the same swatch row for both, distinguished by pendingSelection.existing). */
private data class PendingHighlightPicker(
    val locator: Locator,
    val anchorRect: RectF?,
    val existingHighlightId: String? = null
)

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
    private lateinit var readerContainer: View

    /** Plain (non-remember) Compose state so it can be mutated from outside the composition --
     *  see setUpSettingsOverlay/applyReaderSettings. Compose's snapshot system observes writes to
     *  this the same way it would a remembered one. */
    private val readerSettingsState = mutableStateOf(ReaderSettings())
    private var navigatorFragment: EpubNavigatorFragment? = null
    private var publication: Publication? = null

    /** Drives the chapter-title header and position indicator -- updated from the navigator's
     *  currentLocator flow alongside savePosition/readingTimerTracker below. */
    private val currentLocatorState = mutableStateOf<Locator?>(null)

    /** Publication.positions() walks every resource to build the position list, so it's computed
     *  once (off the main thread via lifecycleScope) rather than per-recomposition. Null until
     *  that finishes -- the page-count indicator just waits until then. */
    private val totalPositionsState = mutableStateOf<Int?>(null)

    private val readingTimerTracker: ReadingTimerTracker by lazy {
        ReadingTimerTracker(lifecycleScope, app.readingTimerRepository, app.completedReadsRepository)
    }
    private val annotationsController: AnnotationsController by lazy {
        AnnotationsController(lifecycleScope, app.annotationsRepository, app.notesRepository)
    }

    /** Plain mutableStateOf, same reasoning as readerSettingsState -- these are written from
     *  outside the composition (the selection ActionMode callback, the decoration-tap listener). */
    private val pendingNewNoteLocator = mutableStateOf<Locator?>(null)
    private val pendingHighlightColorPicker = mutableStateOf<PendingHighlightPicker?>(null)
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

        // Lock rotation to whatever orientation the reader was opened in -- device tilt no
        // longer flips the page layout underneath the reader; only the explicit rotate button
        // (see toggleOrientationPreservingPosition) does. The manifest already declares
        // configChanges for orientation/screenSize, so this doesn't recreate the Activity.
        requestedOrientation = currentOrientationLock()

        progressOverlay = findViewById(R.id.reader_progress_overlay)
        progressBar = findViewById(R.id.reader_progress_bar)
        progressText = findViewById(R.id.reader_progress_text)
        composeOverlay = findViewById(R.id.reader_compose_overlay)
        readerContainer = findViewById(R.id.reader_container)

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
            applyContainerAppearance(readerSettingsState.value)
            val localFile = ensureDownloaded(manifestUrl) ?: return@launch
            showSyncingOverlay()
            openPublication(localFile, manifestUrl)
        }
    }

    /** [ReaderSettings.verticalMargin] has no Readium preference counterpart -- applied directly
     *  as padding on the navigator's own container view instead (see ReaderSettings' doc comment
     *  for why). That padding is outside the WebView, so it doesn't pick up the page's own
     *  background color automatically -- without this it shows through as a plain white bar
     *  regardless of the active reader theme, so the container's own background is kept in sync
     *  with [ReaderTheme.backgroundHex] too (falling back to white, ReadiumCSS's own default,
     *  when theme is null/"Auto"). Safe to call before the navigator fragment exists -- padding
     *  and background on the container are independent of when its child view gets attached. */
    private fun applyContainerAppearance(settings: ReaderSettings) {
        val px = (settings.verticalMargin * resources.displayMetrics.density).roundToInt()
        readerContainer.setPadding(readerContainer.paddingLeft, px, readerContainer.paddingRight, px)
        readerContainer.setBackgroundColor(android.graphics.Color.parseColor(settings.theme?.backgroundHex ?: "#FFFFFF"))
    }

    /** [Selection.rect]/[DecorableNavigator.OnActivatedEvent.rect] are documented as "in the
     *  coordinate of the navigator view" -- i.e. relative to VisualNavigator.publicationView (the
     *  WebView pager), not this Activity's root or the ComposeView overlay HighlightColorPopover
     *  actually draws into. composeOverlay and the navigator's container are full-size siblings in
     *  the same root FrameLayout, so the only difference between the two coordinate spaces is
     *  publicationView's own on-screen offset within that container (nonzero once vertical margin
     *  padding pushes it down) -- measured fresh each call since that offset can change. */
    private fun overlayRectFor(rect: RectF?): RectF? {
        if (rect == null) return null
        val publicationView = (navigatorFragment as? VisualNavigator)?.publicationView ?: return null

        val navigatorLocation = IntArray(2)
        publicationView.getLocationInWindow(navigatorLocation)
        val overlayLocation = IntArray(2)
        composeOverlay.getLocationInWindow(overlayLocation)

        val dx = (navigatorLocation[0] - overlayLocation[0]).toFloat()
        val dy = (navigatorLocation[1] - overlayLocation[1]).toFloat()
        return RectF(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
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
        this.publication = publication
        lifecycleScope.launch { totalPositionsState.value = publication.positions().size }

        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) != null) return

        val selectionCallback = AnnotationSelectionActionModeCallback(
            scope = lifecycleScope,
            navigatorProvider = { navigatorFragment as? SelectableNavigator },
            onHighlight = { selection: Selection ->
                pendingHighlightColorPicker.value = PendingHighlightPicker(
                    locator = selection.locator,
                    anchorRect = overlayRectFor(selection.rect)
                )
            },
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
                currentLocatorState.value = locator
                savePosition(locator)
                readingTimerTracker.onLocatorChanged(locator)
            }
            .launchIn(lifecycleScope)

        val decorationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                when (event.group) {
                    ANNOTATIONS_GROUP_HIGHLIGHTS -> pendingHighlightColorPicker.value = PendingHighlightPicker(
                        locator = event.decoration.locator,
                        anchorRect = overlayRectFor(event.rect),
                        existingHighlightId = event.decoration.id
                    )
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
        val bookTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        composeOverlay.setContent {
            IshiReaderTheme {
                var settingsSheetOpen by remember { mutableStateOf(false) }
                var timerSheetOpen by remember { mutableStateOf(false) }
                var annotationsSheetOpen by remember { mutableStateOf(false) }
                val settings by readerSettingsState
                val timerState by readingTimerTracker.state.collectAsState()
                val annotationsState by annotationsController.state.collectAsState()
                val pendingNote by pendingNewNoteLocator
                val highlightPicker by pendingHighlightColorPicker
                val editingNoteId by activeNoteEditId
                val chromeShown by chromeVisible
                val currentLocator by currentLocatorState
                val totalPositions by totalPositionsState
                val chapterTitle = currentLocator?.let { locator -> publication?.chapterTitleFor(locator) }
                val positionText = positionDisplayText(settings.positionDisplayMode, currentLocator, totalPositions)

                // The chapter title/position overlays sit directly against the page, so they
                // track the reader's own theme colors rather than the app's Material chrome
                // theme (unlike the tap-menu bars, which stay on the app theme). White/#121212
                // mirrors the same "Auto" fallback as applyContainerAppearance/EpubPreferences.
                val readerBackgroundColor = Color(android.graphics.Color.parseColor(settings.theme?.backgroundHex ?: "#FFFFFF"))
                val readerTextColor = Color(android.graphics.Color.parseColor(settings.theme?.textHex ?: "#121212"))

                Box(Modifier.fillMaxSize()) {
                    // Top: tap-menu bar (back + title) stacked above the persistent chapter-title
                    // header. When the tap-menu is hidden, the chapter title -- if enabled -- is
                    // the topmost element and needs the safe-drawing inset itself; when the
                    // tap-menu is shown, it already absorbed that inset so the header doesn't
                    // need to double up on it.
                    Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = chromeShown,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close book", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 16.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = settings.showChapterTitle && chapterTitle != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = chapterTitle.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = readerTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(readerBackgroundColor.copy(alpha = 0.85f))
                                    .then(
                                        if (!chromeShown) {
                                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Bottom: position indicator stacked above the tap-menu icon bar, same
                    // stacking/inset reasoning as the top Column above.
                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = positionText != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Lifts the whole bar a bit off the bottom edge -- applied
                                    // before the background so the gap stays empty rather than
                                    // getting filled in by it.
                                    .padding(bottom = 8.dp)
                                    .background(readerBackgroundColor.copy(alpha = 0.85f))
                                    .then(
                                        if (!chromeShown) {
                                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    // Extra horizontal inset (beyond the safe-drawing insets
                                    // above) so left/right-aligned text clears the screen edge
                                    // instead of being cut off by it.
                                    .padding(horizontal = 32.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = positionText.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = readerTextColor,
                                    modifier = Modifier.align(
                                        when (settings.positionDisplayAlignment) {
                                            PositionDisplayAlignment.LEFT -> Alignment.CenterStart
                                            PositionDisplayAlignment.CENTER -> Alignment.Center
                                            PositionDisplayAlignment.RIGHT -> Alignment.CenterEnd
                                        }
                                    )
                                )
                            }
                        }

                        // Bottom bar: annotations/timer/settings -- moved down from the old
                        // top-right floating row so nothing sits behind a camera cutout.
                        AnimatedVisibility(
                            visible = chromeShown,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { annotationsSheetOpen = true }) {
                                    Icon(Icons.Filled.Bookmarks, contentDescription = "Annotations", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { timerSheetOpen = true }) {
                                    Icon(Icons.Filled.Timer, contentDescription = "Reading timer", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { lifecycleScope.launch { toggleOrientationPreservingPosition() } }) {
                                    Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate reader", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { settingsSheetOpen = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Reader settings", tint = MaterialTheme.colorScheme.onSurface)
                                }
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

                highlightPicker?.let { picker ->
                    val existingId = picker.existingHighlightId
                    HighlightColorPopover(
                        anchor = picker.anchorRect,
                        selectedColor = existingId?.let { id ->
                            HighlightColor.fromId(annotationsController.highlightById(id)?.color)
                        },
                        onColorSelected = { color ->
                            if (existingId != null) {
                                annotationsController.updateHighlightColor(existingId, color)
                            } else {
                                annotationsController.addHighlight(picker.locator, color)
                            }
                            pendingHighlightColorPicker.value = null
                        },
                        onDelete = existingId?.let { id ->
                            {
                                annotationsController.deleteHighlight(id)
                                pendingHighlightColorPicker.value = null
                            }
                        },
                        onDismiss = { pendingHighlightColorPicker.value = null }
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
        applyContainerAppearance(updated)
        lifecycleScope.launch { applyPreferencesPreservingPosition(updated.toEpubPreferences()) }
        lifecycleScope.launch { app.readerPreferencesStore.save(updated) }
    }

    /**
     * Font size (and lineHeight/margins/spacing) changes reflow the page -- Readium's own
     * post-relayout recovery in EpubNavigatorFragment just clamps the previous *pixel* scroll
     * offset into the new layout, which can land far from the paragraph actually being read on a
     * large change (this is exactly the drift the website's useEpubNavigator.correctPositionAround
     * exists to fix, and Readium's own navigator doesn't do it automatically on either platform).
     * [preservePositionAcross] ports that fix; this just supplies the "change" as submitting the
     * new preferences.
     */
    private suspend fun applyPreferencesPreservingPosition(preferences: EpubPreferences) {
        preservePositionAcross { navigatorFragment?.submitPreferences(preferences) }
    }

    /**
     * Rotating the reader reflows the page exactly like a font-size/margin change does (the
     * container's width changes under the WebView), and drifts the same way for the same reason
     * -- so it reuses [preservePositionAcross] rather than a separate mechanism. Locking rotation
     * to only happen through this button (see currentOrientationLock in onCreate) is what makes
     * capturing the anchor "before" meaningful -- a device-tilt rotation would reflow before we
     * ever got a chance to.
     */
    private suspend fun toggleOrientationPreservingPosition() {
        preservePositionAcross { toggleOrientation() }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun currentOrientationLock(): Int =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /**
     * Captures a content-anchored locator (firstVisibleElementLocator finds the first visible
     * block and anchors it with a DOM/text-quote locator, not a raw scroll fraction) before
     * [change] runs, then re-navigates to it once the WebView has had a moment to reflow.
     * [preferencesApplyGeneration] discards a stale re-navigation if a newer change (e.g. another
     * slider tick, or a second rotation) has since superseded this one.
     *
     * The website's own first cut of this fix had a race: the reflow's internal auto-snap (a
     * ResizeObserver on the *content* WebView's own body, not driven by our corrective go() call
     * at all) can still be pending when our first go() resolves, and if it fires afterward it
     * silently re-clamps to the wrong pixel offset and undoes our correction (see Ishi-Read commit
     * 831ac5fc, "font size changing fix"). The fix there -- and here -- is to re-assert the same
     * go() a second time after giving that observer a further moment to have already fired, so our
     * correction is the last word either way. Still gated by the same generation check.
     */
    private suspend fun preservePositionAcross(change: () -> Unit) {
        val generation = ++preferencesApplyGeneration
        val anchor = (navigatorFragment as? VisualNavigator)?.firstVisibleElementLocator()
        change()
        if (anchor == null) return

        delay(150)
        if (preferencesApplyGeneration != generation) return
        navigatorFragment?.go(anchor, animated = false)

        delay(150)
        if (preferencesApplyGeneration != generation) return
        navigatorFragment?.go(anchor, animated = false)
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

/** One-decimal-place percent, matching percentFromLocator's rounding (used by the library
 *  screen) exactly, so the standalone [PositionDisplayMode.PERCENT] reads the same everywhere in
 *  the app -- e.g. "23.2%". */
private fun formatPercent(totalProgression: Double): String {
    val percent = kotlin.math.round(min(1.0, max(0.0, totalProgression)) * 1000) / 10
    return "%.1f%%".format(percent)
}

/** Builds the bottom position indicator's text per [mode], or null if there's nothing to show
 *  (mode is NONE, or the data it needs hasn't loaded/isn't available for this locator yet).
 *  [PositionDisplayMode.PAGE_PERCENT] uses a whole-number percent in parentheses -- distinct from
 *  the one-decimal [formatPercent] used by the standalone PERCENT mode -- per the "8 of 100 (8%)"
 *  example this was speced against. */
private fun positionDisplayText(
    mode: PositionDisplayMode,
    locator: Locator?,
    totalPositions: Int?
): String? {
    if (mode == PositionDisplayMode.NONE || locator == null) return null

    val page = locator.locations.position
    val pageText = if (page != null && totalPositions != null) "$page of $totalPositions" else null
    val totalProgression = locator.locations.totalProgression

    return when (mode) {
        PositionDisplayMode.NONE -> null
        PositionDisplayMode.PAGE -> pageText
        PositionDisplayMode.PERCENT -> totalProgression?.let { formatPercent(it) }
        PositionDisplayMode.PAGE_PERCENT -> {
            val percentWhole = totalProgression?.let { (min(1.0, max(0.0, it)) * 100).roundToInt() }
            when {
                pageText != null && percentWhole != null -> "$pageText ($percentWhole%)"
                pageText != null -> pageText
                percentWhole != null -> "$percentWhole%"
                else -> null
            }
        }
    }
}
