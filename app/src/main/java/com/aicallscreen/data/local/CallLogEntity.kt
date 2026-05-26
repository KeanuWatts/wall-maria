package com.aicallscreen.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent record of each call screening event for in-app historical review.
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val phoneNumber: String,
    val timestamp: Long,
    val ttsGreetingText: String,
    val callerTranscript: String,
    val aiThoughts: String,
    val wasBlocked: Boolean,
    /** [com.aicallscreen.routing.CallRoute] name */
    val callRoute: String = "",
    val conversationHistory: String = "",
    val wasConnectedToUser: Boolean = false,
    val leftMessage: String = "",
)
