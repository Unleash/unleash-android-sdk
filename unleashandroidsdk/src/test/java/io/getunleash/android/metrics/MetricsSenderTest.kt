package io.getunleash.android.metrics

import android.content.Context
import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Payload
import io.getunleash.android.data.Variant
import io.getunleash.android.http.ClientBuilder
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit
import net.javacrumbs.jsonunit.assertj.assertThatJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.awaitility.Awaitility.await
import java.math.BigDecimal
import java.math.BigDecimal.valueOf
import java.util.concurrent.CountDownLatch

class MetricsSenderTest : BaseTest() {
    var server: MockWebServer  = MockWebServer()
    var proxyUrl: String = ""
    var configBuilder: UnleashConfig.Builder = UnleashConfig.newBuilder("my-test-app")
        .clientKey("some-key")
        .pollingStrategy.enabled(false)
        .localStorageConfig.enabled(false)

    @Before
    fun setUp() {
        server = MockWebServer()
        proxyUrl = server.url("proxy").toString()
        configBuilder = configBuilder.proxyUrl(proxyUrl)
    }

    @Test
    fun `does not push metrics if no metrics`() = runTest {
        val config = configBuilder.build()
        val httpClient = ClientBuilder(config, mock(Context::class.java)).build("test", config.metricsStrategy)
        val metricsSender = MetricsSender(config, httpClient)

        metricsSender.sendMetrics()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `pushes metrics if metrics`() = runTest {
        val config = configBuilder.build()
        val httpClient = ClientBuilder(config, mock(Context::class.java)).build("test", config.metricsStrategy)
        val metricsSender = MetricsSender(config, httpClient)

        metricsSender.count("feature1", true)
        metricsSender.count("feature2", false)
        metricsSender.countVariant(
            "feature2",
            Variant(
                "variant1",
                enabled = true,
                featureEnabled = false,
                Payload("string", "my variant")
            )
        )
        metricsSender.count("feature1", false)
        metricsSender.count("feature1", true)
        val today = java.time.LocalDate.now()
        metricsSender.sendMetrics()
        val request = server.takeRequest(
            1,
            TimeUnit.SECONDS
        )!!
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/proxy/client/metrics")
        assertThatJson(request.body.readUtf8()) {
            node("appName").isString().isEqualTo("my-test-app")
            node("instanceId").isString().matches(".+")
            node("bucket").apply {
                node("start").isString().matches("${today}T.+")
                node("stop").isString().matches("${today}T.+")
                node("toggles").apply {
                    node("feature1").apply {
                        node("yes").isNumber().isEqualTo(valueOf(2))
                        node("no").isNumber().isEqualTo(valueOf(1))
                        node("variants").isObject().isEqualTo(emptyMap<String, Any>())
                    }
                    node("feature2").apply {
                        node("yes").isNumber().isEqualTo(valueOf(0))
                        node("no").isNumber().isEqualTo(valueOf(1))
                        node("variants").apply {
                            node("variant1").isNumber().isEqualTo(valueOf(1))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `failed send merges metrics back and next successful send includes both old and new`() = runTest {
        val config = configBuilder.build()
        val httpClient = ClientBuilder(config, mock(Context::class.java)).build("test", config.metricsStrategy)
        val metricsSender = MetricsSender(config, httpClient)

        // Initial metrics that will be part of the failed request
        metricsSender.count("featureA", true)
        metricsSender.count("featureB", true)

        val latch = CountDownLatch(1)
        var calls = 0
        // Make the server drop the connection to simulate a network failure (500, 429, etc. would throttle further requests)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        metricsSender.sendMetrics { latch.countDown(); calls++ }
        metricsSender.count("featureA", true) // in between the failed send and the retry, add another metric
        metricsSender.sendMetrics { calls++ } // This should be skipped since a send is in-flight
        metricsSender.sendMetrics { calls++ } // This should be skipped since a send is in-flight

        // Ensure the server received the (failed) request (may be disconnected)
        server.takeRequest(1, TimeUnit.SECONDS)
        latch.await(1, TimeUnit.SECONDS)
        assertThat(calls).isEqualTo(1) // Only the first call should have been executed

        // Add another metric after the failed send - should be merged with the snapshot
        metricsSender.count("featureB", true)

        // Now enqueue a successful response for the retry/send that should contain both counts
        server.enqueue(MockResponse().setResponseCode(200))
        metricsSender.sendMetrics()

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/proxy/client/metrics")
        // The combined count for featureA should be 2 (one from failed snapshot + one new) and for featureB should be 1 (from failed snapshot)
        assertThatJson(request.body.readUtf8()) {
            node("bucket").apply {
                node("toggles").apply {
                    node("featureA").apply {
                        node("yes").isNumber().isEqualTo(BigDecimal(2))
                    }
                    node("featureB").apply {
                        node("yes").isNumber().isEqualTo(BigDecimal(2))
                    }
                }
            }
        }
    }
}