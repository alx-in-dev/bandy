package li.cactus.bandy.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import li.cactus.bandy.core.domain.model.AudioSettings
import li.cactus.bandy.core.domain.model.FftWindowSize
import li.cactus.bandy.core.domain.model.SampleRateOption
import li.cactus.bandy.core.domain.repository.SettingsRepository

private val SAMPLE_RATE_KEY = intPreferencesKey("sample_rate_hz")
private val FFT_WINDOW_KEY = intPreferencesKey("fft_window_size")

internal class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AudioSettings> = dataStore.data.map { prefs ->
        AudioSettings(
            sampleRate = SampleRateOption.entries.firstOrNull { it.hz == prefs[SAMPLE_RATE_KEY] }
                ?: SampleRateOption.SR_44100,
            fftWindowSize = FftWindowSize.entries.firstOrNull { it.samples == prefs[FFT_WINDOW_KEY] }
                ?: FftWindowSize.SIZE_2048,
        )
    }

    override suspend fun setSampleRate(option: SampleRateOption) {
        dataStore.edit { it[SAMPLE_RATE_KEY] = option.hz }
    }

    override suspend fun setFftWindowSize(size: FftWindowSize) {
        dataStore.edit { it[FFT_WINDOW_KEY] = size.samples }
    }
}
