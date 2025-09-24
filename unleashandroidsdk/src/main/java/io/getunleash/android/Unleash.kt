package io.getunleash.android

import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashListener
import java.io.Closeable
import java.io.File

val disabledVariant = Variant("disabled")

interface Unleash: Closeable {
    /**
     * Check if a toggle is enabled or disabled
     */
    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    /**
     * Get the variant for a toggle
     */
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

    /** Add a listener to be notified of Unleash events */
    fun addUnleashEventListener(listener: UnleashListener)

    /** Remove previously added listener */
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

    /**
     * Check if the Unleash client is ready to be used.
     * If you have disabled delayedInitialization in the [io.getunleash.android.UnleashConfig] this will always be true.
     * If you have enabled delayedInitialization in the [io.getunleash.android.UnleashConfig] this will be true
     * once the initial fetch of toggles has been completed or failed.
     */
    fun isReady(): Boolean

    /**
     * Starts Unleash manually
     */
    fun start(
        eventListeners: List<UnleashListener> = emptyList(),
        bootstrapFile: File? = null,
        bootstrap: List<Toggle> = emptyList()
    )
}
