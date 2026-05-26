package com.aicallscreen.llm

import kotlinx.coroutines.Deferred

/**
 * Evaluates caller transcripts and returns a structured spam decision.
 */
interface ILocalLLMEngine {

    fun evaluateTranscript(transcript: String): Deferred<LLMDecision>
}
