package com.infinity.core.advanced.data.capabilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.infinity.core.advanced.domain.capabilities.InfinityConnectionState
import com.infinity.core.advanced.domain.capabilities.InfinityConnectivityCapability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Read-only Android network monitor. It never changes connectivity settings. */
internal class InfinityAndroidConnectivityCapabilityAdapter(
    context: Context
) : InfinityConnectivityCapability {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val connectionState: Flow<InfinityConnectionState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(INFINITY_OFFLINE_CONNECTION_STATE)
            close()
            return@callbackFlow
        }

        fun publishCurrentState() {
            trySend(readActiveConnectionState(manager))
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishCurrentState()
            override fun onLost(network: Network) = publishCurrentState()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = publishCurrentState()
        }

        try {
            manager.registerDefaultNetworkCallback(callback)
            publishCurrentState()
        } catch (error: SecurityException) {
            trySend(INFINITY_OFFLINE_CONNECTION_STATE)
            close(error)
            return@callbackFlow
        } catch (error: RuntimeException) {
            trySend(INFINITY_OFFLINE_CONNECTION_STATE)
            close(error)
            return@callbackFlow
        }

        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun readActiveConnectionState(
        manager: ConnectivityManager
    ): InfinityConnectionState {
        val network = runCatching { manager.activeNetwork }.getOrNull()
            ?: return INFINITY_OFFLINE_CONNECTION_STATE
        val capabilities = runCatching {
            manager.getNetworkCapabilities(network)
        }.getOrNull() ?: return INFINITY_OFFLINE_CONNECTION_STATE

        val hasInternet = capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        val isNotMetered = capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
        )
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }

        return infinityMapConnectionState(hasInternet, isNotMetered, transport)
    }
}

internal val INFINITY_OFFLINE_CONNECTION_STATE = InfinityConnectionState(
    hasNetwork = false,
    isMetered = false,
    transport = null
)

internal fun infinityMapConnectionState(
    hasInternet: Boolean,
    isNotMetered: Boolean,
    transport: String?
): InfinityConnectionState = InfinityConnectionState(
    hasNetwork = hasInternet,
    isMetered = hasInternet && !isNotMetered,
    transport = transport?.takeIf { it in INFINITY_SUPPORTED_NETWORK_TRANSPORTS }
)

private val INFINITY_SUPPORTED_NETWORK_TRANSPORTS = setOf(
    "wifi", "cellular", "ethernet", "vpn", "bluetooth", "other"
)
