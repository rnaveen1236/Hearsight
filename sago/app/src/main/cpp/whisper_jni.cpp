#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <algorithm>

#include "whisper.h"

#define TAG "SagoWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_hearsight_sago_stt_WhisperNative_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    LOGI("init %s -> %p", path, ctx);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hearsight_sago_stt_WhisperNative_transcribe(JNIEnv *env, jobject, jlong handle,
                                                     jfloatArray samples, jstring language) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewByteArray(0);

    const char *lang = env->GetStringUTFChars(language, nullptr);
    jsize n = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;            // "auto", "ta" or "en"
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.n_threads = std::max(1, std::min(6, (int) std::thread::hardware_concurrency() - 2));

    std::string text;
    if (whisper_full(ctx, params, pcm, n) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; ++i) text += whisper_full_get_segment_text(ctx, i);
    }

    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    // Raw UTF-8 bytes: Tamil output can contain sequences NewStringUTF rejects.
    jbyteArray out = env->NewByteArray((jsize) text.size());
    env->SetByteArrayRegion(out, 0, (jsize) text.size(), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hearsight_sago_stt_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}
