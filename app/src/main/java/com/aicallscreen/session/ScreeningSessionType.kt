package com.aicallscreen.session

enum class ScreeningSessionType {
    /** Unknown caller — silent AI screening. */
    UNKNOWN_SCREENING,

    /** Known contact — user chose “Send to assistant” or no-answer timeout. */
    KNOWN_ASSISTANT,

    /** Known contact — incoming call UI (ringing). */
    INCOMING_KNOWN_RINGING,
}
