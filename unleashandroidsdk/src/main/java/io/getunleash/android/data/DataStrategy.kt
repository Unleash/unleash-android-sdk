package io.getunleash.android.data

import io.getunleash.android.UnleashConfig

/**
 * @property enabled Whether the strategy is enabled or not. (Optional - Defaults to true)
 * @property interval How often to perform the operation in milliseconds. (Optional - Defaults to 60000)
 * @property delay How long to wait before starting the operation in milliseconds. (Optional - Defaults to 0)
 * @property pauseOnBackground Whether the operation should pause when the app is in background. (Optional - Defaults to true)
 * @property httpReadTimeout How long to wait for HTTP reads in milliseconds. (Optional - Defaults to 5000)
 * @property httpWriteTimeout How long to wait for HTTP writes in milliseconds. (Optional - Defaults to 5000)
 * @property httpConnectionTimeout How long to wait for HTTP connection in milliseconds. (Optional - Defaults to 2000)
 * @property httpCacheSize Disk space (in bytes) set aside for http cache. (Optional - Defaults to 10MB)
 * @property httpCustomHeaders Enables users to override httpCustomHeaders. (Optional - Defaults to empty)
 */
data class DataStrategy(
    val enabled: Boolean = true,
    val interval: Long = 60000,
    val delay: Long = 0,
    val pauseOnBackground: Boolean = true,
    val httpConnectionTimeout: Long = 2000,
    val httpReadTimeout: Long = 5000,
    val httpWriteTimeout: Long = 5000,
    val httpCacheSize: Long = 1024 * 1024 * 10,
    val httpCustomHeaders: Map<String, String> = emptyMap(),
) {
    fun newBuilder(parent: UnleashConfig.Builder): Builder =
        Builder(
            parent = parent,
            enabled = enabled,
            interval = interval,
            delay = delay,
            pauseOnBackground = pauseOnBackground,
            httpConnectionTimeout = httpConnectionTimeout,
            httpReadTimeout = httpReadTimeout,
            httpCacheSize = httpCacheSize,
            httpCustomHeaders = httpCustomHeaders
        )

    class Builder(
        val parent: UnleashConfig.Builder,
        var enabled: Boolean,
        var interval: Long,
        var delay: Long,
        var pauseOnBackground: Boolean,
        var httpConnectionTimeout: Long,
        var httpReadTimeout: Long,
        var httpCacheSize: Long,
        var httpCustomHeaders: Map<String, String>
    ) {
        fun build(): DataStrategy {
            return DataStrategy(
                enabled = enabled,
                interval = interval,
                delay = delay,
                pauseOnBackground = pauseOnBackground,
                httpConnectionTimeout = httpConnectionTimeout,
                httpReadTimeout = httpReadTimeout,
                httpCacheSize = httpCacheSize,
                httpCustomHeaders = httpCustomHeaders
            )
        }

        fun enabled(enabled: Boolean): UnleashConfig.Builder {
            this.enabled = enabled
            return parent
        }

        fun interval(interval: Long): UnleashConfig.Builder {
            this.interval = interval
            return parent
        }

        fun delay(delay: Long): UnleashConfig.Builder {
            this.delay = delay
            return parent
        }

        fun pauseOnBackground(pauseOnBackground: Boolean): UnleashConfig.Builder {
            this.pauseOnBackground = pauseOnBackground
            return parent
        }

        fun httpConnectionTimeout(httpConnectionTimeout: Long): UnleashConfig.Builder {
            this.httpConnectionTimeout = httpConnectionTimeout
            return parent
        }

        fun httpReadTimeout(httpReadTimeout: Long): UnleashConfig.Builder {
            this.httpReadTimeout = httpReadTimeout
            return parent
        }

        fun httpCacheSize(httpCacheSize: Long): UnleashConfig.Builder {
            this.httpCacheSize = httpCacheSize
            return parent
        }

        fun httpCustomHeaders(httpCustomHeaders: Map<String, String>): UnleashConfig.Builder {
            this.httpCustomHeaders = httpCustomHeaders
            return parent
        }
    }
}



