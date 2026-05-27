package com.aicallscreen.llm

import kotlinx.coroutines.Deferred

/**
 * Evaluates caller transcripts and returns structured screening decisions.
 */
interface ILocalLLMEngine {

    /** Single-shot classification (legacy / logging). */
    fun evaluateTranscript(transcript: String): Deferred<LLMDecision>

    /**
     * Multi-turn dialog step — uses [history] plus latest [callerTranscript].
     */
    fun evaluateDialogTurn(
        callerTranscript: String,
        history: List<ConversationTurn>,
        mode: DialogMode,
        contactDisplayName: String? = null,
        ownerDisplayName: String? = null,
    ): Deferred<LLMDialogDecision>
}
