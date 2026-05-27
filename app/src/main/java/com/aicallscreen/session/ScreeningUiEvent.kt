package com.aicallscreen.session

import com.aicallscreen.llm.ScreeningAction

sealed class ScreeningUiEvent {
    data class Status(val message: String) : ScreeningUiEvent()

    data class AiSpeaking(val text: String) : ScreeningUiEvent()

    data class CallerPartial(val text: String) : ScreeningUiEvent()

    data class CallerHeard(val text: String) : ScreeningUiEvent()

    data class LlmThinking(val preview: String = "Evaluating…") : ScreeningUiEvent()

    data class LlmDecision(
        val action: ScreeningAction,
        val thoughts: String,
    ) : ScreeningUiEvent()

    data class SessionEnded(
        val reason: EndReason,
        val summary: String,
    ) : ScreeningUiEvent()

    enum class EndReason {
        COMPLETED,
        BLOCKED,
        CONNECTED_TO_USER,
        MESSAGE_TAKEN,
        USER_TAKEOVER,
        DECLINED,
        ERROR,
    }
}
