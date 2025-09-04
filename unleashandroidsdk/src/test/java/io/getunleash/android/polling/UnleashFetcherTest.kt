package io.getunleash.android.polling

import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.UnleashContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class UnleashFetcherTest : BaseTest() {

    @Test
    fun `should fetch toggles after initialization`() {
        // Given
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
            this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        // When
        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )
        unleashFetcher.startWatchingContext()

        // Then
        val request = server.takeRequest()
        assertThat(request).isNotNull
        assertThat(request.path).isEqualTo("/unleash?appName=test-app&userId=123")
        assertThat(request.method).isEqualTo("GET")  // or POST, PUT, etc.
    }

    @Test
    fun `changing the context should cancel in flight requests`() {
        // Given
        val server = MockWebServer()
        // a slow request
        server.enqueue(
            MockResponse()
                .setHeadersDelay(1000, TimeUnit.MILLISECONDS)
                .setBody(this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )

        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        // When
        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )

        runBlocking {
            launch {
                println("Setting context to 123")
                unleashFetcher.doFetchToggles(UnleashContext(userId = "123"))
            }
            delay(150)
            launch {
                println("Setting context to 321")
                unleashFetcher.doFetchToggles(UnleashContext(userId = "321"))
            }
        }

        // Then
        val firstRequest = server.takeRequest()
        assertThat(firstRequest.bodySize).isEqualTo(0)
        assertThat(firstRequest.path).isEqualTo("/unleash?appName=test-app&userId=123")

        val secondRequest = server.takeRequest()
        assertThat(secondRequest.path).isEqualTo("/unleash?appName=test-app&userId=321")
    }

    @Test
    fun `refreshNow with force should bypass context equality and perform fetch`() {
        // Given
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )

        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )

        runBlocking {
            // initial fetch to set contextForLastFetch
            unleashFetcher.doFetchToggles(UnleashContext(userId = "123"))
        }

        // first request consumed
        val firstRequest = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(firstRequest).isNotNull
        assertThat(firstRequest!!.path).isEqualTo("/unleash?appName=test-app&userId=123")

        runBlocking {
            // non-forced refresh should detect unchanged context and skip (no network call)
            val resp = unleashFetcher.refreshToggles()
            assertThat(resp.status.isNotModified()).isTrue()
        }

        // there should be no new request within a short timeout
        val noReq = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertThat(noReq).isNull()

        runBlocking {
            // forced refresh should perform network call even if context unchanged
            val forced = unleashFetcher.refreshToggles()
            // response should be success (second enqueued response)
            assertThat(forced.status.isSuccess()).isTrue()
        }

        val secondRequest = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(secondRequest).isNotNull
        assertThat(secondRequest!!.path).isEqualTo("/unleash?appName=test-app&userId=123")
    }

    @Test
    fun `scheduled polling or setContext path should skip fetch when context unchanged`() {
        // Given
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )

        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )

        runBlocking {
            // simulate initial fetch via explicit context-based fetch path
            val initial = unleashFetcher.refreshTogglesWithContext(UnleashContext(userId = "123"))
            assertThat(initial.status.isSuccess()).isTrue()
        }

        val firstRequest = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(firstRequest).isNotNull
        assertThat(firstRequest!!.path).isEqualTo("/unleash?appName=test-app&userId=123")

        runBlocking {
            // scheduled polling (non-forced) should skip because context unchanged
            val skipped = unleashFetcher.refreshToggles()
            assertThat(skipped.status.isNotModified()).isTrue()
        }

        val noReq = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertThat(noReq).isNull()

        // simulate setContext behavior: calling refreshTogglesWithContext with same ctx should skip
        runBlocking {
            val skipped2 = unleashFetcher.refreshTogglesWithContext(UnleashContext(userId = "123"))
            assertThat(skipped2.status.isNotModified()).isTrue()
        }

        val stillNoReq = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertThat(stillNoReq).isNull()
    }
}