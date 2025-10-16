package io.getunleash.android.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.getunleash.android.util.UnleashLogger
import java.util.concurrent.atomic.AtomicInteger

interface NetworkListener {
    fun onAvailable()
    fun onLost()
}

class NetworkStatusHelper(
    private val context: Context,
    private val scheduleRetry: (Long, () -> Unit) -> Unit = { delayMs, action ->
        Handler(Looper.getMainLooper()).postDelayed(action, delayMs)
    }
) {
    companion object {
        private const val TAG = "NetworkState"
        internal const val MAX_REGISTRATION_ATTEMPTS = 5
        private const val REGISTRATION_RETRY_DELAY_MS = 200L
    }

    internal val networkCallbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    private val availableNetworks = mutableSetOf<Network>()

    private val registrationEpoch = AtomicInteger(0)

    fun registerNetworkListener(listener: NetworkListener) {
        val epoch = registrationEpoch.get()
        registerNetworkListener(listener, MAX_REGISTRATION_ATTEMPTS, epoch)
    }

    private fun registerNetworkListener(
        listener: NetworkListener,
        remainingAttempts: Int,
        epoch: Int
    ) {
        if (epoch != registrationEpoch.get()) {
            UnleashLogger.d(TAG, "Skipping stale network registration attempt")
            return
        }

        val attemptNumber = MAX_REGISTRATION_ATTEMPTS - remainingAttempts + 1
        try {
            val connectivityManager = getConnectivityManager() ?: return
            val networkRequest = buildNetworkRequest()

            val networkCallback = buildCallback(listener)
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            if (epoch != registrationEpoch.get()) {
                UnleashLogger.d(TAG, "Registration completed for stale attempt; unregistering callback")
                connectivityManager.unregisterNetworkCallback(networkCallback)
                return
            }
            networkCallbacks += networkCallback
        } catch (securityException: SecurityException) {
            if (remainingAttempts > 1) {
                UnleashLogger.w(
                    TAG,
                    "registerNetworkCallback failed on attempt $attemptNumber/$MAX_REGISTRATION_ATTEMPTS; retrying in $REGISTRATION_RETRY_DELAY_MS ms",
                    securityException
                )
                if (epoch == registrationEpoch.get()) {
                    scheduleRetry(REGISTRATION_RETRY_DELAY_MS) {
                        registerNetworkListener(listener, remainingAttempts - 1, epoch)
                    }
                }
            } else {
                UnleashLogger.w(
                    TAG,
                    "registerNetworkCallback failed after $attemptNumber attempts; network updates disabled",
                    securityException
                )
            }
        }
    }

    fun close() {
        val epoch = registrationEpoch.incrementAndGet()
        val connectivityManager = getConnectivityManager() ?: run {
            networkCallbacks.clear()
            availableNetworks.clear()
            return
        }
        val callbacks = networkCallbacks.toList()
        networkCallbacks.clear()
        availableNetworks.clear()
        callbacks.forEach { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (illegalArgumentException: IllegalArgumentException) {
                UnleashLogger.w(
                    TAG,
                    "NetworkCallback already unregistered during close (epoch=$epoch)",
                    illegalArgumentException
                )
            } catch (securityException: SecurityException) {
                UnleashLogger.w(
                    TAG,
                    "SecurityException while unregistering NetworkCallback during close (epoch=$epoch)",
                    securityException
                )
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getConnectivityManager() ?: return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun getConnectivityManager(): ConnectivityManager? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        if (connectivityManager !is ConnectivityManager) {
            UnleashLogger.w(TAG, "Failed to get ConnectivityManager assuming network is available")
            return null
        }
        return connectivityManager
    }

    private fun isAirplaneModeOn(): Boolean {
        return android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    fun isAvailable(): Boolean {
        return !isAirplaneModeOn() && isNetworkAvailable()
    }

    private fun buildNetworkRequest(): NetworkRequest {
        val requestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return requestBuilder.build()
    }

    private fun buildCallback(listener: NetworkListener) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                availableNetworks += network
                listener.onAvailable()
            }

            override fun onLost(network: Network) {
                availableNetworks -= network
                if (availableNetworks.isEmpty()) {
                    listener.onLost()
                }
            }
        }
}
