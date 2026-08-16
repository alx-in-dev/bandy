package li.cactus.bandy.feature.record.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import li.cactus.bandy.core.navigation.NavigationConstants
import li.cactus.bandy.core.navigation.NavigationState
import li.cactus.bandy.feature.record.presentation.RecordScreen

fun NavGraphBuilder.recordScreen(navigationState: NavigationState) {
    composable(route = NavigationConstants.Route.RECORD) {
        RecordScreen(navigationState)
    }
}
