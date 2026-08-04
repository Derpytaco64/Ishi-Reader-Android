package com.ishireader.app.data.repository

import android.content.Context
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Downloads a book's raw publication file (epub/pdf/cbz) to local storage so the Readium
 * Kotlin navigator can open it as a local asset instead of streaming the remote manifest
 * resource-by-resource, which is the fragile, lightly-exercised path in the toolkit and has no
 * offline support. Files live under filesDir (not cacheDir) -- these are deliberate downloads
 * the OS shouldn't reclaim on its own.
 */
class BookDownloadRepository(
    private val context: Context,
    private val network: NetworkModule
) {

    private val booksDir: File
        get() = File(context.filesDir, "books").apply { mkdirs() }

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

    /** Downloads to a ".part" temp file first, only exposing it under its real name once the
     *  transfer completes -- an interrupted download must never look like a usable local copy. */
    suspend fun download(
        manifestUrl: String,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        val tempFile = File(booksDir, "${keyFor(manifestUrl)}.part")
        try {
            val response = network.api.downloadBook(manifestUrl)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext ApiResult.Failure("Couldn't download book (${response.code()})")
            }

            writeToFile(body, tempFile, onProgress)

            val finalFile = File(booksDir, "${keyFor(manifestUrl)}.${extensionFor(response)}")
            localFileFor(manifestUrl)?.delete()
            tempFile.renameTo(finalFile)
            ApiResult.Success(finalFile)
        } catch (e: Exception) {
            tempFile.delete()
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Frees local storage; the book is re-downloaded on next read. */
    fun delete(manifestUrl: String) {
        localFileFor(manifestUrl)?.delete()
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
