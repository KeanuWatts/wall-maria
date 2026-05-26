package com.aicallscreen.llm

/**
 * Structured output from the decision engine (on-device or cloud).
 */
data class LLMDecision(
    val isSpam: Boolean,
    val thoughts: String,
)
