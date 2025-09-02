package io.getunleash.unleashandroid

import android.app.Application
import io.getunleash.android.DefaultUnleash
import io.getunleash.android.Unleash
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.events.UnleashReadyListener
import io.getunleash.android.events.UnleashStateListener
import java.util.Date

const val initialFlagValue = "unleash-android-test"
const val initialUserId = "123"
object UnleashStats {
    var readySince: Date? = null
    var lastStateUpdate: Date? = null
}
class TestApplication: Application() {
    val unleashContext: UnleashContext = UnleashContext(userId = initialUserId)
    val unleash: Unleash by lazy {
        val instance = DefaultUnleash(
            androidContext = this,
            unleashConfig = UnleashConfig.newBuilder(appName = "test-android-app")
                .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
                .clientKey("default:development.5d6b7aaeb6a9165f28e91290d13ba0ed39f56f6d9e6952c642fed7cc")
                .pollingStrategy.interval(3000) // 3 secs is just for testing purposes, not recommended for production
                .metricsStrategy.interval(3000) // 3 secs is just for testing purposes, not recommended for production
                .build()
            ,
            unleashContext = unleashContext
        )
        instance.start(listOf(object: UnleashReadyListener, UnleashStateListener {
            override fun onReady() {
                println("Unleash is ready")
                UnleashStats.readySince = Date()
            }

            override fun onStateChanged() {
                UnleashStats.lastStateUpdate = Date()
            }
        }))
        instance
    }

    override fun onTerminate() {
        super.onTerminate()
        unleash.close()
    }
}

