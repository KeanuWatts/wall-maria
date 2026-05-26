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

@Serializable
data class LlmDialogRequest(
    val callerTranscript: String,
    val history: List<DialogTurnDto> = emptyList(),
    val mode: String,
    val contactDisplayName: String? = null,
    val ownerDisplayName: String? = null,
)

@Serializable
data class DialogTurnDto(
    val role: String,
    val text: String,
)

@Serializable
data class LlmDialogResponse(
    val action: String,
    val replyToSpeak: String,
    val thoughts: String,
)
