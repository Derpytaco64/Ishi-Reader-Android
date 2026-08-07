package com.ishireader.app.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.TappedImage

/** Full-resolution image viewer, opened by tapping an image while reading (see
 *  ImageTapInputListener) -- mirrors the website's StatefulImageOverlay, but leans on Compose's
 *  own multi-touch [transformable] gesture detector for pinch-to-zoom/pan instead of a slider +
 *  hand-rolled drag handler, so there's no separate zoom controller UI to build. */
@Composable
fun ImageViewerOverlay(image: TappedImage, onClose: () -> Unit) {
    // Claims the system back gesture/button for as long as this overlay is on screen -- without
    // this, back falls through to the Activity's default handling and exits the book instead of
    // just dismissing the overlay, the same behavior the close X gives.
    BackHandler(onBack = onClose)

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        // panChange arrives in the transformable node's local (pre-scale) coordinate space --
        // Compose divides raw touch movement by the layer's own zoom before reporting it here, so
        // it must be scaled back up or the image would visibly trail behind the finger more and
        // more as you zoom in.
        offset = if (newScale <= 1f) Offset.Zero else offset + panChange * newScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            bitmap = image.bitmap.asImageBitmap(),
            contentDescription = image.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(transformState)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .displayCutoutPadding()
                // Extra headroom below the status bar / cutout insets above -- a front camera
                // punch-hole isn't always fully covered by those insets alone, and this keeps the
                // X from sitting flush in line with it.
                .padding(top = 24.dp, end = 8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
