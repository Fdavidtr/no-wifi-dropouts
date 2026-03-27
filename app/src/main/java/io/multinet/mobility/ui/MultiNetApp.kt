package io.multinet.mobility.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.layout.padding
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.multinet.mobility.domain.ManagedWifiProfile
import io.multinet.mobility.service.MobilityModeService
import io.multinet.mobility.ui.components.ProfileEditorDialog
import io.multinet.mobility.ui.navigation.MainTab
import io.multinet.mobility.ui.screens.DashboardScreen
import io.multinet.mobility.ui.screens.EventLogScreen
import io.multinet.mobility.ui.screens.OnboardingScreen
import io.multinet.mobility.ui.screens.WifiProfilesScreen
import kotlinx.coroutines.launch

@Composable
fun MultiNetApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val inspectionMode = LocalInspectionMode.current
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    var editingProfile by remember { mutableStateOf<ManagedWifiProfile?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val runtimePermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val allRuntimePermissionsGranted = runtimePermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val approvalLabel = when (uiState.runtime.approvalStatus) {
        android.net.wifi.WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER -> "Approved"
        android.net.wifi.WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE -> "Carrier approved"
        android.net.wifi.WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING -> "Pending"
        android.net.wifi.WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER -> "Rejected"
        else -> "Unknown"
    }

    if (showProfileDialog) {
        ProfileEditorDialog(
            existingProfile = editingProfile,
            onDismiss = {
                editingProfile = null
                showProfileDialog = false
            },
            onSave = { ssid, passphrase, securityType, priority, preferredBand, minSignalDbm, allowCellFallback, enabled ->
                viewModel.saveProfile(
                    existingProfile = editingProfile,
                    ssid = ssid,
                    passphrase = passphrase,
                    securityType = securityType,
                    priority = priority,
                    preferredBand = preferredBand,
                    minSignalDbm = minSignalDbm,
                    allowCellFallback = allowCellFallback,
                    enabled = enabled,
                )
                editingProfile = null
                showProfileDialog = false
            },
        )
    }

    if (!uiState.settings.onboardingCompleted) {
        OnboardingScreen(
            hasRuntimePermissions = allRuntimePermissionsGranted || inspectionMode,
            profileCount = uiState.settings.profiles.size,
            approvalLabel = approvalLabel,
            onRequestPermissions = {
                if (!inspectionMode) {
                    permissionLauncher.launch(runtimePermissions)
                }
            },
            onOpenWifiSettings = {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onAddProfile = {
                editingProfile = null
                showProfileDialog = true
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.DASHBOARD,
                    onClick = { selectedTab = MainTab.DASHBOARD },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(MainTab.DASHBOARD.title) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILES,
                    onClick = { selectedTab = MainTab.PROFILES },
                    icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    label = { Text(MainTab.PROFILES.title) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.EVENTS,
                    onClick = { selectedTab = MainTab.EVENTS },
                    icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
                    label = { Text(MainTab.EVENTS.title) },
                )
            }
        },
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                MainTab.DASHBOARD -> DashboardScreen(
                    runtimeState = uiState.runtime,
                    mobilityModeEnabled = uiState.settings.mobilityModeEnabled,
                    approvalLabel = approvalLabel,
                    onToggleMobilityMode = { enabled ->
                        scope.launch {
                            if (enabled) {
                                MobilityModeService.start(context)
                            } else {
                                MobilityModeService.stop(context)
                            }
                        }
                    },
                )

                MainTab.PROFILES -> WifiProfilesScreen(
                    profiles = uiState.settings.profiles,
                    onAddProfile = {
                        editingProfile = null
                        showProfileDialog = true
                    },
                    onEditProfile = { profile ->
                        editingProfile = profile
                        showProfileDialog = true
                    },
                    onToggleProfile = viewModel::toggleProfile,
                    onDeleteProfile = viewModel::removeProfile,
                )

                MainTab.EVENTS -> EventLogScreen(events = uiState.events)
            }
        }
    }
}
