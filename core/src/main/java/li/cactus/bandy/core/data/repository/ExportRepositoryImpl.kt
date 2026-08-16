package li.cactus.bandy.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import li.cactus.bandy.core.data.audio.AacEncoder
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.domain.repository.ExportRepository
import li.cactus.bandy.core.domain.repository.StorageRepository

internal class ExportRepositoryImpl(
    private val context: Context,
    private val storageRepository: StorageRepository,
    private val aacEncoder: AacEncoder,
) : ExportRepository {

    override suspend fun encodeToAac(sourceFilePath: String): String {
        val sourceFile = File(sourceFilePath)
        val sampleRate = WavFileReader(sourceFile).sampleRate
        val outputFile = storageRepository.newRecordingFile("m4a")
        aacEncoder.encode(sourceFile, outputFile, sampleRate)
        return outputFile.path
    }

    override fun shareUriFor(filePath: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))
}
