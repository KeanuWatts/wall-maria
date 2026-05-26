# Keep JNI entry points for whisper.cpp and llama.cpp stubs.
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.aicallscreen.data.local.** { *; }
-keep class com.aicallscreen.llm.** { *; }
