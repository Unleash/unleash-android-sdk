package io.getunleash.android.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.getunleash.android.BaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
class NetworkStatusHelperTest : BaseTest() {

    @Test
    fun `when connectivity service is not available assumes network is available`() {
        val networkStatusHelper = NetworkStatusHelper(mock(Context::class.java))
        assertThat(networkStatusHelper.isAvailable()).isTrue()
    }

    @Test
    fun `when api version is 21 check active network info`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val activeNetwork = mock(android.net.NetworkInfo::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(connectivityManager.activeNetworkInfo).thenReturn(activeNetwork)
        `when`(activeNetwork.isConnected).thenReturn(true)
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isTrue()
        verify(activeNetwork).isConnected
    }

    @Test
    @Config(sdk = [23])
    fun `when api version is 23 check active network info`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val activeNetwork = mock(Network::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(connectivityManager.activeNetwork).thenReturn(activeNetwork)
        val networkCapabilities = mock(NetworkCapabilities::class.java)
        `when`(connectivityManager.getNetworkCapabilities(activeNetwork)).thenReturn(networkCapabilities)
        `when`(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isTrue()
        verify(networkCapabilities, times(2)).hasCapability(anyInt())
    }

    @Test
    @Config(sdk = [23])
    fun `when no active network the network is not available`() {
        val context = contextWithNetwork(null)
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isFalse()
    }

    @Test
    @Config(sdk = [23])
    fun `when active network has no capability the network is not available`() {
        val context = contextWithNetwork(mock(Network::class.java))
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isFalse()
    }

    @Test
    @Config(sdk = [23])
    fun `when no internet capability then the network is not available`() {
        val context = contextWithNetwork(
            mock(Network::class.java),
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isFalse()
    }

    @Test
    @Config(sdk = [23])
    fun `when network not validated then the network is not available`() {
        val context = contextWithNetwork(
            mock(Network::class.java),
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isFalse()
    }

    @Test
    @Config(sdk = [23])
    fun `when network is validated and has internet then the network is available`() {
        val context = contextWithNetwork(
            mock(Network::class.java),
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isTrue()
    }

    @Test
    @Config(sdk = [23])
    fun `can register a network listener with API level above 23`() {
        val network = mock(Network::class.java)
        val network2 = mock(Network::class.java)
        val context = contextWithNetwork(
            network,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkStatusHelper = NetworkStatusHelper(context)
        val listener = mock(NetworkListener::class.java)
        networkStatusHelper.registerNetworkListener(listener)
        verify(connectivityManager).registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())

        networkStatusHelper.networkCallbacks[0].onAvailable(network)
        verify(listener).onAvailable()
        networkStatusHelper.networkCallbacks[0].onAvailable(network2)
        verify(listener, times(2)).onAvailable()
        networkStatusHelper.networkCallbacks[0].onLost(network)
        verify(listener, never()).onLost()
        networkStatusHelper.networkCallbacks[0].onLost(network2)
        verify(listener).onLost()
    }

    @Test
    fun `retries registering callback when SecurityException occurs`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        doThrow(SecurityException("binder race"))
            .doNothing()
            .`when`(connectivityManager)
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())

        val scheduledActions = mutableListOf<() -> Unit>()
        val listener = mock(NetworkListener::class.java)

        val networkStatusHelper = NetworkStatusHelper(
            context,
            scheduleRetry = { _, action -> scheduledActions.add(action) }
        )

        networkStatusHelper.registerNetworkListener(listener)

        assertThat(networkStatusHelper.networkCallbacks).isEmpty()
        assertThat(scheduledActions).hasSize(1)

        scheduledActions.removeAt(0).invoke()

        assertThat(networkStatusHelper.networkCallbacks).hasSize(1)
        verify(connectivityManager, times(2))
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `gives up registering callback after max retries`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        doThrow(SecurityException("binder race"))
            .`when`(connectivityManager)
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())

        val scheduledActions = mutableListOf<() -> Unit>()
        val networkStatusHelper = NetworkStatusHelper(
            context,
            scheduleRetry = { _, action -> scheduledActions.add(action) }
        )

        networkStatusHelper.registerNetworkListener(mock(NetworkListener::class.java))

        while (scheduledActions.isNotEmpty()) {
            scheduledActions.removeAt(0).invoke()
        }

        assertThat(networkStatusHelper.networkCallbacks).isEmpty()
        verify(connectivityManager, times(NetworkStatusHelper.MAX_REGISTRATION_ATTEMPTS))
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `close swallows IllegalArgumentException when unregistering callback`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val callback = mock(ConnectivityManager.NetworkCallback::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        doThrow(IllegalArgumentException("already unregistered"))
            .`when`(connectivityManager)
            .unregisterNetworkCallback(callback)

        val networkStatusHelper = NetworkStatusHelper(context)
        networkStatusHelper.networkCallbacks += callback

        networkStatusHelper.close()

        verify(connectivityManager).unregisterNetworkCallback(callback)
        assertThat(networkStatusHelper.networkCallbacks).isEmpty()
    }

    @Test
    fun `close does not trigger retry attempts scheduled before closing`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        doThrow(SecurityException("binder race"))
            .`when`(connectivityManager)
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())

        val scheduledActions = mutableListOf<() -> Unit>()
        val networkStatusHelper = NetworkStatusHelper(
            context,
            scheduleRetry = { _, action -> scheduledActions.add(action) }
        )

        networkStatusHelper.registerNetworkListener(mock(NetworkListener::class.java))
        assertThat(scheduledActions).hasSize(1)

        networkStatusHelper.close()

        scheduledActions.removeAt(0).invoke()

        verify(connectivityManager, times(1))
            .registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `close can be called multiple times without unregistering twice`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val callback = mock(ConnectivityManager.NetworkCallback::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)

        val networkStatusHelper = NetworkStatusHelper(context)
        networkStatusHelper.networkCallbacks += callback

        networkStatusHelper.close()
        networkStatusHelper.close()

        verify(connectivityManager, times(1)).unregisterNetworkCallback(callback)
        assertThat(networkStatusHelper.networkCallbacks).isEmpty()
    }

    private fun contextWithNetwork(network: Network?, vararg capabilities: Int): Context {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        if (network != null) {
            `when`(connectivityManager.activeNetwork).thenReturn(network)
            val mockedCapabilities = mock<NetworkCapabilities>()
            capabilities.forEach {
                `when`(mockedCapabilities.hasCapability(it)).thenReturn(true)
            }
            `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(mockedCapabilities)
        }
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(
            connectivityManager
        )
        return context
    }


}
