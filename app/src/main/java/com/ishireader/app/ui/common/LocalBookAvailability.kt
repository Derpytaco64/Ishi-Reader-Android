package com.ishireader.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.ishireader.app.data.repository.BookDownloadRepository

/**
 * Enough for [BookCoverCard] to know whether a book can actually be opened right now without
 * hitting the network -- a book with no local download can't be read/played while [isOffline], so
 * it dims the cover (tapping through to the detail view still works either way, it's just the
 * open/download action that would fail). [downloadsVersion] exists purely to invalidate
 * BookCoverCard's remembered per-book check after a download completes or a local file is
 * deleted, since [BookDownloadRepository.isDownloaded] hits the filesystem and isn't otherwise
 * observable; its actual count is never read. Provided once from MainActivity, same pattern as
 * LocalAppSettings.
 */
data class BookAvailability(
    val isOffline: Boolean,
    val downloadsVersion: Int,
    val bookDownloadRepository: BookDownloadRepository?
)

val LocalBookAvailability = compositionLocalOf {
    BookAvailability(isOffline = false, downloadsVersion = 0, bookDownloadRepository = null)
}
