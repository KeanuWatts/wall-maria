package com.aicallscreen.llm.api

import kotlinx.serialization.Serializable

@Serializable
data class LlmEvaluateRequest(
    val transcript: String,
)

@Serializable
data class LlmEvaluateResponse(
    val isSpam: Boolean,
    val thoughts: String,
)
