package com.ishireader.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ishireader.app.R
import com.ishireader.app.data.model.Book
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.series.SeriesScreen
import com.ishireader.app.ui.series.SeriesViewModel
import kotlinx.coroutines.launch

private val TabTitles = listOf("Home", "Library", "Series")
private const val BannerAspectRatio = 1536f / 256f

/** Home/Library/Series as swipeable pages under one tab strip, instead of separate pushed
 *  destinations -- each keeps its own ViewModel (scoped to this composable's back stack entry,
 *  same as before) so state survives swiping away and back. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    seriesViewModel: SeriesViewModel,
    onBookClick: (Book) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TabTitles.size })
    val scope = rememberCoroutineScope()

    // Drawn full-bleed (no status bar padding) so it fills the space behind the status bar/camera
    // cutout instead of leaving it blank; the tab strip then sits below it, already clear of the
    // cutout since the banner's own height comfortably exceeds it. Each page's TopAppBar has its
    // own status bar inset zeroed out (see HomeScreen/LibraryScreen/SeriesScreen), since neither
    // it nor the tab strip ever sits at the true top of the window anymore.
    Column(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.home_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(BannerAspectRatio)
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            TabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> HomeScreen(viewModel = homeViewModel, onBookClick = onBookClick)
                1 -> LibraryScreen(viewModel = libraryViewModel, onBookClick = onBookClick)
                else -> SeriesScreen(viewModel = seriesViewModel, onBookClick = onBookClick)
            }
        }
    }
}
