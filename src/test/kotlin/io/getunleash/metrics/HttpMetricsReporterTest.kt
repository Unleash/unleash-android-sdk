package io.getunleash.metrics

import io.getunleash.UnleashConfig
import io.getunleash.data.Variant
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HttpMetricsReporterTest {

    @Test
    fun metricsUrlIsCorrect() {
        val okHttpClient = OkHttpClient.Builder().build()
        HttpMetricsReporter(UnleashConfig.newBuilder().proxyUrl("http://localhost:4242/proxy").clientKey("some-key").build(), okHttpClient).use { reporter ->
            assertThat(reporter.metricsUrl.toString()).isEqualTo("http://localhost:4242/proxy/client/metrics")
        }
    }

}
