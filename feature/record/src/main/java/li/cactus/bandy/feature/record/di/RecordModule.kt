package li.cactus.bandy.feature.record.di

import li.cactus.bandy.feature.record.presentation.RecordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val recordModule = module {
    viewModelOf(::RecordViewModel)
}
