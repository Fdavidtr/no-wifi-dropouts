package io.multinet.mobility.data

import android.annotation.SuppressLint
import android.net.ConnectivityDiagnosticsManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.TransportType
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConnectivityRepository @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val connectivityDiagnosticsManager: ConnectivityDiagnosticsManager?,
    private val callbackExecutor: Executor,
) {
    private val _snapshot = MutableStateFlow(ConnectivitySnapshot())
    val snapshot: StateFlow<ConnectivitySnapshot> = _snapshot.asStateFlow()

    private var registered = false
    private var lastDataStallAtEpochMillis: Long? = null
    private var currentDefaultNetwork: Network? = null

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshFromNetwork(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshFromNetwork(network, networkCapabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            refreshFromNetwork(network)
        }

        override fun onLost(network: Network) {
            if (_snapshot.value.currentNetworkId == network.toString()) {
                currentDefaultNetwork = null
                _snapshot.value = ConnectivitySnapshot(
                    currentNetworkId = null,
                    defaultTransport = TransportType.NONE,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private val diagnosticsCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        object : ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback() {
            override fun onConnectivityReportAvailable(report: ConnectivityDiagnosticsManager.ConnectivityReport) {
                refreshFromNetwork(report.network)
            }

            override fun onDataStallSuspected(report: ConnectivityDiagnosticsManager.DataStallReport) {
                lastDataStallAtEpochMillis = System.currentTimeMillis()
                refreshFromNetwork(report.network)
            }
        }
    } else {
        null
    }

    fun startMonitoring() {
        if (registered) return
        registered = true
        connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && diagnosticsCallback != null) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityDiagnosticsManager?.registerConnectivityDiagnosticsCallback(
                request,
                callbackExecutor,
                diagnosticsCallback,
            )
        }
        refreshFromNetwork(connectivityManager.activeNetwork)
    }

    fun stopMonitoring() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && diagnosticsCallback != null) {
            connectivityDiagnosticsManager?.unregisterConnectivityDiagnosticsCallback(diagnosticsCallback)
        }
        registered = false
        currentDefaultNetwork = null
    }

    @SuppressLint("MissingPermission")
    fun refreshFromNetwork(network: Network?, cachedCapabilities: NetworkCapabilities? = null) {
        if (network == null) {
            currentDefaultNetwork = null
            _snapshot.value = ConnectivitySnapshot(
                updatedAtEpochMillis = System.currentTimeMillis(),
                dataStallSuspected = lastDataStallAtEpochMillis?.let { System.currentTimeMillis() - it <= 15_000 } == true,
                lastDataStallAtEpochMillis = lastDataStallAtEpochMillis,
            )
            return
        }

        val capabilities = cachedCapabilities ?: connectivityManager.getNetworkCapabilities(network) ?: return
        currentDefaultNetwork = network
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TransportType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
            else -> TransportType.OTHER
        }

        val wifiInfo = capabilities.transportInfo as? WifiInfo
        val frequency = wifiInfo?.frequency
        val ssid = wifiInfo?.ssid
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
            ?.removeSurrounding("\"")

        _snapshot.value = ConnectivitySnapshot(
            currentNetworkId = network.toString(),
            defaultTransport = transport,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            internetAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            wifiSsid = ssid,
            rssi = wifiInfo?.rssi,
            frequencyMhz = frequency,
            dataStallSuspected = lastDataStallAtEpochMillis?.let { System.currentTimeMillis() - it <= 15_000 } == true,
            lastDataStallAtEpochMillis = lastDataStallAtEpochMillis,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun defaultNetwork(): Network? = currentDefaultNetwork

    fun reportBadConnectivityOnDefaultNetwork(): Result<Boolean> = runCatching {
        val network = currentDefaultNetwork ?: return@runCatching false
        connectivityManager.reportNetworkConnectivity(network, false)
        true
    }
}
