package com.aicallscreen.llm

import android.util.Log
import com.aicallscreen.llm.api.LlmEvaluateRequest
import com.aicallscreen.llm.api.LlmEvaluateResponse
import com.aicallscreen.llm.api.LlmRestApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Cloud LLM path using Ktor (primary) with a Retrofit mirror for GPU auto-scaling REST gateways.
 */
class CloudApiLlamaEngine(
    private val scope: CoroutineScope,
    private val baseUrl: String,
) : ILocalLLMEngine {

    private val ktorClient: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    private val retrofitApi: LlmRestApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LlmRestApi::class.java)
    }

    override fun evaluateTranscript(transcript: String): Deferred<LLMDecision> =
        scope.async(Dispatchers.IO) {
            evaluateWithKtor(transcript)
        }

    private suspend fun evaluateWithKtor(transcript: String): LLMDecision = withContext(Dispatchers.IO) {
        try {
            val response: LlmEvaluateResponse = ktorClient.post("${baseUrl.trimEnd('/')}/evaluate") {
                contentType(ContentType.Application.Json)
                setBody(LlmEvaluateRequest(transcript = transcript))
            }.body()

            LLMDecision(
                isSpam = response.isSpam,
                thoughts = response.thoughts,
            )
        } catch (ktorError: Exception) {
            Log.w(TAG, "Ktor evaluate failed; falling back to Retrofit", ktorError)
            evaluateWithRetrofit(transcript)
        }
    }

    private suspend fun evaluateWithRetrofit(transcript: String): LLMDecision = withContext(Dispatchers.IO) {
        val response = retrofitApi.evaluate(LlmEvaluateRequest(transcript = transcript))
        LLMDecision(
            isSpam = response.isSpam,
            thoughts = response.thoughts,
        )
    }

    companion object {
        private const val TAG = "CloudApiLlamaEngine"
    }
}

/** Retrofit service definition — mirrors Ktor JSON contract. */
interface LlmRestApi {
    @POST("evaluate")
    suspend fun evaluate(@Body request: LlmEvaluateRequest): LlmEvaluateResponse
}
