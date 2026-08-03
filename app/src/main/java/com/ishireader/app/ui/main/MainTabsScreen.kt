package com.ishireader.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.R
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.series.SeriesScreen
import com.ishireader.app.ui.series.SeriesViewModel
import com.ishireader.app.ui.shelves.ShelvesScreen
import com.ishireader.app.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch

private val TabTitles = listOf("Home", "Library", "Series", "Shelves")
private val LogoSize = 56.dp
private val AvatarSize = 40.dp

/** Home/Library/Series as swipeable pages under one tab strip, instead of separate pushed
 *  destinations -- each keeps its own ViewModel (scoped to this composable's back stack entry,
 *  same as before) so state survives swiping away and back. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    seriesViewModel: SeriesViewModel,
    shelvesViewModel: ShelvesViewModel,
    topBarViewModel: TopBarViewModel,
    avatarBaseUrl: String?,
    onBookClick: (Book) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TabTitles.size })
    val scope = rememberCoroutineScope()
    val user by topBarViewModel.user.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Filled with the app's own surface color and drawn behind the status bar/camera cutout
        // (background painted before statusBarsPadding shrinks the content inset), so the logo and
        // avatar are never obscured by it. Each page's own TopAppBar has its status bar inset
        // zeroed out (see HomeScreen/LibraryScreen/SeriesScreen) since neither it nor this header
        // ever sits at the true top of the window.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "Ishi Reader",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(LogoSize).clip(CircleShape)
            )
            UserAvatar(user = user, baseUrl = avatarBaseUrl)
        }

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
                2 -> SeriesScreen(viewModel = seriesViewModel, onBookClick = onBookClick)
                else -> ShelvesScreen(viewModel = shelvesViewModel, onBookClick = onBookClick)
            }
        }
    }
}

/** Mirrors the website's AvatarCircle: the user's uploaded avatar if they have one, otherwise a
 *  circle with their name's first letter (avatarUrl is server-relative, so it needs [baseUrl]
 *  prepended before Coil can load it). */
@Composable
private fun UserAvatar(user: PublicUser?, baseUrl: String?) {
    val avatarUrl = user?.avatarUrl?.let { path -> baseUrl?.let { it + path } }
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = user?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = user?.name?.take(1)?.uppercase().orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
