package li.cactus.bandy

import android.app.Application
import li.cactus.bandy.core.di.appModule
import li.cactus.bandy.core.di.audioModule
import li.cactus.bandy.feature.editor.di.editorModule
import li.cactus.bandy.feature.library.di.libraryModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SiftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SiftApplication)
            modules(appModule, audioModule, editorModule, libraryModule)
        }
    }
}
