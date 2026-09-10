package com.rockbyte.vicu

import android.app.Application
import com.rockbyte.vicu.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VicuApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VicuApplication)
            modules(appModule)
        }
    }
}
