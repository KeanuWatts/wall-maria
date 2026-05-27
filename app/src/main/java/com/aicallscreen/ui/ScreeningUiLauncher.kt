package com.aicallscreen.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aicallscreen.service.ScreeningForegroundService

object ScreeningUiLauncher {

    fun showIncomingCallScreen(
        context: Context,
        sessionId: String,
        phoneNumber: String,
        displayName: String,
        ownerDisplayName: String,
    ) {
        val intent = IncomingCallActivity.createIntent(
            context = context,
            sessionId = sessionId,
            phoneNumber = phoneNumber,
            displayName = displayName,
            ownerDisplayName = ownerDisplayName,
        )
        ScreeningForegroundService.start(
            context = context,
            sessionId = sessionId,
            title = displayName,
            subtitle = context.getString(com.aicallscreen.R.string.incoming_call_notification_subtitle),
            contentIntent = intent,
        )
        context.startActivity(intent)
    }

    fun showLiveScreening(
        context: Context,
        sessionId: String,
        displayLabel: String,
    ) {
        val intent = LiveScreeningActivity.createIntent(context, sessionId)
        ScreeningForegroundService.start(
            context = context,
            sessionId = sessionId,
            title = displayLabel,
            subtitle = context.getString(com.aicallscreen.R.string.live_screening_notification_subtitle),
            contentIntent = intent,
        )
        context.startActivity(intent)
    }

    fun pendingLiveScreenIntent(context: Context, sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            LiveScreeningActivity.createIntent(context, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
