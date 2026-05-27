package com.aicallscreen.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Captures caller microphone audio during an active screened call.
 *
 * Uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] which is appropriate while
 * [AudioManager.MODE_IN_COMMUNICATION] is active. Full downlink capture requires system
 * privileges; this foundation streams uplink/caller speech for STT.
 */
class CallAudioCapture(
    private val context: Context,
) {

    private val sampleRateHz: Int = 16_000
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO
    private val encoding: Int = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSizeBytes: Int = AudioRecord.getMinBufferSize(
        sampleRateHz,
        channelConfig,
        encoding,
    ).coerceAtLeast(sampleRateHz * 2)

    /**
     * Emits fixed-size PCM chunks while the coroutine scope remains active.
     */
    fun capturePcmChunks(): Flow<ByteArray> = flow {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRateHz,
            channelConfig,
            encoding,
            bufferSizeBytes * 2,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state=${audioRecord.state})")
            audioRecord.release()
            return@flow
        }

        val chunk = ByteArray(bufferSizeBytes)
        try {
            audioRecord.startRecording()
            while (coroutineContext.isActive) {
                val read = audioRecord.read(chunk, 0, chunk.size)
                if (read > 0) {
                    emit(chunk.copyOf(read))
                }
            }
        } finally {
            try {
                audioRecord.stop()
            } catch (ignored: IllegalStateException) {
                Log.w(TAG, "AudioRecord stop ignored", ignored)
            }
            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "CallAudioCapture"
    }
}
