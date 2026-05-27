package com.aicallscreen.llm

import org.json.JSONObject

object DialogDecisionParser {

    fun fromJson(json: String, fallbackTranscript: String): LLMDialogDecision? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            val actionRaw = root.optString("action", root.optString("screeningAction", ""))
            val action = parseAction(actionRaw)
            val reply = root.optString("replyToSpeak", root.optString("reply", ""))
            val thoughts = root.optString("thoughts", json)
            LLMDialogDecision(
                action = action,
                replyToSpeak = reply.ifBlank { defaultReply(action, fallbackTranscript) },
                thoughts = thoughts,
            )
        } catch (ignored: Exception) {
            null
        }
    }

    private fun parseAction(raw: String): ScreeningAction = when (raw.uppercase()) {
        "CONNECT_TO_USER", "CONNECT" -> ScreeningAction.CONNECT_TO_USER
        "BLOCK_CALL", "BLOCK", "SPAM" -> ScreeningAction.BLOCK_CALL
        "TAKE_MESSAGE_AND_END", "TAKE_MESSAGE", "MESSAGE" -> ScreeningAction.TAKE_MESSAGE_AND_END
        else -> ScreeningAction.CONTINUE_DIALOG
    }

    private fun defaultReply(action: ScreeningAction, transcript: String): String = when (action) {
        ScreeningAction.CONNECT_TO_USER ->
            "Thank you. One moment while I connect you."
        ScreeningAction.BLOCK_CALL ->
            "This call cannot be completed."
        ScreeningAction.TAKE_MESSAGE_AND_END ->
            "Thank you, I will pass that message along. Goodbye."
        ScreeningAction.CONTINUE_DIALOG ->
            if (transcript.isBlank()) {
                "I didn't catch that. Could you briefly say who you are and why you're calling?"
            } else {
                "Could you tell me a bit more about the purpose of your call?"
            }
    }
}
