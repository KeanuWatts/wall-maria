package com.aicallscreen

import android.app.Application
import com.aicallscreen.di.ServiceLocator

/**
 * Application entry point. Initializes thread-safe singletons used by [LocalCallScreeningService].
 */
class AiCallScreeningApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
