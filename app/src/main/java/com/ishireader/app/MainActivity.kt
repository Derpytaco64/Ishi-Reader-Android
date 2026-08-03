package com.ishireader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.login.LoginScreen
import com.ishireader.app.ui.login.LoginViewModel
import com.ishireader.app.ui.theme.IshiReaderTheme

private const val ROUTE_LOGIN = "login"
private const val ROUTE_LIBRARY = "library"
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
                                navController.navigate(ROUTE_LIBRARY) {
                                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_LIBRARY) {
                        val viewModel: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.Factory(app.libraryRepository)
                        )
                        LibraryScreen(
                            viewModel = viewModel,
                            onBookClick = { book ->
                                navController.navigate("bookDetail/${Uri.encode(book.manifestUrl())}")
                            }
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

    private fun openReader(book: Book) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_MANIFEST_URL, book.manifestUrl())
            putExtra(ReaderActivity.EXTRA_TITLE, book.title)
        }
        startActivity(intent)
    }
}
