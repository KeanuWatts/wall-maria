package com.aicallscreen.llm

enum class ScreeningAction {
    /** Ask another clarifying question (multi-turn). */
    CONTINUE_DIALOG,

    /** Caller passed screening — ring the device owner. */
    CONNECT_TO_USER,

    /** Spam / unwanted — drop the call. */
    BLOCK_CALL,

    /** Known-contact path: capture a message then end. */
    TAKE_MESSAGE_AND_END,
}
