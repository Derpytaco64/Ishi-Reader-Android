package com.ishireader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.audiobook.AudiobookPlayerActivity
import com.ishireader.app.reader.ReaderActivity
import com.ishireader.app.ui.admin.AdminScreen
import com.ishireader.app.ui.admin.AdminViewModel
import com.ishireader.app.ui.bookdetail.BookDetailScreen
import com.ishireader.app.ui.bookdetail.BookDetailViewModel
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.login.LoginScreen
import com.ishireader.app.ui.login.LoginViewModel
import com.ishireader.app.ui.common.BookAvailability
import com.ishireader.app.ui.common.LocalBookAvailability
import com.ishireader.app.ui.main.MainTabsScreen
import com.ishireader.app.ui.main.TopBarViewModel
import com.ishireader.app.ui.series.SeriesViewModel
import org.readium.r2.shared.publication.Locator
import com.ishireader.app.ui.settings.LocalAppSettings
import com.ishireader.app.ui.settings.SettingsViewModel
import com.ishireader.app.ui.settings.parseAccentColor
import com.ishireader.app.ui.shelves.ShelvesViewModel
import com.ishireader.app.ui.theme.IshiReaderTheme
import kotlinx.coroutines.launch

private const val ROUTE_LOGIN = "login"
private const val ROUTE_HOME = "home"
private const val ROUTE_ADMIN = "admin"
private const val ARG_MANIFEST_URL = "manifestUrl"
private const val ROUTE_BOOK_DETAIL = "bookDetail/{$ARG_MANIFEST_URL}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = application as IshiReaderApp

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.libraryPrefsRepository)
            )
            val settings by settingsViewModel.settings.collectAsState()
            val darkTheme = when (settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val isOffline by app.libraryRepository.isOffline.collectAsState()
            val downloadsVersion by app.bookDownloadRepository.downloadsVersion.collectAsState()
            val bookAvailability = BookAvailability(isOffline, downloadsVersion, app.bookDownloadRepository)

            IshiReaderTheme(darkTheme = darkTheme, accentColor = parseAccentColor(settings.accentColor)) {
                CompositionLocalProvider(LocalAppSettings provides settings, LocalBookAvailability provides bookAvailability) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
                        composable(ROUTE_LOGIN) {
                            val viewModel: LoginViewModel = viewModel(
                                factory = LoginViewModel.Factory(app.preferences, app.network, app.authRepository)
                            )
                            val loginState by viewModel.uiState.collectAsState()
                            IshiReaderTheme(
                                darkTheme = loginState.themeMode == "dark",
                                dynamicColor = false,
                                accentColor = parseAccentColor(loginState.accentColor)
                            ) {
                                LoginScreen(
                                    viewModel = viewModel,
                                    onLoggedIn = {
                                        settingsViewModel.reload()
                                        navController.navigate(ROUTE_HOME) {
                                            popUpTo(ROUTE_LOGIN) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                        composable(ROUTE_HOME) {
                            val homeViewModel: HomeViewModel = viewModel(
                                factory = HomeViewModel.Factory(app.libraryRepository, app.positionRepository, app.libraryPrefsRepository)
                            )
                            val libraryViewModel: LibraryViewModel = viewModel(
                                factory = LibraryViewModel.Factory(app.libraryRepository)
                            )
                            val seriesViewModel: SeriesViewModel = viewModel(
                                factory = SeriesViewModel.Factory(app.libraryRepository)
                            )
                            val shelvesViewModel: ShelvesViewModel = viewModel(
                                factory = ShelvesViewModel.Factory(app.libraryRepository, app.libraryPrefsRepository)
                            )
                            val topBarViewModel: TopBarViewModel = viewModel(
                                factory = TopBarViewModel.Factory(app.authRepository)
                            )
                            MainTabsScreen(
                                homeViewModel = homeViewModel,
                                libraryViewModel = libraryViewModel,
                                seriesViewModel = seriesViewModel,
                                shelvesViewModel = shelvesViewModel,
                                topBarViewModel = topBarViewModel,
                                settingsViewModel = settingsViewModel,
                                notesRepository = app.notesRepository,
                                statsRepository = app.statsRepository,
                                avatarBaseUrl = app.network.baseUrl,
                                onBookClick = { book -> openBookDetail(navController, book) },
                                onOpenAdmin = { navController.navigate(ROUTE_ADMIN) },
                                onLogout = {
                                    lifecycleScope.launch {
                                        app.authRepository.logout()
                                        // Otherwise a subsequent offline launch would silently let
                                        // this device back in without a real session -- see the
                                        // offline-entry check in LoginViewModel.connect.
                                        app.preferences.setWasLoggedIn(false)
                                        navController.navigate(ROUTE_LOGIN) {
                                            popUpTo(ROUTE_HOME) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable(ROUTE_ADMIN) {
                            val adminViewModel: AdminViewModel = viewModel(
                                factory = AdminViewModel.Factory(app.adminRepository)
                            )
                            val adminTopBarViewModel: TopBarViewModel = viewModel(
                                factory = TopBarViewModel.Factory(app.authRepository)
                            )
                            val currentUser by adminTopBarViewModel.user.collectAsState()
                            AdminScreen(
                                viewModel = adminViewModel,
                                currentUserId = currentUser?.id,
                                avatarBaseUrl = app.network.baseUrl,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = ROUTE_BOOK_DETAIL,
                            arguments = listOf(navArgument(ARG_MANIFEST_URL) { type = NavType.StringType })
                        ) { backStackEntry ->
                            val manifestUrl = Uri.decode(backStackEntry.arguments?.getString(ARG_MANIFEST_URL))
                            val book = app.libraryRepository.findCached(manifestUrl)

                            if (book == null) {
                                // Cache was empty (e.g. process death brought us straight back here) --
                                // there's no single-book endpoint to refetch from, so just back out.
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            } else {
                                val viewModel: BookDetailViewModel = viewModel(
                                    factory = BookDetailViewModel.Factory(
                                        book,
                                        app.positionRepository,
                                        app.notesRepository,
                                        app.annotationsRepository,
                                        app.completedReadsRepository,
                                        app.readingTimerRepository,
                                        app.listeningTimeRepository
                                    )
                                )
                                BookDetailScreen(
                                    book = book,
                                    viewModel = viewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onReadClick = { openReader(book) },
                                    onJumpToLocator = { locator -> openReader(book, locator) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openBookDetail(navController: NavHostController, book: Book) {
        navController.navigate("bookDetail/${Uri.encode(book.manifestUrl())}")
    }

    private fun openReader(book: Book, initialLocator: Locator? = null) {
        val intent = if (book.isAudiobook) {
            Intent(this, AudiobookPlayerActivity::class.java).apply {
                putExtra(AudiobookPlayerActivity.EXTRA_MANIFEST_URL, book.manifestUrl())
                putExtra(AudiobookPlayerActivity.EXTRA_TITLE, book.title)
                putExtra(AudiobookPlayerActivity.EXTRA_AUTHOR, book.author)
                putExtra(AudiobookPlayerActivity.EXTRA_COVER_URL, book.cover)
            }
        } else {
            Intent(this, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_MANIFEST_URL, book.manifestUrl())
                putExtra(ReaderActivity.EXTRA_TITLE, book.title)
                if (initialLocator != null) {
                    putExtra(ReaderActivity.EXTRA_INITIAL_LOCATOR, initialLocator.toJSON().toString())
                }
            }
        }
        startActivity(intent)
    }
}
