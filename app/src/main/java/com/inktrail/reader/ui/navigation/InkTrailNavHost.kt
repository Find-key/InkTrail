package com.inktrail.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inktrail.reader.ui.bookshelf.BookshelfRoute
import com.inktrail.reader.ui.reader.ReaderRoute

object InkTrailDestinations {
    const val BOOKSHELF = "bookshelf"
    const val READER_ROUTE = "reader/{bookId}"

    fun reader(bookId: String): String = "reader/${Uri.encode(bookId)}"
}

@Composable
fun InkTrailNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = InkTrailDestinations.BOOKSHELF
    ) {
        composable(route = InkTrailDestinations.BOOKSHELF) {
            BookshelfRoute(
                onOpenBook = { bookId ->
                    navController.navigate(InkTrailDestinations.reader(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = InkTrailDestinations.READER_ROUTE,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            ReaderRoute(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
