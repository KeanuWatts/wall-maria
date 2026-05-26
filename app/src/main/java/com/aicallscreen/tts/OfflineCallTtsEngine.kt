package com.aicallscreen.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline [TextToSpeech] wrapper that routes synthesized speech into the active call stream
 * via [AudioAttributes.USAGE_VOICE_COMMUNICATION].
 */
class OfflineCallTtsEngine(
    private val context: Context,
) {

    private val isReady = AtomicBoolean(false)
    private var textToSpeech: TextToSpeech? = null
    private val initDeferred = CompletableDeferred<Boolean>()
    private val utteranceCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureEngine()
                isReady.set(true)
                initDeferred.complete(true)
            } else {
                Log.e(TAG, "TextToSpeech init failed: status=$status")
                initDeferred.complete(false)
            }
        }
    }

    private fun configureEngine() {
        val engine = textToSpeech ?: return
        engine.language = Locale.US

        val callAttributes = ServiceLocator.audioFocusManager
            .voiceCommunicationAttributes()

        engine.setAudioAttributes(callAttributes)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val voices = engine.voices.orEmpty()
            val offlineVoice = voices.firstOrNull { voice ->
                !voice.isNetworkConnectionRequired
            }
            if (offlineVoice != null) {
                engine.voice = offlineVoice
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                completeUtterance(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                completeUtterance(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error utterance=$utteranceId code=$errorCode")
                completeUtterance(utteranceId)
            }
        })
    }

    private fun completeUtterance(utteranceId: String?) {
        if (utteranceId == null) return
        utteranceCompletions.remove(utteranceId)?.complete(Unit)
    }

    suspend fun speakGreetingToCall(
        text: String = context.getString(R.string.default_tts_greeting),
    ): String = speakToCall(text, utteranceId = UTTERANCE_GREETING)

    /**
     * Speaks [text] into the call audio path and suspends until playback completes.
     */
    suspend fun speakToCall(
        text: String,
        utteranceId: String = "tts_${System.nanoTime()}",
    ): String = withContext(Dispatchers.Main) {
        if (!initDeferred.await()) {
            Log.w(TAG, "TTS not ready; returning text without playback")
            return@withContext text
        }

        val engine = textToSpeech ?: return@withContext text
        val done = CompletableDeferred<Unit>()
        utteranceCompletions[utteranceId] = done

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, 0)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId,
        )

        done.await()
        text
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    companion object {
        private const val TAG = "OfflineCallTtsEngine"
        private const val UTTERANCE_GREETING = "ai_screening_greeting"
    }
}
