package com.ishireader.app.reader

import android.os.Bundle
import android.widget.Toast
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
import org.readium.r2.navigator.epub.EpubNavigatorFragment
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
 * CAVEAT: this is the one file in the scaffold built against APIs that were not possible to
 * verify against the live Readium Maven artifacts while writing it (see the "readium" version
 * TODO in gradle/libs.versions.toml). AssetRetriever / PublicationOpener / EpubNavigatorFragment
 * construction has shifted signatures across kotlin-toolkit releases -- if this doesn't compile
 * as-is, diff it against the `reader` module of https://github.com/readium/kotlin-toolkit's own
 * test-app for whatever version actually resolves; the overall flow (retrieve asset -> open
 * publication -> create navigator fragment -> observe currentLocator) will still be the same
 * shape even if a constructor or two has moved.
 */
class ReaderActivity : FragmentActivity() {

    companion object {
        const val EXTRA_MANIFEST_URL = "manifest_url"
        const val EXTRA_TITLE = "title"
        private const val NAVIGATOR_FRAGMENT_TAG = "epub_navigator"
    }

    private val app: IshiReaderApp by lazy { application as IshiReaderApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        title = intent.getStringExtra(EXTRA_TITLE)

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

    private fun showNavigator(publication: Publication, initialLocator: Locator?) {
        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) != null) return

        val factory = EpubNavigatorFragment.createFactory(
            publication = publication,
            initialLocator = initialLocator
        )
        val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)

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
