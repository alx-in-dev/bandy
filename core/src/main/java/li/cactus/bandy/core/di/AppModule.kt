package li.cactus.bandy.core.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import li.cactus.bandy.core.data.repository.RecordingRepositoryImpl
import li.cactus.bandy.core.data.storage.SettingsRepositoryImpl
import li.cactus.bandy.core.data.storage.SiftDatabase
import li.cactus.bandy.core.data.storage.StorageRepositoryImpl
import li.cactus.bandy.core.domain.repository.RecordingRepository
import li.cactus.bandy.core.domain.repository.SettingsRepository
import li.cactus.bandy.core.domain.repository.StorageRepository
import li.cactus.bandy.core.domain.usecase.DeleteRecordingUseCase
import li.cactus.bandy.core.domain.usecase.DeleteRecordingUseCaseImpl
import li.cactus.bandy.core.domain.usecase.GetRecordingUseCase
import li.cactus.bandy.core.domain.usecase.GetRecordingUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ObserveAudioSettingsUseCase
import li.cactus.bandy.core.domain.usecase.ObserveAudioSettingsUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ObserveRecordingsUseCase
import li.cactus.bandy.core.domain.usecase.ObserveRecordingsUseCaseImpl
import li.cactus.bandy.core.domain.usecase.RenameRecordingUseCase
import li.cactus.bandy.core.domain.usecase.RenameRecordingUseCaseImpl
import li.cactus.bandy.core.domain.usecase.UpdateAudioSettingsUseCase
import li.cactus.bandy.core.domain.usecase.UpdateAudioSettingsUseCaseImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.settingsDataStore by preferencesDataStore(name = "sift_settings")

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), SiftDatabase::class.java, "sift.db").build()
    }
    single { get<SiftDatabase>().recordingDao() }
    single { androidContext().settingsDataStore }

    singleOf(::StorageRepositoryImpl) bind StorageRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class

    factoryOf(::ObserveRecordingsUseCaseImpl) bind ObserveRecordingsUseCase::class
    factoryOf(::GetRecordingUseCaseImpl) bind GetRecordingUseCase::class
    factoryOf(::RenameRecordingUseCaseImpl) bind RenameRecordingUseCase::class
    factoryOf(::DeleteRecordingUseCaseImpl) bind DeleteRecordingUseCase::class
    factoryOf(::ObserveAudioSettingsUseCaseImpl) bind ObserveAudioSettingsUseCase::class
    factoryOf(::UpdateAudioSettingsUseCaseImpl) bind UpdateAudioSettingsUseCase::class
}
