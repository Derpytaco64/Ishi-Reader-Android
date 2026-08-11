package com.ishireader.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cover + title, used by the Books/Audiobooks grid and every Home shelf (carousel or wrapping).
 *  [onLongClick] opens the shared book context menu (Go to Series / Export Notes / shelf toggle /
 *  Remove from Continue Reading) where the caller wires it up -- null wherever that doesn't apply
 *  (e.g. the shelf "manage books" picker grid, where a tap already means something else). Dims
 *  itself while offline with no local download, since it can't actually be opened right now --
 *  see [com.ishireader.app.ui.common.LocalBookAvailability]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCoverCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    uniformCoverSlot: Boolean = true
) {
    val availability = LocalBookAvailability.current
    // CLAUDE-ADDED: isDownloaded() hits the filesystem (File.listFiles()) -- every visible cover
    // used to run this inline in remember{} while offline, meaning a single isOffline flip could
    // fire a synchronous disk scan per book on the main thread in the same recomposition pass.
    // Off the main thread and defaults to "not dimmed" until the check resolves.
    var dimmed by remember(book.url, availability) { mutableStateOf(false) }
    LaunchedEffect(book.url, availability) {
        dimmed = availability.isOffline &&
            withContext(Dispatchers.IO) { availability.bookDownloadRepository?.isDownloaded(book.manifestUrl()) } == false
    }
    Column(
        modifier = modifier
            .alpha(if (dimmed) 0.5f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // CLAUDE-ADDED: The slot keeps the portrait 2:3 height even for a square audiobook cover,
        // which is centred inside it -- otherwise the shorter cover pulls its title (and Continue
        // Reading's "Remove" button under that) up out of line with the rest of the row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (book.isAudiobook && !uniformCoverSlot) 1f else 2f / 3f)
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            AsyncImage(
                // CLAUDE-ADDED: Size.ORIGINAL skips Coil's default behaviour of downsampling the
                // decode to match this composable's (small) layout size -- covers are decoded at
                // their full source resolution and Compose's Crop scaling does the downscale, so
                // they stay sharp regardless of grid density or screen size.
                model = remember(book.cover) {
                    ImageRequest.Builder(context)
                        .data(book.cover)
                        .size(Size.ORIGINAL)
                        .build()
                },
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // Audiobook cover art is conventionally distributed square (like an album/
                    // podcast cover), unlike the portrait 2:3 book-jacket ratio everything else
                    // here uses.
                    // Square only when it's being letterboxed into the taller portrait slot --
                    // when the slot is already 1:1 the image just fills it, so the 4dp bottom
                    // padding can't force a square image wider than the space left for it.
                    .then(
                        if (book.isAudiobook && uniformCoverSlot) Modifier.aspectRatio(1f)
                        else Modifier.fillMaxHeight()
                    )
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            // Two lines are always reserved so a one-line title doesn't pull whatever the caller
            // stacks below it (see ContinueReadingCard) up past its neighbours in the same row.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
