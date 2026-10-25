package com.wichosu.simplerunner

import android.app.Application
import com.wichosu.simplerunner.data.AppContainer
import org.maplibre.android.MapLibre

class SimpleRunnerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must happen before any MapView is inflated, or a second map crashes.
        MapLibre.getInstance(this)
    }

    /** Hand-rolled container: one graph for an app this size, no annotation processor. */
    val container: AppContainer by lazy { AppContainer(this) }
}
