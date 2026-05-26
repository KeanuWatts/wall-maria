package com.aicallscreen.llm

import android.util.Log
import com.aicallscreen.llm.native.LlamaNativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * On-device llama.cpp inference via JNI stubs.
 */
class OnDeviceLlamaEngine(
    private val scope: CoroutineScope,
    private val modelAssetPath: String = "models/llama-spam-classifier.gguf",
) : ILocalLLMEngine {

    private val nativeBridge = LlamaNativeBridge()

    init {
        val loaded = nativeBridge.init(modelAssetPath)
        if (!loaded) {
            Log.w(TAG, "Llama native init failed; decisions will use heuristic fallback")
        }
    }

    override fun evaluateTranscript(transcript: String): Deferred<LLMDecision> =
        scope.async(Dispatchers.Default) {
            val json = nativeBridge.evaluate(transcript)
            parseDecision(json, transcript)
        }

    private fun parseDecision(json: String, transcript: String): LLMDecision {
        if (json.isBlank()) {
            return heuristicDecision(transcript)
        }
        val isSpam = json.contains("\"isSpam\":true", ignoreCase = true) ||
            json.contains("\"is_spam\":true", ignoreCase = true)
        val thoughts = json.substringAfter("\"thoughts\":", json)
            .trim('"', ' ', '\n', '}')
        return LLMDecision(isSpam = isSpam, thoughts = thoughts.ifBlank { json })
    }

    private fun heuristicDecision(transcript: String): LLMDecision {
        val lower = transcript.lowercase()
        val spamKeywords = listOf("warranty", "irs", "lottery", "press 1", "robo")
        val isSpam = spamKeywords.any { lower.contains(it) }
        return LLMDecision(
            isSpam = isSpam,
            thoughts = "Heuristic fallback — native llama.cpp not linked. Transcript length=${transcript.length}",
        )
    }

    companion object {
        private const val TAG = "OnDeviceLlamaEngine"
    }
}
