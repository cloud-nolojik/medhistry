package com.medhistry.doctor

import android.app.Application
import com.medhistry.data.MedHistryApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MedHistryDoctorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MedHistryDoctorApp)
            modules(doctorAppModule)
        }
    }
}

val doctorAppModule = module {
    single { MedHistryApi(baseUrl = "https://app.medhistry.com/api/v1") }
}
