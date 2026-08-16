package li.cactus.bandy.core.navigation

object NavigationConstants {
    object Route {
        const val LIBRARY = "library"
        const val WORKSPACE_ARG_RECORDING_ID = "recordingId"
        const val WORKSPACE_NEW = "workspace"
        const val WORKSPACE = "workspace?recordingId={$WORKSPACE_ARG_RECORDING_ID}"

        fun workspace(recordingId: Long) = "workspace?recordingId=$recordingId"
    }
}
