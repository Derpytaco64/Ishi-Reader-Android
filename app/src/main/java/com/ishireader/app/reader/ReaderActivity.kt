package com.ishireader.app.reader

import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.R
import com.ishireader.app.data.network.ApiResult
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Streams a book straight from the Ishi-Read server's Readium Web Publication Server and
 * renders it with the Readium Kotlin toolkit's EPUB navigator, syncing the reading position
 * back to /api/userdata/position as the user reads.
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
        private const val READER_PREFS_NAME = "reader_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
    }

    private val app: IshiReaderApp by lazy { application as IshiReaderApp }

    @OptIn(ExperimentalReadiumApi::class)
    private var currentPreferences: EpubPreferences = EpubPreferences()
    private val fontScaleState = mutableStateOf(1.0)
    private val themeState = mutableStateOf(Theme.LIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        title = intent.getStringExtra(EXTRA_TITLE)

        currentPreferences = loadPreferences()
        fontScaleState.value = currentPreferences.fontSize ?: 1.0
        themeState.value = currentPreferences.theme ?: Theme.LIGHT
        setUpSettingsOverlay()

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

    @OptIn(ExperimentalReadiumApi::class)
    private fun setUpSettingsOverlay() {
        findViewById<ComposeView>(R.id.reader_overlay).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ReaderSettingsOverlay(
                    fontScale = fontScaleState.value,
                    theme = themeState.value,
                    onFontScaleChange = { scale ->
                        fontScaleState.value = scale
                        applyPreferences(currentPreferences.copy(fontSize = scale))
                    },
                    onThemeChange = { theme ->
                        themeState.value = theme
                        applyPreferences(currentPreferences.copy(theme = theme))
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun applyPreferences(preferences: EpubPreferences) {
        currentPreferences = preferences
        val fragment = supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) as? EpubNavigatorFragment
        fragment?.submitPreferences(preferences)
        savePreferences(preferences)
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun loadPreferences(): EpubPreferences {
        val prefs = getSharedPreferences(READER_PREFS_NAME, MODE_PRIVATE)
        val fontSize = if (prefs.contains(KEY_FONT_SIZE)) prefs.getFloat(KEY_FONT_SIZE, 1.0f).toDouble() else null
        val theme = prefs.getString(KEY_THEME, null)?.let { name -> runCatching { Theme.valueOf(name) }.getOrNull() }
        return EpubPreferences(fontSize = fontSize, theme = theme)
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun savePreferences(preferences: EpubPreferences) {
        getSharedPreferences(READER_PREFS_NAME, MODE_PRIVATE).edit {
            preferences.fontSize?.let { putFloat(KEY_FONT_SIZE, it.toFloat()) }
            preferences.theme?.let { putString(KEY_THEME, it.name) }
        }
    }

    private fun openBook(manifestUrl: String) {
        lifecycleScope.launch {
            val url = AbsoluteUrl(manifestUrl)
            if (url == null) {
                toastAndFinish("Invalid manifest URL")
                return@launch
            }

            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(contentResolver, httpClient)
            val publicationParser = DefaultPublicationParser(
                context = this@ReaderActivity,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
            val publicationOpener = PublicationOpener(publicationParser)

            val asset = when (val result = assetRetriever.retrieve(url)) {
                is Try.Success -> result.value
                is Try.Failure -> return@launch toastAndFinish("Couldn't reach book: ${result.value}")
            }

            val publication = when (val result = publicationOpener.open(asset, allowUserInteraction = false)) {
                is Try.Success -> result.value
                is Try.Failure -> return@launch toastAndFinish("Couldn't open book: ${result.value}")
            }

            val initialLocator = fetchSavedLocator(manifestUrl)
            showNavigator(publication, initialLocator)
        }
    }

    /** Bridges the JSON we get back from Retrofit (kotlinx.serialization) into the org.json
     *  shape Locator.fromJSON expects -- Readium's own model classes parse from org.json. */
    private suspend fun fetchSavedLocator(manifestUrl: String): Locator? {
        val result = app.positionRepository.getPosition(manifestUrl)
        val locatorJson = (result as? ApiResult.Success)?.data ?: return null
        return runCatching { Locator.fromJSON(JSONObject(locatorJson.toString())) }.getOrNull()
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun showNavigator(publication: Publication, initialLocator: Locator?) {
        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) != null) return

        val navigatorFactory = EpubNavigatorFactory(publication = publication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = currentPreferences
        )
        val fragment = fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)

        supportFragmentManager.commitNow {
            replace(R.id.reader_container, fragment, NAVIGATOR_FRAGMENT_TAG)
        }

        (fragment as EpubNavigatorFragment).currentLocator
            .onEach { locator -> savePosition(locator) }
            .launchIn(lifecycleScope)
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
