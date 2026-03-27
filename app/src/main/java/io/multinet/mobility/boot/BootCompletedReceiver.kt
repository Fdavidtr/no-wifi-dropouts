package io.multinet.mobility.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.multinet.mobility.data.preferences.UserPreferencesRepository
import io.multinet.mobility.service.MobilityModeService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val settings = preferencesRepository.currentSettings()
                if (settings.mobilityModeEnabled) {
                    MobilityModeService.start(context)
                }
            }
            pendingResult.finish()
        }
    }
}
