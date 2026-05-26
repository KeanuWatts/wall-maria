package com.aicallscreen.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aicallscreen.R
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.di.ServiceLocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
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

        // Prefer on-device engine when available (Android 11+).
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
                if (utteranceId == UTTERANCE_GREETING) {
                    greetingDone.complete(Unit)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                greetingDone.complete(Unit)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error utterance=$utteranceId code=$errorCode")
                greetingDone.complete(Unit)
            }
        })
    }

    private var greetingDone = CompletableDeferred<Unit>()

    /**
     * Speaks [text] into the call audio path as soon as TTS is initialized.
     */
    suspend fun speakGreetingToCall(
        text: String = context.getString(R.string.default_tts_greeting),
    ): String = withContext(Dispatchers.Main) {
        if (!initDeferred.await()) {
            Log.w(TAG, "TTS not ready; returning text without playback")
            return@withContext text
        }

        greetingDone = CompletableDeferred()
        val engine = textToSpeech ?: return@withContext text

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, 0)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            UTTERANCE_GREETING,
        )

        greetingDone.await()
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
