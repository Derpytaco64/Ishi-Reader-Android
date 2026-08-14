package com.ishireader.app.ui.common

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.manifestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin enough to read as a frame rather than a bar -- [ContinueReadingCard]'s own progress bar
 *  stays the more prominent treatment there; this is the "everywhere else" discrete version. */
private val ProgressBorderWidth = 2.5.dp

/** Upper bound for cover decode width, comfortably above the widest a grid cell can actually get:
 *  [CoverSize]'s largest setting is 160dp, and GridCells.Adaptive can stretch a column up to just
 *  under 2x its minSize before adding another one -- so this stays a strict upper bound (never
 *  visibly softer than decoding at full source resolution) across every CoverSize/grid width
 *  combination, while still avoiding decoding arbitrarily large source images into memory. */
private val MaxCoverDecodeWidth = 360.dp

/** Cover + title, used by the Books/Audiobooks grid and every Home shelf (carousel or wrapping).
 *  [onLongClick] opens the shared book context menu (Go to Series / Export Notes / shelf toggle /
 *  Remove from Continue Reading) where the caller wires it up -- null wherever that doesn't apply
 *  (e.g. the shelf "manage books" picker grid, where a tap already means something else). Dims
 *  itself while offline with no local download, since it can't actually be opened right now --
 *  see [com.ishireader.app.ui.common.LocalBookAvailability]. Traces a thin progress border around
 *  the cover for whatever reading percent is locally known (see [LocalReadingProgress]) --
 *  [showProgressBorder] turns it off for ContinueReadingCard, which already shows its own more
 *  prominent progress bar below the cover and would otherwise show the same progress twice. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCoverCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    uniformCoverSlot: Boolean = true,
    showProgressBorder: Boolean = true
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

    // Local-only (no network) so every cover in a grid can afford to look this up, unlike
    // PositionRepository.getPosition's server refresh -- see LocalReadingProgress.
    val positionRepository = LocalReadingProgress.current
    var progressPercent by remember(book.url, positionRepository) { mutableStateOf<Double?>(null) }
    LaunchedEffect(book.url, positionRepository) {
        progressPercent = positionRepository?.localPercent(book.manifestUrl())
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
            // CLAUDE-ADDED: decoding at Size.ORIGINAL (full source resolution) used to keep covers
            // sharp regardless of grid density/screen size, but it also meant a large source image
            // (e.g. a 2000x3000 scan) stayed fully decoded in memory for a cell that only ever
            // shows it at a couple hundred px -- a real cost on low-RAM devices when a whole grid
            // of covers is on screen. Capping the decode to MaxCoverDecodeWidth keeps the same
            // "always at least as sharp as the cell needs" guarantee (see its own doc comment)
            // without paying for resolution no cell can actually display.
            val maxDecodeWidthPx = with(LocalDensity.current) { MaxCoverDecodeWidth.roundToPx() }
            val isSquareCover = book.isAudiobook
            AsyncImage(
                model = remember(book.cover, maxDecodeWidthPx, isSquareCover) {
                    ImageRequest.Builder(context)
                        .data(book.cover)
                        .size(
                            if (isSquareCover) Size(maxDecodeWidthPx, maxDecodeWidthPx)
                            else Size(maxDecodeWidthPx, maxDecodeWidthPx * 3 / 2)
                        )
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
            val borderPercent = progressPercent
            if (showProgressBorder && borderPercent != null) {
                val trackColor = MaterialTheme.colorScheme.outlineVariant
                val progressColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawProgressBorder(
                        fraction = (borderPercent / 100.0).toFloat(),
                        trackColor = trackColor,
                        progressColor = progressColor
                    )
                }
            }
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

/** Traces [fraction] (0..1) of the cover's own rectangular perimeter in [progressColor], clockwise
 *  from the top-left corner, over a full [trackColor] outline -- the "everywhere else" counterpart
 *  to ContinueReadingCard's linear bar, stroked inward by half its own width so it never gets
 *  clipped by the cover's bounds. */
private fun DrawScope.drawProgressBorder(fraction: Float, trackColor: Color, progressColor: Color) {
    val strokeWidthPx = ProgressBorderWidth.toPx()
    val inset = strokeWidthPx / 2f
    val perimeter = Path().apply {
        moveTo(inset, inset)
        lineTo(size.width - inset, inset)
        lineTo(size.width - inset, size.height - inset)
        lineTo(inset, size.height - inset)
        close()
    }
    val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    drawPath(perimeter, color = trackColor, style = stroke)

    if (fraction > 0f) {
        val measure = PathMeasure().apply { setPath(perimeter, forceClosed = false) }
        val progressSegment = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), progressSegment, startWithMoveTo = true)
        drawPath(progressSegment, color = progressColor, style = stroke)
    }
}
