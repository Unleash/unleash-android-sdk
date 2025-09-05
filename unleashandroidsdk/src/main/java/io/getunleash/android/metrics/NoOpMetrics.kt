package io.getunleash.android.metrics

import io.getunleash.android.data.Variant

class NoOpMetrics: MetricsHandler {
    override fun count(featureName: String, enabled: Boolean): Boolean {
        return enabled
    }

    override fun countVariant(featureName: String, variant: Variant): Variant {
        return variant
    }

    override suspend fun sendMetrics(onComplete: ((Result<Unit>) -> Unit)?) {
    }
}