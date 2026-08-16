package li.cactus.bandy.core.domain.repository

interface ExportRepository {
    /** Encodes the WAV at [sourceFilePath] to AAC (M4A) via MediaCodec+MediaMuxer, returns output path. */
    suspend fun encodeToAac(sourceFilePath: String): String

    /** Content:// URI (via FileProvider) for a file, suitable for ACTION_SEND. */
    fun shareUriFor(filePath: String): android.net.Uri
}
