package io.getunleash.android

import io.getunleash.android.backup.LocalStorageConfig
import io.getunleash.android.data.DataStrategy
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * Represents configuration for Unleash.
 * @property proxyUrl HTTP(s) URL to the Unleash Proxy (Optional).
 * @property clientKey the key added as the Authorization header sent to the unleash-proxy (Optional)
 * @property appName: name of the underlying application. Will be used as default in the [io.getunleash.android.data.UnleashContext] call (Required).
 * @property pollingStrategy How to poll for features. (Optional - Defaults to [io.getunleash.android.data.DataStrategy] with poll interval set to 60 seconds).
 * @property metricsStrategy How to poll for metrics. (Optional - Defaults to [io.getunleash.android.data.DataStrategy] with poll interval set to 60 seconds).
 * @property httpClient Custom http client to be used. (Optional - Use if you want to pass in custom SSL certificates).
 */
data class UnleashConfig(
    val proxyUrl: String?,
    val clientKey: String?,
    val appName: String,
    val localStorageConfig: LocalStorageConfig = LocalStorageConfig(),
    val pollingStrategy: DataStrategy = DataStrategy(
        pauseOnBackground = true,
    ),
    val metricsStrategy: DataStrategy = DataStrategy(
        pauseOnBackground = true,
    ),
    val delayedInitialization: Boolean = true,
    val forceImpressionData: Boolean = false,
    val httpClient: OkHttpClient? = null,
) {
    companion object {
        val instanceId: String = UUID.randomUUID().toString()
        /**
         * Get a [io.getunleash.android.UnleashConfig.Builder] with default values.
         */
        fun newBuilder(appName: String): Builder = Builder(appName)
    }
    val instanceId: String get() = Companion.instanceId

    fun getApplicationHeaders(strategy: DataStrategy): Map<String, String> {
        val auth = clientKey ?: ""
        return strategy.httpCustomHeaders.plus(mapOf(
            "Authorization" to auth,
            "Content-Type" to "application/json",
            "UNLEASH-APPNAME" to appName,
            "User-Agent" to appName,
            "UNLEASH-INSTANCEID" to instanceId,
            "UNLEASH-CONNECTION-ID" to instanceId,
            "UNLEASH-SDK" to "unleash-android-sdk:" + BuildConfig.VERSION,
        ))
    }

    /**
     * Builder for [io.getunleash.android.UnleashConfig]
     */
    data class Builder(
        private var appName: String,
        private var proxyUrl: String? = null,
        private var clientKey: String? = null
    ) {
        private var delayedInitialization: Boolean = true
        private var forceImpressionData: Boolean = false
        val pollingStrategy: DataStrategy.Builder = DataStrategy()
            .newBuilder(parent = this)
        val metricsStrategy: DataStrategy.Builder = DataStrategy()
            .newBuilder(parent = this)
        val localStorageConfig: LocalStorageConfig.Builder = LocalStorageConfig()
            .newBuilder(parent = this)
        var httpClient: OkHttpClient? = null
        fun build(): UnleashConfig {
            if ((proxyUrl == null || clientKey == null) && (pollingStrategy.enabled || metricsStrategy.enabled)) {
                throw IllegalStateException("You must either set proxyUrl and clientKey or disable both polling and metrics.")
            }
            return UnleashConfig(
                proxyUrl = proxyUrl,
                clientKey = clientKey,
                appName = appName,
                pollingStrategy = pollingStrategy.build(),
                metricsStrategy = metricsStrategy.build(),
                delayedInitialization = delayedInitialization,
                forceImpressionData = forceImpressionData,
                localStorageConfig = localStorageConfig.build(),
                httpClient = httpClient
            )
        }

        fun proxyUrl(proxyUrl: String) = apply { this.proxyUrl = proxyUrl }
        fun clientKey(clientKey: String) = apply { this.clientKey = clientKey }

        fun delayedInitialization(delayedInitialization: Boolean) =
            apply { this.delayedInitialization = delayedInitialization }

        fun forceImpressionData(forceImpressionData: Boolean) =
            apply { this.forceImpressionData = forceImpressionData }

        fun httpClient(httpClient: OkHttpClient) = apply {
            this.httpClient = httpClient
        }
    }
}
