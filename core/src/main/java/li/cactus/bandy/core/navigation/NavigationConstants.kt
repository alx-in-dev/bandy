package li.cactus.bandy.core.navigation

object NavigationConstants {
    object Route {
        const val RECORD = "record"
        const val LIBRARY = "library"
        const val EDITOR_ARG_RECORDING_ID = "recordingId"
        const val EDITOR = "editor/{$EDITOR_ARG_RECORDING_ID}"

        fun editor(recordingId: Long) = "editor/$recordingId"
    }
}
