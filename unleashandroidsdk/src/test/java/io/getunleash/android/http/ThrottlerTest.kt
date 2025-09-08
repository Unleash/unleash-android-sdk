package io.getunleash.android.http

import io.getunleash.android.BaseTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.MalformedURLException

class ThrottlerTest : BaseTest(){

    @Test
    @Throws(MalformedURLException::class)
    fun shouldNeverDecrementFailuresOrSkipsBelowZero() {
        val throttler = Throttler(10, 300, "https://localhost:1500/api")
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        assertThat(throttler.getSkips()).isEqualTo(0)
        assertThat(throttler.getFailures()).isEqualTo(0)
    }

    @Test
    @Throws(MalformedURLException::class)
    fun setToMaxShouldReduceDownEventually() {
        val throttler = Throttler(150, 300, "https://localhost:1500/api")
        throttler.handleHttpErrorCodes(404)
        assertThat(throttler.getSkips()).isEqualTo(2)
        assertThat(throttler.getFailures()).isEqualTo(1)
        runBlocking { throttler.runIfAllowed { /* skipped this interval */ } }
        assertThat(throttler.getSkips()).isEqualTo(1)
        assertThat(throttler.getFailures()).isEqualTo(1)
        runBlocking { throttler.runIfAllowed { /* skipped this interval */ } }
        assertThat(throttler.getSkips()).isEqualTo(0)
        assertThat(throttler.getFailures()).isEqualTo(1)
        throttler.decrementFailureCountAndResetSkips()
        assertThat(throttler.getSkips()).isEqualTo(0)
        assertThat(throttler.getFailures()).isEqualTo(0)
        throttler.decrementFailureCountAndResetSkips()
        assertThat(throttler.getSkips()).isEqualTo(0)
        assertThat(throttler.getFailures()).isEqualTo(0)
    }

    @Test
    @Throws(MalformedURLException::class)
    fun handleIntermittentFailures() {
        val throttler = Throttler(50, 300, "https://localhost:1500/api")
        throttler.handleHttpErrorCodes(429)
        throttler.handleHttpErrorCodes(429)
        throttler.handleHttpErrorCodes(503)
        throttler.handleHttpErrorCodes(429)
        assertThat(throttler.getSkips()).isEqualTo(4)
        assertThat(throttler.getFailures()).isEqualTo(4)
        throttler.decrementFailureCountAndResetSkips()
        assertThat(throttler.getSkips()).isEqualTo(3)
        assertThat(throttler.getFailures()).isEqualTo(3)
        throttler.handleHttpErrorCodes(429)
        assertThat(throttler.getSkips()).isEqualTo(4)
        assertThat(throttler.getFailures()).isEqualTo(4)
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        throttler.decrementFailureCountAndResetSkips()
        assertThat(throttler.getSkips()).isEqualTo(0)
        assertThat(throttler.getFailures()).isEqualTo(0)
    }
}
