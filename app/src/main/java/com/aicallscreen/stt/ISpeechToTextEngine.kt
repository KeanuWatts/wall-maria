package com.aicallscreen.stt

import kotlinx.coroutines.flow.Flow

/**
 * Streaming speech-to-text contract. Implementations may run fully on-device (Whisper)
 * or delegate to cloud STT in future phases.
 */
interface ISpeechToTextEngine {

    /**
     * Transforms raw PCM chunks into partial or final transcript tokens.
     */
    fun streamAudioToText(audioData: ByteArray): Flow<String>
}
