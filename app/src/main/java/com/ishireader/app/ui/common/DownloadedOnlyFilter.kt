package com.ishireader.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ishireader.app.IshiReaderApp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether the hamburger's "Only show downloaded books" toggle is on -- provided once from
 *  MainTabsScreen (same pattern as [LocalBookAvailability]) so any book grid can filter itself
 *  via [filterDownloadedOnly] without threading the flag through every screen's ViewModel. */
val LocalDownloadedOnlyFilter = compositionLocalOf { false }

/** Drops every element whose [bookOf] book has no local file when [LocalDownloadedOnlyFilter] is
 *  on; a no-op passthrough otherwise. Checks the whole list with a single directory listing (see
 *  [com.ishireader.app.data.repository.BookDownloadRepository.downloadedManifestUrls]) rather than
 *  one filesystem lookup per element, so a large grid doesn't turn this into an O(n^2) disk scan. */
@Composable
fun <T> List<T>.filterDownloadedOnly(bookOf: (T) -> Book): List<T> {
    val onlyDownloaded = LocalDownloadedOnlyFilter.current
    val items = this
    if (!onlyDownloaded) return items

    val context = LocalContext.current
    val app = context.applicationContext as IshiReaderApp
    var downloadedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(items) {
        val urls = items.map { bookOf(it).manifestUrl() }
        downloadedUrls = withContext(Dispatchers.IO) { app.bookDownloadRepository.downloadedManifestUrls(urls) }
    }
    return items.filter { bookOf(it).manifestUrl() in downloadedUrls }
}

/** Convenience overload for the common case of filtering a plain [Book] list directly. */
@Composable
fun List<Book>.filterDownloadedOnly(): List<Book> = filterDownloadedOnly { it }

/** Same idea as [filterDownloadedOnly], but for elements that bundle multiple books -- a series
 *  slot on the Series tab overview, say. Keeps the element if *any* of [booksOf] has a local file,
 *  so a series with only some volumes downloaded still shows (its own within-series grid then
 *  filters down to just those volumes); only series with nothing downloaded at all disappear. */
@Composable
fun <T> List<T>.filterAnyDownloaded(booksOf: (T) -> List<Book>): List<T> {
    val onlyDownloaded = LocalDownloadedOnlyFilter.current
    val items = this
    if (!onlyDownloaded) return items

    val context = LocalContext.current
    val app = context.applicationContext as IshiReaderApp
    var downloadedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(items) {
        val urls = items.flatMap { booksOf(it) }.map { it.manifestUrl() }
        downloadedUrls = withContext(Dispatchers.IO) { app.bookDownloadRepository.downloadedManifestUrls(urls) }
    }
    return items.filter { item -> booksOf(item).any { it.manifestUrl() in downloadedUrls } }
}
