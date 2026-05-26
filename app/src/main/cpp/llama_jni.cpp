#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aicallscreen_llm_native_LlamaNativeBridge_nativeInit(
    JNIEnv *env,
    jobject thiz,
    jstring model_path
) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("llama.cpp init stub — model path: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    // TODO: link llama.cpp and mmap GGUF weights.
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_aicallscreen_llm_native_LlamaNativeBridge_nativeEvaluate(
    JNIEnv *env,
    jobject thiz,
    jstring transcript
) {
    const char *text = env->GetStringUTFChars(transcript, nullptr);
    std::string payload = std::string("{\"isSpam\":false,\"thoughts\":\"Native llama stub evaluated: ") + text + "\"}";
    env->ReleaseStringUTFChars(transcript, text);
    return env->NewStringUTF(payload.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_aicallscreen_llm_native_LlamaNativeBridge_nativeEvaluateDialog(
    JNIEnv *env,
    jobject thiz,
    jstring caller_transcript,
    jstring history_json,
    jstring mode
) {
    const char *text = env->GetStringUTFChars(caller_transcript, nullptr);
    std::string payload = std::string(
        "{\"action\":\"CONTINUE_DIALOG\",\"replyToSpeak\":\"\",\"thoughts\":\"Native dialog stub for: "
    ) + text + "\"}";
    env->ReleaseStringUTFChars(caller_transcript, text);
    return env->NewStringUTF(payload.c_str());
}

JNIEXPORT void JNICALL
Java_com_aicallscreen_llm_native_LlamaNativeBridge_nativeRelease(
    JNIEnv *env,
    jobject thiz
) {
    LOGI("llama.cpp release stub");
}

} // extern "C"
