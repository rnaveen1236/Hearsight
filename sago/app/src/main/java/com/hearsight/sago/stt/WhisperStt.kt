package com.hearsight.sago.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device speech to text with whisper.cpp.
 *
 * The model lives in the app-specific external dir so `adb push` can reach it
 * and uninstalling Sago deletes it:
 *   /sdcard/Android/data/com.hearsight.sago/files/models/ggml-small.bin
 */
class WhisperStt(context: Context) {

    val modelFile: File = modelFile(context)

    private val lock = Mutex()
    private var handle = 0L

    suspend fun transcribe(samples: FloatArray, language: String = "auto"): String =
        withContext(Dispatchers.Default) {
            lock.withLock {
                if (handle == 0L) {
                    check(modelFile.exists()) { "Whisper model missing: ${modelFile.absolutePath}" }
                    handle = WhisperNative.init(modelFile.absolutePath)
                    check(handle != 0L) { "Whisper failed to load ${modelFile.name}" }
                }
                WhisperNative.transcribe(handle, samples, language)
                    .toString(Charsets.UTF_8)
                    .trim()
            }
        }

    suspend fun release() = lock.withLock {
        if (handle != 0L) WhisperNative.free(handle)
        handle = 0L
    }

    companion object {
        const val MODEL_NAME = "ggml-small.bin"

        fun modelFile(context: Context): File =
            File(context.getExternalFilesDir(null), "models/$MODEL_NAME")
    }
}
