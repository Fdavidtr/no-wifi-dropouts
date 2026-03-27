package io.multinet.mobility.data

import android.os.Build
import io.multinet.mobility.domain.DeviceCapabilities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityProbe @Inject constructor() {
    fun probe(): DeviceCapabilities {
        return DeviceCapabilities(
            apiLevel = Build.VERSION.SDK_INT,
            hasConnectivityDiagnostics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            canRequestCellularWarmup = true,
            canReportBadWifi = true,
        )
    }
}
