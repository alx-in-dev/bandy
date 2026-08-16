package li.cactus.bandy.core.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import li.cactus.bandy.core.domain.repository.StorageRepository

/** Wraps [AudioRecord] capture on a dedicated blocking thread, streaming PCM16 mono chunks and writing them to a WAV file. */
class AudioRecorderDataSource(
    private val storageRepository: StorageRepository,
) {
    private var audioRecord: AudioRecord? = null
    private var writer: WavFileWriter? = null
    @Volatile private var capturing = false
    @Volatile private var paused = false

    private val _pcmFlow = MutableSharedFlow<ShortArray>(extraBufferCapacity = 64)
    val pcmFlow: SharedFlow<ShortArray> = _pcmFlow.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun start(sampleRate: Int): String {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)
        val bufferSizeBytes = minBuffer * 2

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes,
        )

        val file = storageRepository.newRecordingFile("wav")
        val fileWriter = WavFileWriter(file, sampleRate)
        fileWriter.open()

        writer = fileWriter
        audioRecord = record
        capturing = true
        paused = false

        record.startRecording()
        thread(name = "AudioCapture") { captureLoop(bufferSizeBytes / 2) }

        return file.path
    }

    private fun captureLoop(chunkSize: Int) {
        val buffer = ShortArray(chunkSize)
        val record = audioRecord ?: return
        while (capturing) {
            if (paused) {
                Thread.sleep(20)
                continue
            }
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                val chunk = buffer.copyOf(read)
                writer?.writeSamples(chunk, read)
                _pcmFlow.tryEmit(chunk)
            }
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop(): Long {
        capturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val durationMs = writer?.finish() ?: 0L
        writer = null
        return durationMs
    }
}
