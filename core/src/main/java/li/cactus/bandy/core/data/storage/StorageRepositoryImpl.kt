package li.cactus.bandy.core.data.storage

import android.content.Context
import android.os.StatFs
import java.io.File
import li.cactus.bandy.core.domain.repository.StorageRepository

private const val LOW_SPACE_THRESHOLD_BYTES = 100L * 1024 * 1024

internal class StorageRepositoryImpl(
    private val context: Context,
) : StorageRepository {

    override fun recordingsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun newRecordingFile(extension: String): File {
        val timestamp = System.currentTimeMillis()
        return File(recordingsDir(), "sift_$timestamp.$extension")
    }

    override fun freeSpaceBytes(): Long {
        val stat = StatFs(recordingsDir().path)
        return stat.availableBytes
    }

    override fun isFreeSpaceLow(): Boolean = freeSpaceBytes() < LOW_SPACE_THRESHOLD_BYTES

    override fun delete(filePath: String) {
        File(filePath).takeIf { it.exists() }?.delete()
    }
}
