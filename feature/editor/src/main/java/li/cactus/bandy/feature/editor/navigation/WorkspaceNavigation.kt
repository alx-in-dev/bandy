package li.cactus.bandy.feature.editor.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import li.cactus.bandy.core.navigation.NavigationConstants
import li.cactus.bandy.core.navigation.NavigationState
import li.cactus.bandy.feature.editor.presentation.WorkspaceScreen

/** Registers both the "new recording" (no arg) and "edit existing" (recordingId arg) entry
 * points — they're the same merged record+edit screen, just started in a different phase. */
fun NavGraphBuilder.workspaceScreen(navigationState: NavigationState) {
    composable(
        route = NavigationConstants.Route.WORKSPACE,
        arguments = listOf(
            navArgument(NavigationConstants.Route.WORKSPACE_ARG_RECORDING_ID) {
                type = NavType.LongType
                defaultValue = -1L
            }
        ),
    ) { backStackEntry ->
        val recordingId = backStackEntry.arguments
            ?.getLong(NavigationConstants.Route.WORKSPACE_ARG_RECORDING_ID)
            ?.takeIf { it >= 0 }
        WorkspaceScreen(recordingId, navigationState)
    }
}
