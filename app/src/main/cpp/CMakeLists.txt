cmake_minimum_required(VERSION 3.22.1)
project("aicallscreen_native")

add_library(
    aicallscreen_native
    SHARED
    whisper_jni.cpp
    llama_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    aicallscreen_native
    ${log-lib}
)
