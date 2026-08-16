package li.cactus.bandy.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

class NavigationState(val navController: NavHostController) {

    fun navigateToLibrary() {
        navController.navigate(NavigationConstants.Route.LIBRARY)
    }

    /** Opens the merged record+edit screen fresh, for a new recording. */
    fun navigateToNewRecording() {
        navController.navigate(NavigationConstants.Route.WORKSPACE_NEW)
    }

    /** Opens the merged record+edit screen directly in editing mode for an existing recording. */
    fun navigateToWorkspace(recordingId: Long) {
        navController.navigate(NavigationConstants.Route.workspace(recordingId))
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberNavigationState(navController: NavHostController = rememberNavController()): NavigationState =
    remember(navController) { NavigationState(navController) }
