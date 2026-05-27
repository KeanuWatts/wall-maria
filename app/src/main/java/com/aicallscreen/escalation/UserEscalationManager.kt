package com.aicallscreen.escalation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aicallscreen.R
import com.aicallscreen.ui.EscalationTrampolineActivity

/**
 * Rings the device owner locally (notification + system ringtone) when an unknown caller
 * passes AI screening. Does not steal music focus — uses [AudioAttributes.USAGE_NOTIFICATION_RINGTONE].
 */
class UserEscalationManager(
    private val context: Context,
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var activeRingtone: Ringtone? = null

    fun startEscalationRinging(
        phoneNumber: String,
        callerLabel: String,
    ) {
        createChannelIfNeeded()
        stopEscalationRinging()

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            ESCALATION_REQUEST_CODE,
            EscalationTrampolineActivity.createIntent(context, phoneNumber, callerLabel),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val acceptIntent = PendingIntent.getActivity(
            context,
            ESCALATION_REQUEST_CODE + 1,
            EscalationTrampolineActivity.createIntent(context, phoneNumber, callerLabel),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.escalation_notification_title))
            .setContentText(context.getString(R.string.escalation_notification_body, callerLabel))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(acceptIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.escalation_action_answer),
                acceptIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        notificationManager.notify(ESCALATION_NOTIFICATION_ID, notification)
        playLocalRingtone()
        vibrate()
        Log.i(TAG, "Escalation ringing started for $callerLabel ($phoneNumber)")
    }

    fun stopEscalationRinging() {
        activeRingtone?.stop()
        activeRingtone = null
        notificationManager.cancel(ESCALATION_NOTIFICATION_ID)
    }

    fun ensureNotificationChannel() {
        createChannelIfNeeded()
    }

    private fun playLocalRingtone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
        ringtone.play()
        activeRingtone = ringtone
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.escalation_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.escalation_channel_description)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "UserEscalationManager"
        const val CHANNEL_ID = "screened_call_escalation"
        const val ESCALATION_NOTIFICATION_ID = 42_001
        const val ESCALATION_REQUEST_CODE = 42_002
    }
}
