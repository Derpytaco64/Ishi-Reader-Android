package com.ishireader.app.reader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Shared "save an image to the device's Pictures gallery" logic -- used by the reader's own
 * long-press/image-viewer Save actions (ReaderActivity) and by the book detail screen's cover
 * image viewer (BookDetailScreen), so both go through one MediaStore write path rather than two
 * copies of it drifting apart.
 *
 * Callers are responsible for the runtime WRITE_EXTERNAL_STORAGE permission check/request on
 * Android 9 (Pie, API 28) and below -- that needs an Activity-scoped permission launcher, which
 * this object (usable from a plain Context) can't own itself. From Android 10 (Q, API 29) onward
 * MediaStore's scoped-storage columns need no permission at all.
 */
object ImageGallery {
    fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    /** Performs the actual MediaStore insert/write -- assumes any necessary permission has already
     *  been granted (or isn't needed, i.e. Android 10+). Returns whether the save succeeded. */
    fun saveBytes(context: Context, bytes: ByteArray, mimeType: String, displayNameBase: String, extension: String): Boolean {
        val displayName = "${displayNameBase}_${System.currentTimeMillis()}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ishi Reader")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }.isSuccess
    }
}
