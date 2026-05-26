# AI Call Screening (Android 14+)

Hybrid local/cloud call screening foundation targeting **API 34+**. The app silences incoming rings, answers programmatically without pausing background media, streams caller audio to on-device STT, evaluates transcripts with a pluggable LLM strategy, logs every event to Room, and drops spam calls.

## Architecture

```
app/src/main/java/com/aicallscreen/
├── AiCallScreeningApplication.kt
├── di/ServiceLocator.kt
├── service/LocalCallScreeningService.kt      # CallScreeningService entry
├── screening/CallScreeningOrchestrator.kt    # Coroutine pipeline
├── audio/CallAudioFocusManager.kt            # No global audio focus
├── audio/CallAudioCapture.kt                   # AudioRecord PCM stream
├── tts/OfflineCallTtsEngine.kt                 # Offline TTS → call path
├── stt/ISpeechToTextEngine.kt
├── stt/WhisperCppEngine.kt                     # JNI → whisper.cpp stub
├── llm/ILocalLLMEngine.kt
├── llm/LLMDecision.kt
├── llm/OnDeviceLlamaEngine.kt
├── llm/CloudApiLlamaEngine.kt                  # Ktor + Retrofit
├── llm/LLMProvider.kt                          # Local vs Cloud flag
├── data/local/CallLogEntity.kt
├── data/local/CallLogDao.kt
├── data/local/AppDatabase.kt
├── data/repository/CallLogRepository.kt
├── telecom/CallControlCoordinator.kt
└── ui/CallLogActivity.kt
app/src/main/cpp/                               # JNI stubs
```

## Setup

1. Open in Android Studio Ladybug+ with JDK 17.
2. Grant runtime permissions: `RECORD_AUDIO`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`.
3. Assign **Call Screening** role: Settings → Apps → Default apps → Call screening → AI Call Screening.
4. Toggle cloud LLM in `app/build.gradle.kts`: `buildConfigField("boolean", "USE_CLOUD_LLM", "true")` and set `CLOUD_LLM_BASE_URL`.

## Native models (Phase 2)

Link `whisper.cpp` and `llama.cpp` in `app/src/main/cpp/CMakeLists.txt`, place GGUF/weights under `app/src/main/assets/models/`, and replace stub bodies in `whisper_jni.cpp` / `llama_jni.cpp`.

## Build

```bash
./gradlew :app:assembleDebug
```

## Screening flow

1. `onScreenCall` → `CallResponse` silences ring, allows call.
2. `CallAudioFocusManager` sets `MODE_IN_COMMUNICATION` without `requestAudioFocus()`.
3. `TelecomManager.acceptRingingCall()` answers silently.
4. `OfflineCallTtsEngine` speaks greeting on `USAGE_VOICE_COMMUNICATION`.
5. `CallAudioCapture` → `WhisperCppEngine.streamAudioToText`.
6. `LLMProvider.activeEngine().evaluateTranscript` → `LLMDecision`.
7. `CallLogRepository.persistScreeningEvent` (Room).
8. If `isSpam`, `TelecomManager.endCall()`.
