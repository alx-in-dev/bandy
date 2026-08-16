package li.cactus.bandy.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import li.cactus.bandy.core.navigation.NavigationConstants
import li.cactus.bandy.core.navigation.NavigationState

fun NavGraphBuilder.libraryScreen(navigationState: NavigationState) {
    composable(route = NavigationConstants.Route.LIBRARY) {
        LibraryScreen(navigationState)
    }
}
