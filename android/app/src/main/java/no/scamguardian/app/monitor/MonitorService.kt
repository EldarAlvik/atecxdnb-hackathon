package no.scamguardian.app.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import no.scamguardian.app.board.verdictLabel

/**
 * Foreground service (type `connectedDevice`) that keeps [BoardMonitor] running. A phone call
 * happens in the dialer, not in this app, so the live connection must survive the app going to the
 * background, or the scam alert would arrive after the call. The service runs while a board is
 * paired and the app is open, and keeps running in the background if "Keep watching in the
 * background" is on (the default). WorkManager was not used: its 15-minute minimum period is far
 * too slow for alerts during a call and for a 30 s offline warning.
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {
    @Inject lateinit var monitor: BoardMonitor
    @Inject lateinit var notifier: Notifier

    private var started = false

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, Notifier.ID_MONITOR, notifier.monitorNotification("Connecting to the board…"), type)
        } catch (e: RuntimeException) {
            // Android 12+ refuses a foreground start from the background (e.g. a sticky restart).
            // The activity starts the service again when the app is opened.
            stopSelf()
            return
        }
        monitor.start()
        started = true
        lifecycleScope.launch {
            combine(monitor.connection, monitor.live) { c, l -> text(c, l) }
                .distinctUntilChanged()
                .collect { notifier.updateMonitor(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        if (started) monitor.stop()
        started = false
        super.onDestroy()
    }

    private fun text(c: Connection, l: LiveState): String = when (c) {
        Connection.LIVE, Connection.RECONNECTING -> {
            val conn = if (c == Connection.LIVE) "Live" else "Reconnecting"
            if (l.guarding) "$conn · guarding · ${verdictLabel(l.verdict)}" else "$conn · not guarding"
        }
        Connection.OFFLINE -> "Board offline"
        Connection.UNAUTHORIZED -> "The board no longer accepts this phone. Run setup again."
        Connection.IDLE -> "Waiting for the board…"
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
