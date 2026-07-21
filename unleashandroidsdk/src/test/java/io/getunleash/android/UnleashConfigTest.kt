package io.getunleash.android

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class UnleashConfigTest {
    @Test
    fun testThrowsExceptionIfNoProxyUrlAndNoClientKey() {
        assertThatThrownBy {
            UnleashConfig.newBuilder("testApp").build()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun testThrowsExceptionIfNoProxyUrl() {
        assertThatThrownBy {
            UnleashConfig.newBuilder("testApp")
                .clientKey("some-key")
                .build()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun testThrowsExceptionIfNoClientKey() {
        assertThatThrownBy {
            UnleashConfig.newBuilder("testApp")
                .proxyUrl("https://io.getunleash.io/demo/proxy")
                .build()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun testDefaultUnleashConfig() {
        val config = UnleashConfig.newBuilder("testApp")
            .proxyUrl("https://io.getunleash.io/demo/proxy")
            .clientKey("some-key")
            .build()
        assertThat(config.appName).isEqualTo("testApp")
        assertThat(config.instanceId).isNotEmpty()
        assertThat(config.proxyUrl).isEqualTo("https://io.getunleash.io/demo/proxy")
        assertThat(config.clientKey).isEqualTo("some-key")
    }

    @Test
    fun testSdkFlavourConfig() {
        val config = UnleashConfig.newBuilder("testApp")
            .proxyUrl("https://io.getunleash.io/demo/proxy")
            .clientKey("some-key")
            .sdkFlavour("unleash-openfeature-android-provider", "1.2.3")
            .build()

        assertThat(config.sdkFlavour).isEqualTo("unleash-openfeature-android-provider")
        assertThat(config.sdkFlavourVersion).isEqualTo("1.2.3")
    }

    @Test
    fun testDisablingMetricsAndPollingIsPossibleForTesting() {
        val config = UnleashConfig.newBuilder("testApp")
            .pollingStrategy.enabled(false)
            .metricsStrategy.enabled(false)
            .build()

        assertThat(config.proxyUrl).isNull()
        assertThat(config.clientKey).isNull()
    }
}
