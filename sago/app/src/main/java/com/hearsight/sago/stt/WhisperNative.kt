package com.hearsight.sago.stt

/** JNI surface of whisper_jni.cpp. Handles are opaque whisper_context pointers. */
internal object WhisperNative {
    init {
        System.loadLibrary("sago_whisper")
    }

    external fun init(modelPath: String): Long
    external fun transcribe(handle: Long, samples: FloatArray, language: String): ByteArray
    external fun free(handle: Long)
}
