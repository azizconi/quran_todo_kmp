// JNI to whisper.cpp (task F7, prototype): load a ggml model, turn 16 kHz mono float PCM into text. Nothing else.
#include <jni.h>
#include <android/log.h>
#include <string>

#include "whisper.h"

#define TAG "VoiceProto"

extern "C" {

JNIEXPORT jlong JNICALL
Java_tj_app_quran_1todo_voiceproto_WhisperNative_init(JNIEnv *env, jobject, jstring path) {
    const char *chars = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = true;
    whisper_context *context = whisper_init_from_file_with_params(chars, params);
    __android_log_print(ANDROID_LOG_INFO, TAG, "model %s -> %p", chars, context);
    env->ReleaseStringUTFChars(path, chars);
    return reinterpret_cast<jlong>(context);
}

JNIEXPORT void JNICALL
Java_tj_app_quran_1todo_voiceproto_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

// The text of the audio; "" when whisper fails. The same settings as `whisper-cli -l ar -nt -bs 1 -bo 1 -mc 0` of the Mac evaluation.
JNIEXPORT jstring JNICALL
Java_tj_app_quran_1todo_voiceproto_WhisperNative_transcribe(JNIEnv *env, jobject, jlong handle, jfloatArray samples, jint threads,
                                                          jstring prompt) {
    auto *context = reinterpret_cast<whisper_context *>(handle);
    jsize count = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "ar";
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.no_timestamps = true;
    params.n_threads = threads;
    params.n_max_text_ctx = 0;
    params.greedy.best_of = 1;
    const char *promptChars = nullptr;
    if (prompt != nullptr) {
        promptChars = env->GetStringUTFChars(prompt, nullptr);
        if (promptChars[0] != '\0') params.initial_prompt = promptChars;
    }

    std::string text;
    int status = whisper_full(context, params, data, count);
    if (status == 0) {
        int segments = whisper_full_n_segments(context);
        for (int i = 0; i < segments; i++) {
            if (i > 0) text += " ";
            text += whisper_full_get_segment_text(context, i);
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed: %d", status);
    }
    if (promptChars != nullptr) env->ReleaseStringUTFChars(prompt, promptChars);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jstring JNICALL
Java_tj_app_quran_1todo_voiceproto_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

}
