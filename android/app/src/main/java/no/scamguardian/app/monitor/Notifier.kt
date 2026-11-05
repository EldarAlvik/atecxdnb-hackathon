package no.scamguardian.app.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import no.scamguardian.app.MainActivity
import no.scamguardian.app.R

/** The app's notifications: scam and word-match alerts, board offline, and the monitoring service. */
class Notifier(private val context: Context) : AlertSink {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ALERTS, "Call alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Scam verdicts and family words heard during a call"
                },
                NotificationChannel(CH_BOARD, "Board status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The board stopped answering"
                },
                NotificationChannel(CH_MONITOR, "Watching the board", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the app keeps a live connection to the board"
                },
            ),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(id: Int, n: Notification) {
        if (!canPost()) return
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    override fun alert(alert: Alert) {
        when (alert) {
        is Alert.Scam -> post(
            ID_SCAM,
            NotificationCompat.Builder(context, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_guardian)
                .setColor(0xFFD32F2F.toInt())
                .setContentTitle("Possible scam call")
                .setContentText(alert.reason.ifEmpty { "The board thinks this call is a scam. Do not share codes or move money." })
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    (alert.reason.ifEmpty { "The board thinks this call is a scam." }) +
                        "\nDo not give out BankID codes, passwords or move money. Hang up and call back on a number you know.",
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
        is Alert.WordHeard -> post(
            ID_WORD,
            NotificationCompat.Builder(context, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_guardian)
                .setColor(0xFF2E7D32.toInt())
                .setContentTitle("Family word heard")
                .setContentText("The caller said ${alert.member.ifEmpty { "a family member" }}'s security word.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
        }
    }

    override fun boardOffline(seconds: Long) = post(
        ID_OFFLINE,
        NotificationCompat.Builder(context, CH_BOARD)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle("Guardian board is offline")
            .setContentText("No answer for $seconds s. Check that it has power and Wi-Fi.")
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build(),
    )

    override fun cancelOffline() = nm.cancel(ID_OFFLINE)

    fun monitorNotification(text: String): Notification =
        NotificationCompat.Builder(context, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle("Scam Call Guardian")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    fun updateMonitor(text: String) = post(ID_MONITOR, monitorNotification(text))

    companion object {
        const val CH_ALERTS = "alerts"
        const val CH_BOARD = "board"
        const val CH_MONITOR = "monitor"
        const val ID_MONITOR = 1
        const val ID_SCAM = 2
        const val ID_WORD = 3
        const val ID_OFFLINE = 4
    }
}
