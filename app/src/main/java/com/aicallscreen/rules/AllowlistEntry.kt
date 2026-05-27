package com.aicallscreen.rules

import kotlinx.serialization.Serializable

@Serializable
data class AllowlistEntry(
    val normalizedNumber: String,
    val displayLabel: String,
)
