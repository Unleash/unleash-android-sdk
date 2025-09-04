package io.getunleash.android

import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashListener
import java.io.Closeable

val disabledVariant = Variant("disabled")

interface Unleash: Closeable {
    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant = disabledVariant): Variant

    /**
     * Set context and trigger a fetch of the latest toggles immediately and block until the fetch is complete or failed.
     */
    fun setContext(context: UnleashContext)

    /**
     * Set context and trigger a fetch of the latest toggles asynchronously
     */
    fun setContextWithTimeout(context: UnleashContext, timeout: Long = 5000)

    /**
     * Set context and trigger a fetch of the latest toggles asynchronously
     */
    fun setContextAsync(context: UnleashContext)

    fun addUnleashEventListener(listener: UnleashListener)

    // Added to allow removing a previously added listener and stop its coroutines
    fun removeUnleashEventListener(listener: UnleashListener)

    /**
     * This function forces a refresh of the toggles from the server and wait until the refresh is complete or failed.
     * Usually, this is done automatically in the background, but you can call this function to force a refresh.
     */
    fun refreshTogglesNow()

    /**
     * This function forces a refresh of the toggles from the server asynchronously using the IO dispatcher.
     * Usually, this is done automatically in the background, but you can call this function to force a refresh.
     */
    fun refreshTogglesNowAsync()

    /**
     * This function forces send metrics to the server and wait until the send is complete or failed.
     * Usually, this is done automatically in the background, but you can call this function to force a send.
     */
    fun sendMetricsNow()

    /**
     * This function forces send metrics to the server asynchronously using the IO dispatcher.
     * Usually, this is done automatically in the background, but you can call this function to force a send.
     */
    fun sendMetricsNowAsync()

    fun isReady(): Boolean
}
