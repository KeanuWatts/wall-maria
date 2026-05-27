package com.aicallscreen.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.escalation.UserEscalationManager

class ScreeningForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLocator.userEscalationManager.ensureNotificationChannel()

        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE).orEmpty()
        val contentPending: PendingIntent? = intent?.getParcelableExtra(EXTRA_CONTENT_INTENT)

        val notification = NotificationCompat.Builder(this, UserEscalationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                contentPending?.let { setContentIntent(it) }
            }
            .build()

        startForeground(NOTIFICATION_ID + sessionId.hashCode(), notification)
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_CONTENT_INTENT = "extra_content_intent"
        private const val NOTIFICATION_ID = 43_000

        fun start(
            context: Context,
            sessionId: String,
            title: String,
            subtitle: String,
            contentIntent: Intent,
        ) {
            val pending = PendingIntent.getActivity(
                context,
                sessionId.hashCode(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val serviceIntent = Intent(context, ScreeningForegroundService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_CONTENT_INTENT, pending)
            }
            context.startForegroundService(serviceIntent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreeningForegroundService::class.java))
        }
    }
}
