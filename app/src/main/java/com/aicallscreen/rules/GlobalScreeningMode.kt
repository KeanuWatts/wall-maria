package com.aicallscreen.rules

/**
 * How broadly AI call handling is applied.
 */
enum class GlobalScreeningMode {
    /**
     * Production behavior: unknown → AI screening; known → normal ring + optional assistant.
     */
    DEFAULT,

    /**
     * Testing: only numbers on the allowlist are handled by AI; everyone else uses the normal phone app.
     */
    ALLOWLIST_ONLY,

    /**
     * AI fully off — all calls pass through with no screening, assistant, or custom UI.
     */
    AI_DISABLED,
}
