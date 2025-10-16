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

    fun registerNetworkListener(listener: NetworkListener) {
        registerNetworkListener(listener, MAX_REGISTRATION_ATTEMPTS)
    }

    private fun registerNetworkListener(
        listener: NetworkListener,
        remainingAttempts: Int
    ) {
        val attemptNumber = MAX_REGISTRATION_ATTEMPTS - remainingAttempts + 1
        try {
            val connectivityManager = getConnectivityManager() ?: return
            val networkRequest = buildNetworkRequest()

            val networkCallback = buildCallback(listener)
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            networkCallbacks += networkCallback
        } catch (securityException: SecurityException) {
            if (remainingAttempts > 1) {
                UnleashLogger.i(
                    TAG,
                    "registerNetworkCallback failed on attempt $attemptNumber/$MAX_REGISTRATION_ATTEMPTS; retrying in $REGISTRATION_RETRY_DELAY_MS ms",
                    securityException
                )
                scheduleRetry(REGISTRATION_RETRY_DELAY_MS) {
                    registerNetworkListener(listener, remainingAttempts - 1)
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
        val connectivityManager = getConnectivityManager() ?: return
        networkCallbacks.forEach {
            connectivityManager.unregisterNetworkCallback(it)
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
