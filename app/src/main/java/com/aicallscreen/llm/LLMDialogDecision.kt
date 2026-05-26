package com.aicallscreen.llm

/**
 * Structured multi-turn LLM output: what to say next and what to do with the call.
 */
data class LLMDialogDecision(
    val action: ScreeningAction,
    val replyToSpeak: String,
    val thoughts: String,
) {
    val isSpam: Boolean get() = action == ScreeningAction.BLOCK_CALL
}
