package io.multinet.mobility.data

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.multinet.mobility.domain.CellularWarmupState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CellularWarmupRepository @Inject constructor(
    private val connectivityManager: ConnectivityManager,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CellularWarmupState.IDLE)

    val state: StateFlow<CellularWarmupState> = _state.asStateFlow()

    private var activeNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    suspend fun ensureWarmupRequested() = mutex.withLock {
        when (_state.value) {
            CellularWarmupState.REQUESTING,
            CellularWarmupState.AVAILABLE,
            CellularWarmupState.HOLDING,
            -> Unit

            CellularWarmupState.IDLE,
            CellularWarmupState.UNAVAILABLE,
            -> requestWarmupLocked()
        }
    }

    suspend fun holdWarmup() = mutex.withLock {
        when (_state.value) {
            CellularWarmupState.AVAILABLE -> _state.value = CellularWarmupState.HOLDING
            CellularWarmupState.IDLE,
            CellularWarmupState.UNAVAILABLE,
            -> requestWarmupLocked()

            CellularWarmupState.REQUESTING,
            CellularWarmupState.HOLDING,
            -> Unit
        }
    }

    suspend fun release() = mutex.withLock {
        val callback = networkCallback
        networkCallback = null
        activeNetwork = null
        if (callback != null) {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
        _state.value = CellularWarmupState.IDLE
    }

    private fun requestWarmupLocked() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (networkCallback !== this) return
                activeNetwork = network
                if (_state.value != CellularWarmupState.HOLDING) {
                    _state.value = CellularWarmupState.AVAILABLE
                }
            }

            override fun onLost(network: Network) {
                if (networkCallback !== this) return
                if (activeNetwork == network) {
                    activeNetwork = null
                }
                _state.value = CellularWarmupState.UNAVAILABLE
            }

            override fun onUnavailable() {
                if (networkCallback !== this) return
                activeNetwork = null
                _state.value = CellularWarmupState.UNAVAILABLE
            }
        }

        networkCallback = callback
        activeNetwork = null
        _state.value = CellularWarmupState.REQUESTING
        connectivityManager.requestNetwork(request, callback, REQUEST_TIMEOUT_MILLIS)
    }

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 10_000
    }
}
