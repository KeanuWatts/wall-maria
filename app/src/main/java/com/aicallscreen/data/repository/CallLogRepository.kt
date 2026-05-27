package com.aicallscreen.data.repository

import com.aicallscreen.data.local.CallLogDao
import com.aicallscreen.data.local.CallLogEntity
import kotlinx.coroutines.flow.Flow

class CallLogRepository(
    private val callLogDao: CallLogDao,
) {

    suspend fun persistScreeningEvent(
        phoneNumber: String,
        timestamp: Long,
        ttsGreetingText: String,
        callerTranscript: String,
        aiThoughts: String,
        wasBlocked: Boolean,
        callRoute: String = "",
        conversationHistory: String = "",
        wasConnectedToUser: Boolean = false,
        leftMessage: String = "",
    ) {
        callLogDao.insertLog(
            CallLogEntity(
                phoneNumber = phoneNumber,
                timestamp = timestamp,
                ttsGreetingText = ttsGreetingText,
                callerTranscript = callerTranscript,
                aiThoughts = aiThoughts,
                wasBlocked = wasBlocked,
                callRoute = callRoute,
                conversationHistory = conversationHistory,
                wasConnectedToUser = wasConnectedToUser,
                leftMessage = leftMessage,
            ),
        )
    }

    fun observeAllLogs(): Flow<List<CallLogEntity>> = callLogDao.getAllLogs()
}
