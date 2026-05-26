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

## Call routing

| Caller | Ring behavior | AI behavior |
|--------|---------------|-------------|
| **Saved contact** | Normal ring (`setSilenceCall(false)`) | If unanswered ~25s → assistant: "*Owner* is not available… Can I take a message?" |
| **Unknown number** | Silent (`setSilenceCall(true)`) | Multi-turn AI dialog → block spam **or** ring you via `UserEscalationManager` |

## Unknown caller flow

1. Silence ring → auto-answer → multi-turn TTS/STT/LLM loop (`ConversationPipeline`).
2. `CONNECT_TO_USER` → speak hold message → local ringtone + full-screen notification (does not pause Spotify).
3. Tap notification → `EscalationTrampolineActivity` → system dialer for the live call.
4. `BLOCK_CALL` → speak goodbye → `endCall()`.

## Permissions

Grant on first launch: `READ_CONTACTS` (known vs unknown), `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `RECORD_AUDIO`, `POST_NOTIFICATIONS` (escalation ring).
