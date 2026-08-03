package com.ishireader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.reader.ReaderActivity
import com.ishireader.app.ui.bookdetail.BookDetailScreen
import com.ishireader.app.ui.bookdetail.BookDetailViewModel
import com.ishireader.app.ui.home.HomeScreen
import com.ishireader.app.ui.home.HomeViewModel
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.login.LoginScreen
import com.ishireader.app.ui.login.LoginViewModel
import com.ishireader.app.ui.series.SeriesScreen
import com.ishireader.app.ui.series.SeriesViewModel
import com.ishireader.app.ui.theme.IshiReaderTheme

private const val ROUTE_LOGIN = "login"
private const val ROUTE_HOME = "home"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SERIES = "series"
private const val ARG_MANIFEST_URL = "manifestUrl"
private const val ROUTE_BOOK_DETAIL = "bookDetail/{$ARG_MANIFEST_URL}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as IshiReaderApp

        setContent {
            IshiReaderTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
                    composable(ROUTE_LOGIN) {
                        val viewModel: LoginViewModel = viewModel(
                            factory = LoginViewModel.Factory(app.preferences, app.network, app.authRepository)
                        )
                        LoginScreen(
                            viewModel = viewModel,
                            onLoggedIn = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_HOME) {
                        val viewModel: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(app.libraryRepository, app.positionRepository, app.libraryPrefsRepository)
                        )
                        HomeScreen(
                            viewModel = viewModel,
                            onBookClick = { book -> openBookDetail(navController, book) },
                            onViewLibraryClick = { navController.navigate(ROUTE_LIBRARY) },
                            onViewSeriesClick = { navController.navigate(ROUTE_SERIES) }
                        )
                    }
                    composable(ROUTE_LIBRARY) {
                        val viewModel: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.Factory(app.libraryRepository)
                        )
                        LibraryScreen(
                            viewModel = viewModel,
                            onBookClick = { book -> openBookDetail(navController, book) }
                        )
                    }
                    composable(ROUTE_SERIES) {
                        val viewModel: SeriesViewModel = viewModel(
                            factory = SeriesViewModel.Factory(app.libraryRepository)
                        )
                        SeriesScreen(
                            viewModel = viewModel,
                            onBookClick = { book -> openBookDetail(navController, book) }
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
                                factory = BookDetailViewModel.Factory(book, app.positionRepository)
                            )
                            BookDetailScreen(
                                book = book,
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onReadClick = { openReader(book) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openBookDetail(navController: NavHostController, book: Book) {
        navController.navigate("bookDetail/${Uri.encode(book.manifestUrl())}")
    }

    private fun openReader(book: Book) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_MANIFEST_URL, book.manifestUrl())
            putExtra(ReaderActivity.EXTRA_TITLE, book.title)
        }
        startActivity(intent)
    }
}
