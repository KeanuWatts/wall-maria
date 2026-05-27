package com.aicallscreen.routing

/**
 * High-level handling strategy chosen before audio/AI work begins.
 */
enum class CallRoute {
    /** Unknown number — silent AI screening, multi-turn dialog, optional user ring. */
    UNKNOWN_AI_SCREENING,

    /** Saved contact — normal ring; AI assistant only if user does not answer in time. */
    KNOWN_CONTACT_NORMAL_RING,

    /**
     * No AI involvement — behave like a normal incoming call (testing / rules bypass).
     */
    PASS_THROUGH,
}
