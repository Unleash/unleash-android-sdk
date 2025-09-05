package io.getunleash.android.metrics

interface MetricsReporter {

    suspend fun sendMetrics(onComplete: ((Result<Unit>) -> Unit)? = null)
}
