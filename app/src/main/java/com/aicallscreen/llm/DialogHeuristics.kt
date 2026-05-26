package com.aicallscreen.llm

object DialogHeuristics {

    fun unknownScreening(
        callerTranscript: String,
        history: List<ConversationTurn>,
        turnIndex: Int,
    ): LLMDialogDecision {
        val lower = callerTranscript.lowercase()
        val spamKeywords = listOf(
            "warranty", "irs", "lottery", "press 1", "robo",
            "you've won", "social security", "gift card",
        )
        if (spamKeywords.any { lower.contains(it) }) {
            return LLMDialogDecision(
                action = ScreeningAction.BLOCK_CALL,
                replyToSpeak = "This number is not accepting solicitations. Goodbye.",
                thoughts = "Heuristic: matched spam keyword in \"$callerTranscript\"",
            )
        }

        val legitSignals = listOf(
            "appointment", "doctor", "it's me", "this is", "calling about",
            "family", "school", "delivery", "uber", "running late",
        )
        val hasLegitSignal = legitSignals.any { lower.contains(it) }
        val callerTurns = history.count { it.role == ConversationTurn.Role.CALLER }

        if (hasLegitSignal && callerTurns >= 1) {
            return LLMDialogDecision(
                action = ScreeningAction.CONNECT_TO_USER,
                replyToSpeak = "Thanks, that helps. One moment while I connect you.",
                thoughts = "Heuristic: legitimate intent detected after $callerTurns caller turn(s)",
            )
        }

        if (turnIndex >= MAX_UNKNOWN_TURNS) {
            return if (callerTranscript.isBlank()) {
                LLMDialogDecision(
                    action = ScreeningAction.BLOCK_CALL,
                    replyToSpeak = "I wasn't able to verify this call. Goodbye.",
                    thoughts = "Heuristic: max turns with no usable transcript",
                )
            } else {
                LLMDialogDecision(
                    action = ScreeningAction.CONNECT_TO_USER,
                    replyToSpeak = "Thank you. Please hold while I try to reach them.",
                    thoughts = "Heuristic: max turns reached — defaulting to connect",
                )
            }
        }

        val reply = when {
            callerTranscript.isBlank() ->
                "Hi, you've reached an AI call screener. Who are you and why are you calling?"
            turnIndex == 0 ->
                "Thanks. This is an automated screener. Please briefly say your name and the reason for your call."
            else ->
                "Got it. Anything else I should know before I try to connect you?"
        }

        return LLMDialogDecision(
            action = ScreeningAction.CONTINUE_DIALOG,
            replyToSpeak = reply,
            thoughts = "Heuristic: continuing dialog at turn $turnIndex",
        )
    }

    fun knownVoicemail(
        callerTranscript: String,
        history: List<ConversationTurn>,
        ownerDisplayName: String,
    ): LLMDialogDecision {
        val callerTurns = history.count { it.role == ConversationTurn.Role.CALLER }
        return if (callerTurns == 0) {
            LLMDialogDecision(
                action = ScreeningAction.CONTINUE_DIALOG,
                replyToSpeak = "$ownerDisplayName is not available at the moment. Can I take a message for them?",
                thoughts = "Known-contact assistant greeting",
            )
        } else {
            LLMDialogDecision(
                action = ScreeningAction.TAKE_MESSAGE_AND_END,
                replyToSpeak = "Thank you, I'll make sure they get your message. Goodbye.",
                thoughts = "Captured message: \"$callerTranscript\"",
            )
        }
    }

    private const val MAX_UNKNOWN_TURNS = 4
}
