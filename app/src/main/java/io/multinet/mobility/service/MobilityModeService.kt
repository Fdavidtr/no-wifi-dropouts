package io.multinet.mobility.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.multinet.mobility.R
import io.multinet.mobility.data.MobilityController
import io.multinet.mobility.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MobilityModeService : android.app.Service() {
    @Inject lateinit var mobilityController: MobilityController
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopMode()
            else -> startMode()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startMode() {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_text_idle)))

        serviceScope.launch {
            preferencesRepository.setMobilityModeEnabled(true)
            mobilityController.startMonitoring()
        }

        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            mobilityController.runtimeState.collect { runtime ->
                val networkLabel = runtime.snapshot.wifiSsid
                    ?: runtime.snapshot.defaultTransport.name.lowercase().replaceFirstChar(Char::uppercase)
                val text = if (runtime.isMonitoring) {
                    "On $networkLabel, ${mobilityController.approvalStatusLabel(runtime.approvalStatus)} suggestions"
                } else {
                    getString(R.string.notification_text_idle)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun stopMode() {
        serviceScope.launch {
            preferencesRepository.setMobilityModeEnabled(false)
            mobilityController.stopMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "mobility_mode"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "io.multinet.mobility.action.START"
        private const val ACTION_STOP = "io.multinet.mobility.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MobilityModeService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MobilityModeService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

