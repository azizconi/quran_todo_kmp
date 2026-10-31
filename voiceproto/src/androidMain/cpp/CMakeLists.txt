cmake_minimum_required(VERSION 3.18)
project(voiceproto LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp v1.9.5 (MIT), pinned by the SHA-256 of the tag archive. WHISPER_SRC_DIR (gradle: -Pwhisper.src) takes an unpacked copy of
# the same tag instead of the download.
include(FetchContent)
if (WHISPER_SRC_DIR)
    FetchContent_Declare(whisper SOURCE_DIR ${WHISPER_SRC_DIR})
else ()
    FetchContent_Declare(whisper
        URL https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.9.5
        URL_HASH SHA256=ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720)
endif ()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # ARMv8.2 with the dot product and half precision: every phone of the last years; the old armv8.0 ones are not supported.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif ()
FetchContent_MakeAvailable(whisper)

add_library(voiceproto SHARED whisper_jni.cpp)
target_link_libraries(voiceproto whisper log android)
target_compile_options(voiceproto PRIVATE -O3 -fvisibility=hidden)
target_link_options(voiceproto PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
