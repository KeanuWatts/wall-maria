package com.aicallscreen.stt

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * On-device STT backed by whisper.cpp via JNI.
 *
 * JNI methods are stubs until native libraries are linked. The Kotlin layer remains stable
 * for integration testing with mocked transcripts.
 */
class WhisperCppEngine : ISpeechToTextEngine {

    init {
        try {
            System.loadLibrary("aicallscreen_native")
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Native whisper library not loaded; using stub transcripts", error)
        }
    }

    override fun streamAudioToText(audioData: ByteArray): Flow<String> = flow {
        if (audioData.isEmpty()) return@flow

        val partial = nativeStreamTranscribe(audioData)
        if (partial.isNotBlank()) {
            emit(partial)
        }
    }

    private external fun nativeStreamTranscribe(pcmChunk: ByteArray): String

    companion object {
        private const val TAG = "WhisperCppEngine"

        @JvmStatic
        private external fun nativeInit(modelPath: String): Boolean

        @JvmStatic
        private external fun nativeRelease()
    }
}
