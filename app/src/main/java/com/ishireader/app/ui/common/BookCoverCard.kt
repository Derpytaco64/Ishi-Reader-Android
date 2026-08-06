package com.ishireader.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl

/** Cover + title, used by the Books/Audiobooks grid and every Home shelf (carousel or wrapping).
 *  [onLongClick] opens the shared book context menu (Go to Series / Export Notes / shelf toggle /
 *  Remove from Continue Reading) where the caller wires it up -- null wherever that doesn't apply
 *  (e.g. the shelf "manage books" picker grid, where a tap already means something else). Dims
 *  itself while offline with no local download, since it can't actually be opened right now --
 *  see [com.ishireader.app.ui.common.LocalBookAvailability]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCoverCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val availability = LocalBookAvailability.current
    val dimmed = remember(book.url, availability) {
        availability.isOffline && availability.bookDownloadRepository?.isDownloaded(book.manifestUrl()) == false
    }
    Column(
        modifier = modifier
            .alpha(if (dimmed) 0.5f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = book.cover,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                // Audiobook cover art is conventionally distributed square (like an album/podcast
                // cover), unlike the portrait 2:3 book-jacket ratio everything else here uses.
                .aspectRatio(if (book.isAudiobook) 1f else 2f / 3f)
                .padding(bottom = 4.dp)
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
