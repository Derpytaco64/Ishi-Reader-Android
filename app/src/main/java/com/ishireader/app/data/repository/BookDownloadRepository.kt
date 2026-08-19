package com.ishireader.app.data.repository

import android.content.Context
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest

/** A single in-flight download's progress, keyed by [id] (the book's content-hash key -- see
 *  [BookDownloadRepository.keyFor]) so the library screen's download ring can tell concurrent
 *  downloads apart and weight each one's ring segment by [totalBytes]. */
data class DownloadProgress(val id: String, val bytesRead: Long, val totalBytes: Long)

/**
 * Downloads a book's raw publication file (epub/pdf/cbz, or an audiobook's, since this is the
 * same generic download used by both -- see ReaderActivity.ensureDownloaded) to local storage so
 * the Readium Kotlin navigator can open it as a local asset instead of streaming the remote
 * manifest resource-by-resource, which is the fragile, lightly-exercised path in the toolkit and
 * has no offline support. Files live under filesDir (not cacheDir) -- these are deliberate
 * downloads the OS shouldn't reclaim on its own.
 */
class BookDownloadRepository(
    private val context: Context,
    private val network: NetworkModule
) {

    private val booksDir: File
        get() = File(context.filesDir, "books").apply { mkdirs() }

    /** Where downloaded book files actually live on disk -- surfaced read-only for the settings
     *  drawer's "Downloaded files" section (see SettingsDrawerContent). */
    val booksDirectory: File get() = booksDir

    /** Bumped on every successful download/delete -- isDownloaded() hits the filesystem, so
     *  BookCoverCard caches its result per book via remember() and needs something to key off of
     *  to know when to re-check (see LocalBookAvailability). The count itself is never read. */
    private val _downloadsVersion = MutableStateFlow(0)
    val downloadsVersion: StateFlow<Int> = _downloadsVersion.asStateFlow()

    /** Every currently in-flight download, keyed by [DownloadProgress.id] -- drives the library
     *  screen's download progress ring (see MainTabsScreen's DownloadProgressRing). */
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    private fun keyFor(manifestUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(manifestUrl.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The already-downloaded file for this book, whatever its extension, or null if absent.
     *  Excludes ".part" files -- those are in-progress/interrupted downloads, not usable assets. */
    fun localFileFor(manifestUrl: String): File? {
        val key = keyFor(manifestUrl)
        return booksDir.listFiles { file -> file.name.startsWith(key) && !file.name.endsWith(".part") }
            ?.firstOrNull()
    }

    fun isDownloaded(manifestUrl: String): Boolean = localFileFor(manifestUrl) != null

    /** Same check as [isDownloaded], but for a whole list of books at once via a single directory
     *  listing instead of one filesystem lookup per book -- used to back the "Only show downloaded
     *  books" filter, where checking a full grid one book at a time would turn into an O(n^2) scan.
     *  Caller is expected to run this off the main thread, same as [isDownloaded]. */
    fun downloadedManifestUrls(manifestUrls: Collection<String>): Set<String> {
        val presentKeys = booksDir.listFiles { file -> !file.name.endsWith(".part") }
            ?.mapTo(mutableSetOf()) { it.name.substringBefore('.') }
            ?: emptySet()
        return manifestUrls.filterTo(mutableSetOf()) { keyFor(it) in presentKeys }
    }

    /** Downloads to a ".part" temp file first, only exposing it under its real name once the
     *  transfer completes -- an interrupted download must never look like a usable local copy. */
    suspend fun download(
        manifestUrl: String,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        val key = keyFor(manifestUrl)
        val tempFile = File(booksDir, "$key.part")
        try {
            val response = network.api.downloadBook(manifestUrl)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext ApiResult.Failure("Couldn't download book (${response.code()})")
            }

            writeToFile(body, tempFile) { bytesRead, totalBytes ->
                _activeDownloads.update { it + (key to DownloadProgress(key, bytesRead, totalBytes)) }
                onProgress(bytesRead, totalBytes)
            }

            val finalFile = File(booksDir, "$key.${extensionFor(response)}")
            localFileFor(manifestUrl)?.delete()
            tempFile.renameTo(finalFile)
            _downloadsVersion.update { it + 1 }
            ApiResult.Success(finalFile)
        } catch (e: Exception) {
            tempFile.delete()
            ApiResult.Failure(e.message ?: "Network error")
        } finally {
            _activeDownloads.update { it - key }
        }
    }

    /** Frees local storage; the book is re-downloaded on next read. */
    fun delete(manifestUrl: String) {
        localFileFor(manifestUrl)?.delete()
        _downloadsVersion.update { it + 1 }
    }

    /** Frees all local storage in one shot -- every downloaded book is re-downloaded on next read.
     *  Leaves any in-flight ".part" file alone if a download is somehow still running concurrently;
     *  [download] cleans up its own temp file on failure/cancellation. */
    fun deleteAll() {
        booksDir.listFiles { file -> !file.name.endsWith(".part") }?.forEach { it.delete() }
        _downloadsVersion.update { it + 1 }
    }

    private fun writeToFile(body: ResponseBody, target: File, onProgress: (Long, Long) -> Unit) {
        val total = body.contentLength()
        var bytesRead = 0L
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    onProgress(bytesRead, total)
                }
            }
        }
    }

    /** Prefers the filename the server sent (see api/books/download's Content-Disposition) so
     *  the local copy keeps its real extension; falls back to Content-Type, then plain ".epub". */
    private fun extensionFor(response: Response<ResponseBody>): String {
        val disposition = response.headers()["Content-Disposition"]
        val filename = disposition
            ?.substringAfter("filename=\"", "")
            ?.substringBefore("\"")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        val fromDisposition = filename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        if (fromDisposition != null) return fromDisposition

        return when (response.headers()["Content-Type"]?.substringBefore(";")?.trim()) {
            "application/pdf" -> "pdf"
            "application/vnd.comicbook+zip" -> "cbz"
            else -> "epub"
        }
    }
}
