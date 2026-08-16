package li.cactus.bandy.feature.library

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import li.cactus.bandy.core.mvi.CollectActions
import li.cactus.bandy.core.navigation.NavigationState
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryScreen(navigationState: NavigationState) {
    val viewModel = koinViewModel<LibraryViewModel>()
    val state by viewModel.getViewState().collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectActions(viewModel) { action ->
        when (action) {
            is LibraryScreenAction.NavigateToEditor ->
                navigationState.navigateToWorkspace(action.recordingId)

            LibraryScreenAction.NavigateToRecord ->
                navigationState.navigateToNewRecording()

            is LibraryScreenAction.ShareRecording -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = action.mimeType
                    putExtra(Intent.EXTRA_STREAM, action.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(sendIntent, null),
                )
            }
        }
    }

    LibraryScreenContent(state = state, onEvent = viewModel::obtainEvent)
}
