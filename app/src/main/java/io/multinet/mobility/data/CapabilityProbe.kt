package io.multinet.mobility.data

import android.os.Build
import android.net.wifi.WifiManager
import io.multinet.mobility.domain.DeviceCapabilities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityProbe @Inject constructor(
    private val wifiManager: WifiManager,
) {
    fun probe(): DeviceCapabilities {
        val supportsMultiInternet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiManager.isStaConcurrencyForMultiInternetSupported
        } else {
            false
        }

        val multiInternetMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (wifiManager.staConcurrencyForMultiInternetMode) {
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP -> "Dual-band same AP"
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP -> "Multiple APs"
                else -> "Disabled"
            }
        } else {
            "Unavailable"
        }

        return DeviceCapabilities(
            apiLevel = Build.VERSION.SDK_INT,
            hasConnectivityDiagnostics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            hasSuggestionApprovalListener = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            supportsMultiInternetWifi = supportsMultiInternet,
            multiInternetModeLabel = multiInternetMode,
        )
    }
}

