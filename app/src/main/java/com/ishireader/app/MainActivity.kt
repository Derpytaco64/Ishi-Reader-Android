package com.ishireader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.reader.ReaderActivity
import com.ishireader.app.ui.library.LibraryScreen
import com.ishireader.app.ui.library.LibraryViewModel
import com.ishireader.app.ui.login.LoginScreen
import com.ishireader.app.ui.login.LoginViewModel
import com.ishireader.app.ui.theme.IshiReaderTheme

private const val ROUTE_LOGIN = "login"
private const val ROUTE_LIBRARY = "library"

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
                            onBookClick = { book -> openReader(book) }
                        )
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
