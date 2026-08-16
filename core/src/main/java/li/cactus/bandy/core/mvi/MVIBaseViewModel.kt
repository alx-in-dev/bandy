package li.cactus.bandy.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [State] — screen render state. [Action] — one-shot side effects the ViewModel pushes to the
 * UI (navigation, snackbars) collected via [li.cactus.bandy.core.mvi.CollectActions]. [Event] —
 * user-triggered UI events fed back into [obtainEvent].
 */
abstract class MVIBaseViewModel<State, Action, Event>(
    initialState: State,
) : ViewModel() {

    private val _viewState = MutableStateFlow(initialState)
    private val _viewAction = MutableSharedFlow<Action>(extraBufferCapacity = 8)

    fun getViewState(): StateFlow<State> = _viewState.asStateFlow()
    fun getViewAction(): SharedFlow<Action> = _viewAction.asSharedFlow()

    protected val currentState: State get() = _viewState.value

    protected fun setState(reducer: State.() -> State) {
        _viewState.update(reducer)
    }

    protected fun sendAction(action: Action) {
        viewModelScope.launch { _viewAction.emit(action) }
    }

    abstract fun obtainEvent(viewEvent: Event)
}
