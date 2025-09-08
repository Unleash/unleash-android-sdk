package io.getunleash.android.metrics

import android.util.Log
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Parser.metricsBodyAdapter
import io.getunleash.android.data.Variant
import io.getunleash.android.http.Throttler
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class MetricsSender(
    private val config: UnleashConfig,
    private val httpClient: OkHttpClient,
    private val applicationHeaders: Map<String, String> = config.getApplicationHeaders(config.metricsStrategy)
): MetricsHandler {
    companion object {
        private const val TAG: String = "MetricsSender"
    }

    private val metricsUrl = config.proxyUrl?.toHttpUrl()?.newBuilder()?.addPathSegment("client")
        ?.addPathSegment("metrics")?.build()
    private var bucket: CountBucket = CountBucket(start = Date())
    private val inFlight = AtomicBoolean(false)
    private val throttler =
        Throttler(
            TimeUnit.MILLISECONDS.toSeconds(config.metricsStrategy.interval),
            longestAcceptableIntervalSeconds = 300,
            metricsUrl.toString()
        )

    override suspend fun sendMetrics(onComplete: ((Result<Unit>) -> Unit)?) {
        if (metricsUrl == null) {
            Log.d(TAG, "No proxy URL configured, skipping metrics reporting")
            return
        }
        if (bucket.isEmpty()) {
            Log.d(TAG, "No metrics to report")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Metrics report already in-flight, skipping this send")
            return
        }
        if (throttler.performAction()) {
            val toReport = swapAndFreeze()
            val payload = MetricsPayload(
                appName = config.appName,
                instanceId = config.instanceId,
                bucket = toReport.first
            )
            val request = Request.Builder()
                .headers(applicationHeaders.toHeaders())
                .url(metricsUrl).post(
                    metricsBodyAdapter.toJson(payload)
                        .toRequestBody("application/json".toMediaType())
                ).build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mergeBack(toReport.second)
                    inFlight.set(false)
                    try {
                        onComplete?.invoke(Result.failure(e))
                    } catch (t: Throwable) {
                        Log.w(TAG, "onComplete callback threw", t)
                    }
                    Log.i(TAG, "Failed to report metrics for interval", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(
                        TAG,
                        "Received status code ${response.code} from ${request.method} $metricsUrl"
                    )
                    throttler.handle(response.code)
                    response.body.use { // Need to consume body to ensure we don't keep connection open
                    }
                    inFlight.set(false)
                    try {
                        onComplete?.invoke(Result.success(Unit))
                    } catch (t: Throwable) {
                        Log.w(TAG, "onComplete callback threw", t)
                    }
                }
            })
        } else {
            throttler.skipped()
            inFlight.set(false)
        }
    }

    private fun swapAndFreeze(): Pair<Bucket, CountBucket> {
        val snapshot = bucket.copy()
        bucket = CountBucket(start = Date())
        return Pair(snapshot.toBucket(bucket.start), snapshot)
    }

    // Note: this does not maintain the initial start time of the snapshot
    private fun mergeBack(snapshot: CountBucket) {
        for ((feature, count) in snapshot.yes) {
            bucket.count(feature, true, count.get())
        }
        for ((feature, count) in snapshot.no) {
            bucket.count(feature, false, count.get())
        }
        for ((pair, count) in snapshot.variants) {
            bucket.countVariant(pair.first, Variant(pair.second), count.get())
        }
    }

    override fun count(featureName: String, enabled: Boolean): Boolean {
        return bucket.count(featureName, enabled)
    }

    override fun countVariant(featureName: String, variant: Variant): Variant {
        return bucket.countVariant(featureName, variant)
    }
}
