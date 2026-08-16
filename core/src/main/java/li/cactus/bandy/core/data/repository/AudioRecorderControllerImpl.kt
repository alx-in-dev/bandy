package li.cactus.bandy.core.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.cactus.bandy.core.data.audio.AudioRecorderDataSource
import li.cactus.bandy.core.data.audio.SpectrumAnalyzer
import li.cactus.bandy.core.domain.model.RecordingPhase
import li.cactus.bandy.core.domain.model.RecordingSession
import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.domain.repository.AudioRecorderController
import li.cactus.bandy.core.domain.repository.StorageRepository

internal class AudioRecorderControllerImpl(
    private val dataSource: AudioRecorderDataSource,
    private val storageRepository: StorageRepository,
) : AudioRecorderController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _session = MutableStateFlow(RecordingSession())
    override val session: StateFlow<RecordingSession> = _session.asStateFlow()

    private val _liveSpectrum = MutableSharedFlow<SpectrumFrame>(extraBufferCapacity = 8)
    override val liveSpectrum: Flow<SpectrumFrame> = _liveSpectrum.asSharedFlow()

    private var analyzer: SpectrumAnalyzer? = null
    private var windowBuffer = ShortArray(0)
    private var windowFillLevel = 0
    private var startedAtMs = 0L
    private var collectJob: Job? = null
    private var tickerJob: Job? = null

    override fun start(sampleRate: Int, fftWindowSize: Int): String {
        analyzer = SpectrumAnalyzer(fftWindowSize)
        windowBuffer = ShortArray(fftWindowSize)
        windowFillLevel = 0

        val path = dataSource.start(sampleRate)
        startedAtMs = System.currentTimeMillis()
        _session.value = RecordingSession(phase = RecordingPhase.RECORDING)

        collectJob = scope.launch {
            dataSource.pcmFlow.collect { chunk -> onChunk(chunk, sampleRate) }
        }
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                if (_session.value.phase == RecordingPhase.RECORDING) {
                    _session.update {
                        it.copy(
                            elapsedMs = System.currentTimeMillis() - startedAtMs,
                            lowStorageWarning = storageRepository.isFreeSpaceLow(),
                        )
                    }
                }
            }
        }
        return path
    }

    private fun onChunk(chunk: ShortArray, sampleRate: Int) {
        val a = analyzer ?: return
        _session.update { it.copy(vuLevel = a.rms(chunk)) }

        var offset = 0
        while (offset < chunk.size) {
            val space = windowBuffer.size - windowFillLevel
            val toCopy = minOf(space, chunk.size - offset)
            System.arraycopy(chunk, offset, windowBuffer, windowFillLevel, toCopy)
            windowFillLevel += toCopy
            offset += toCopy
            if (windowFillLevel == windowBuffer.size) {
                val frame = a.analyzeWindow(windowBuffer, sampleRate, System.currentTimeMillis())
                _liveSpectrum.tryEmit(frame)
                windowFillLevel = 0
            }
        }
    }

    override fun pause() {
        dataSource.pause()
        _session.update { it.copy(phase = RecordingPhase.PAUSED) }
    }

    override fun resume() {
        dataSource.resume()
        _session.update { it.copy(phase = RecordingPhase.RECORDING) }
    }

    override suspend fun stop(): Long {
        tickerJob?.cancel()
        tickerJob = null
        collectJob?.cancel()
        collectJob = null
        val duration = dataSource.stop()
        _session.value = RecordingSession(phase = RecordingPhase.IDLE)
        return duration
    }
}
