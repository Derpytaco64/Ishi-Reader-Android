@file:OptIn(ExperimentalReadiumApi::class)

package com.ishireader.app.reader

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.R
import com.ishireader.app.data.model.ReaderSettings
import com.ishireader.app.data.model.toEpubPreferences
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.ui.reader.ReaderSettingsSheet
import com.ishireader.app.ui.theme.IshiReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
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
        showNavigator(publication, initialLocator)
    }

    /** Reads the best-known position (PositionRepository tries a quick server refresh first, then
     *  falls back to Room -- safe offline) and bridges its kotlinx.serialization JSON into the
     *  org.json shape Locator.fromJSON expects, since Readium's own model classes parse from
     *  org.json. */
    private suspend fun fetchSavedLocator(manifestUrl: String): Locator? {
        val locatorJson = app.positionRepository.getPosition(manifestUrl) ?: return null
        return runCatching { Locator.fromJSON(JSONObject(locatorJson.toString())) }.getOrNull()
    }

    private fun showNavigator(publication: Publication, initialLocator: Locator?) {
        progressOverlay.visibility = View.GONE

        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) != null) return

        val navigatorFactory = EpubNavigatorFactory(publication = publication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = readerSettingsState.value.toEpubPreferences()
        )
        val fragment = fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)

        supportFragmentManager.commitNow {
            replace(R.id.reader_container, fragment, NAVIGATOR_FRAGMENT_TAG)
        }

        navigatorFragment = fragment as EpubNavigatorFragment
        navigatorFragment!!.currentLocator
            .onEach { locator -> savePosition(locator) }
            .launchIn(lifecycleScope)

        setUpSettingsOverlay()
    }

    /** A small always-on-top gear button that opens [ReaderSettingsSheet]; set up once the
     *  navigator fragment exists, since applying a change means calling submitPreferences on it.
     *  readerSettingsState is plain mutableStateOf (not remember{}) so it's the same object
     *  read here and written by applyReaderSettings/openBook -- one source of truth regardless of
     *  which side changes it. */
    private fun setUpSettingsOverlay() {
        composeOverlay.setContent {
            IshiReaderTheme {
                var sheetOpen by remember { mutableStateOf(false) }
                val settings by readerSettingsState

                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { sheetOpen = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Reader settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (sheetOpen) {
                    ReaderSettingsSheet(
                        settings = settings,
                        onSettingsChange = ::applyReaderSettings,
                        onDismiss = { sheetOpen = false }
                    )
                }
            }
        }
    }

    /** Applies a settings change live to the navigator, then persists it -- in that order, so the
     *  reader responds instantly and a failed/slow write never delays what the user sees. */
    private fun applyReaderSettings(updated: ReaderSettings) {
        readerSettingsState.value = updated
        navigatorFragment?.submitPreferences(updated.toEpubPreferences())
        lifecycleScope.launch { app.readerPreferencesStore.save(updated) }
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
