package io.getunleash.android.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

interface NetworkListener {
    fun onAvailable()
    fun onLost()
}

class NetworkStatusHelper(private val context: Context) {
    companion object {
        private const val TAG = "NetworkState"
    }

    internal val networkCallbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    private val availableNetworks = mutableSetOf<Network>()

    fun registerNetworkListener(listener: NetworkListener) {
        val connectivityManager = getConnectivityManager() ?: return
        val requestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val networkRequest = requestBuilder.build()

        // wrap the listener in a NetworkCallback so the listener doesn't have to know about Android specifics
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
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

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        networkCallbacks += networkCallback
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
            Log.w(TAG, "Failed to get ConnectivityManager assuming network is available")
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
}
