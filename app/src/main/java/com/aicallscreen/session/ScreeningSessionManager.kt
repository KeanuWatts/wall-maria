package com.aicallscreen.session

import java.util.concurrent.ConcurrentHashMap

object ScreeningSessionManager {

    private val sessions = ConcurrentHashMap<String, ScreeningSession>()

    @Volatile
    private var activeSessionId: String? = null

    fun createSession(
        phoneNumber: String,
        displayLabel: String,
        sessionType: ScreeningSessionType,
        ownerDisplayName: String = "",
        contactDisplayName: String = "",
    ): ScreeningSession {
        val session = ScreeningSession(
            phoneNumber = phoneNumber,
            displayLabel = displayLabel,
            sessionType = sessionType,
            ownerDisplayName = ownerDisplayName,
            contactDisplayName = contactDisplayName.ifBlank { displayLabel },
        )
        sessions[session.sessionId] = session
        activeSessionId = session.sessionId
        return session
    }

    fun getSession(sessionId: String): ScreeningSession? = sessions[sessionId]

    fun activeSession(): ScreeningSession? =
        activeSessionId?.let { sessions[it] }

    fun requestTakeover(sessionId: String) {
        sessions[sessionId]?.requestTakeover()
    }

    fun endSession(sessionId: String) {
        sessions.remove(sessionId)
        if (activeSessionId == sessionId) {
            activeSessionId = sessions.keys.lastOrNull()
        }
    }
}
