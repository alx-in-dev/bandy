package li.cactus.bandy.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

class NavigationState(val navController: NavHostController) {

    fun navigateToLibrary() {
        navController.navigate(NavigationConstants.Route.LIBRARY)
    }

    fun navigateToRecord() {
        navController.navigate(NavigationConstants.Route.RECORD) {
            popUpTo(NavigationConstants.Route.RECORD) { inclusive = false }
        }
    }

    fun navigateToEditor(recordingId: Long) {
        navController.navigate(NavigationConstants.Route.editor(recordingId))
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberNavigationState(navController: NavHostController = rememberNavController()): NavigationState =
    remember(navController) { NavigationState(navController) }
