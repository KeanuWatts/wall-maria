#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "whisper_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aicallscreen_stt_WhisperCppEngine_nativeInit(
    JNIEnv *env,
    jclass clazz,
    jstring model_path
) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("whisper.cpp init stub — model path: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    // TODO: link whisper.cpp and load model weights from assets.
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_aicallscreen_stt_WhisperCppEngine_nativeRelease(
    JNIEnv *env,
    jclass clazz
) {
    LOGI("whisper.cpp release stub");
}

JNIEXPORT jstring JNICALL
Java_com_aicallscreen_stt_WhisperCppEngine_nativeStreamTranscribe(
    JNIEnv *env,
    jobject thiz,
    jbyteArray pcm_chunk
) {
    jsize length = env->GetArrayLength(pcm_chunk);
    if (length <= 0) {
        return env->NewStringUTF("");
    }
    // Stub: emit empty until whisper.cpp streaming decoder is wired.
    return env->NewStringUTF("");
}

} // extern "C"
