package li.cactus.bandy.feature.library.di

import li.cactus.bandy.feature.library.LibraryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryModule = module {
    viewModelOf(::LibraryViewModel)
}
