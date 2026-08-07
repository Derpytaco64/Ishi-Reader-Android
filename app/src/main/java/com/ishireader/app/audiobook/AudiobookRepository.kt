package com.ishireader.app.audiobook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the Readium Web Publication Manifest (manifest.json) for chapter/track metadata --
 * unlike every other repository in this app, this hits [manifestUrl] itself directly rather than
 * a route on the Ishi-Read Next.js server: manifestUrl already *is* the absolute URL to the
 * self-hosted Go Readium server's own manifest.json (see Book.manifestUrl()'s doc comment), which
 * is the same unauthenticated endpoint the website's own Readium JS fetches manifest data from
 * directly in the browser -- no session cookie needed, so a plain client (not NetworkModule's
 * cookie-jarred Retrofit one) is enough.
 *
 * The actual playable audio bytes come from [com.ishireader.app.data.repository.
 * BookDownloadRepository] instead (same local-download-then-play pattern as EPUBs) -- this
 * repository only supplies the chapter list and the track's own href/type/duration metadata,
 * which the local file alone doesn't carry.
 */
class AudiobookRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null on any failure (offline, unreachable manifest server, malformed JSON) --
     *  callers should treat that as "no chapters known", not fail the whole player, since the
     *  locally downloaded audio can still play with a single implicit chapter. */
    suspend fun fetchManifestInfo(manifestUrl: String): AudiobookManifestInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val manifest = json.decodeFromString(RwpmManifest.serializer(), body)
                val track = manifest.readingOrder.firstOrNull()
                val chapters = flattenToc(manifest.toc)
                    .mapNotNull { item ->
                        parseTimeFragmentSeconds(item.href)?.let { start ->
                            AudiobookChapter(item.title?.takeIf { it.isNotBlank() } ?: "Chapter", start)
                        }
                    }
                    .distinctBy { it.startSeconds }
                    .sortedBy { it.startSeconds }
                AudiobookManifestInfo(
                    chapters = chapters,
                    trackHref = track?.href,
                    trackType = track?.type,
                    trackDurationSeconds = track?.duration
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
