package com.aicallscreen.llm.native

import android.util.Log

/**
 * JNI bridge to llama.cpp. Replace stub bodies in [aicallscreen_native] when models are bundled.
 */
class LlamaNativeBridge {

    init {
        try {
            System.loadLibrary("aicallscreen_native")
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Native llama library not loaded", error)
        }
    }

    fun init(modelPath: String): Boolean = nativeInit(modelPath)

    fun evaluate(transcript: String): String = nativeEvaluate(transcript)

    fun evaluateDialog(
        callerTranscript: String,
        historyJson: String,
        mode: String,
    ): String = nativeEvaluateDialog(callerTranscript, historyJson, mode)

    fun release() = nativeRelease()

    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeEvaluate(transcript: String): String
    private external fun nativeEvaluateDialog(
        callerTranscript: String,
        historyJson: String,
        mode: String,
    ): String
    private external fun nativeRelease()

    companion object {
        private const val TAG = "LlamaNativeBridge"
    }
}
