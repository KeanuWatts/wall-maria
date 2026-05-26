package com.aicallscreen.llm

enum class DialogMode {
    /** Multi-turn gatekeeper for unknown callers. */
    UNKNOWN_SCREENING,

    /** Voicemail-style assistant when a known contact is not answered. */
    KNOWN_CONTACT_VOICEMAIL,
}
