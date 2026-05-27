package com.aicallscreen.llm

/**
 * One utterance in the screening / assistant dialog (caller or AI).
 */
data class ConversationTurn(
    val role: Role,
    val text: String,
) {
    enum class Role {
        AI,
        CALLER,
    }
}
