package li.cactus.bandy.core.di

import li.cactus.bandy.core.data.audio.AacEncoder
import li.cactus.bandy.core.data.audio.AudioRecorderDataSource
import li.cactus.bandy.core.data.repository.AudioAnalysisRepositoryImpl
import li.cactus.bandy.core.data.repository.AudioFilterRepositoryImpl
import li.cactus.bandy.core.data.repository.AudioRecorderControllerImpl
import li.cactus.bandy.core.data.repository.ExportRepositoryImpl
import li.cactus.bandy.core.data.repository.LivePreviewPlayerImpl
import li.cactus.bandy.core.data.repository.PeriodicityRepositoryImpl
import li.cactus.bandy.core.domain.repository.AudioAnalysisRepository
import li.cactus.bandy.core.domain.repository.AudioFilterRepository
import li.cactus.bandy.core.domain.repository.AudioRecorderController
import li.cactus.bandy.core.domain.repository.ExportRepository
import li.cactus.bandy.core.domain.repository.LivePreviewPlayer
import li.cactus.bandy.core.domain.repository.PeriodicityRepository
import li.cactus.bandy.core.domain.usecase.AnalyzePeriodicityUseCase
import li.cactus.bandy.core.domain.usecase.AnalyzePeriodicityUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ApplyBandPassFilterUseCase
import li.cactus.bandy.core.domain.usecase.ApplyBandPassFilterUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ApplyCombFilterUseCase
import li.cactus.bandy.core.domain.usecase.ApplyCombFilterUseCaseImpl
import li.cactus.bandy.core.domain.usecase.BuildSynchronousAverageUseCase
import li.cactus.bandy.core.domain.usecase.BuildSynchronousAverageUseCaseImpl
import li.cactus.bandy.core.domain.usecase.GetAveragedSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.GetAveragedSpectrumUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ObserveLiveSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.ObserveLiveSpectrumUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ObserveRecordingSessionUseCase
import li.cactus.bandy.core.domain.usecase.ObserveRecordingSessionUseCaseImpl
import li.cactus.bandy.core.domain.usecase.PreviewPlaybackUseCase
import li.cactus.bandy.core.domain.usecase.PreviewPlaybackUseCaseImpl
import li.cactus.bandy.core.domain.usecase.RecordingControlUseCase
import li.cactus.bandy.core.domain.usecase.RecordingControlUseCaseImpl
import li.cactus.bandy.core.domain.usecase.ShareRecordingUseCase
import li.cactus.bandy.core.domain.usecase.ShareRecordingUseCaseImpl
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val audioModule = module {
    singleOf(::AudioRecorderDataSource)
    singleOf(::AudioRecorderControllerImpl) bind AudioRecorderController::class
    singleOf(::AacEncoder)

    factoryOf(::AudioAnalysisRepositoryImpl) bind AudioAnalysisRepository::class
    factoryOf(::AudioFilterRepositoryImpl) bind AudioFilterRepository::class
    // single: playback must be shared across screens (editor preview, library quick-play) —
    // starting one stops the other instead of two AudioTracks playing over each other.
    singleOf(::LivePreviewPlayerImpl) bind LivePreviewPlayer::class
    factoryOf(::ExportRepositoryImpl) bind ExportRepository::class
    factoryOf(::PeriodicityRepositoryImpl) bind PeriodicityRepository::class

    factoryOf(::RecordingControlUseCaseImpl) bind RecordingControlUseCase::class
    factoryOf(::ObserveRecordingSessionUseCaseImpl) bind ObserveRecordingSessionUseCase::class
    factoryOf(::ObserveLiveSpectrumUseCaseImpl) bind ObserveLiveSpectrumUseCase::class
    factoryOf(::GetAveragedSpectrumUseCaseImpl) bind GetAveragedSpectrumUseCase::class
    factoryOf(::ApplyBandPassFilterUseCaseImpl) bind ApplyBandPassFilterUseCase::class
    singleOf(::PreviewPlaybackUseCaseImpl) bind PreviewPlaybackUseCase::class
    factoryOf(::ShareRecordingUseCaseImpl) bind ShareRecordingUseCase::class
    factoryOf(::AnalyzePeriodicityUseCaseImpl) bind AnalyzePeriodicityUseCase::class
    factoryOf(::ApplyCombFilterUseCaseImpl) bind ApplyCombFilterUseCase::class
    factoryOf(::BuildSynchronousAverageUseCaseImpl) bind BuildSynchronousAverageUseCase::class
}
