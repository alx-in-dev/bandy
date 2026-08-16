package li.cactus.bandy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import li.cactus.bandy.core.navigation.NavigationConstants
import li.cactus.bandy.core.navigation.rememberNavigationState
import li.cactus.bandy.feature.editor.navigation.editorScreen
import li.cactus.bandy.feature.library.libraryScreen
import li.cactus.bandy.feature.record.navigation.recordScreen
import li.cactus.bandy.ui.theme.BandyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BandyTheme {
                SiftApp()
            }
        }
    }
}

@Composable
private fun SiftApp() {
    val navigationState = rememberNavigationState()
    NavHost(
        navController = navigationState.navController,
        startDestination = NavigationConstants.Route.RECORD,
        modifier = Modifier.fillMaxSize(),
    ) {
        recordScreen(navigationState)
        editorScreen(navigationState)
        libraryScreen(navigationState)
    }
}
