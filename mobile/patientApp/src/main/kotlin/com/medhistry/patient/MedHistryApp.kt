package com.medhistry.patient

import android.app.Application
import com.medhistry.data.MedHistryApi
import com.medhistry.domain.QRSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MedHistryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MedHistryApp)
            modules(appModule)
        }
    }
}

val appModule = module {
    single { MedHistryApi() }
    single { QRSessionManager(get()) }
    single { AuthStore(get()) }
}
