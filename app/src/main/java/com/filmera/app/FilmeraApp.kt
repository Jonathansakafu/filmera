package com.filmera.app

import android.app.Application
import android.content.Context
import com.filmera.app.core.media.CaptureRepository
import com.filmera.app.core.media.ShareHelper
import com.filmera.app.data.SettingsRepository

/** App-wide singletons. Small enough that a DI framework isn't worth its build cost yet. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val captures = CaptureRepository(context)
    val share = ShareHelper(context)
}

class FilmeraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as FilmeraApp).container
