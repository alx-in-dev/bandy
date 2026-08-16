package li.cactus.bandy.feature.editor.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import li.cactus.bandy.core.navigation.NavigationConstants
import li.cactus.bandy.core.navigation.NavigationState
import li.cactus.bandy.feature.editor.presentation.EditorScreen

fun NavGraphBuilder.editorScreen(navigationState: NavigationState) {
    composable(
        route = NavigationConstants.Route.EDITOR,
        arguments = listOf(
            navArgument(NavigationConstants.Route.EDITOR_ARG_RECORDING_ID) { type = NavType.LongType },
        ),
    ) { backStackEntry ->
        val recordingId = backStackEntry.arguments
            ?.getLong(NavigationConstants.Route.EDITOR_ARG_RECORDING_ID)
            ?: return@composable
        EditorScreen(recordingId, navigationState)
    }
}
