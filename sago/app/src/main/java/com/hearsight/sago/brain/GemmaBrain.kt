package com.hearsight.sago.brain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Gemma via MediaPipe LLM Inference.
 *
 * Loads the first `*.task` file in the models dir (Gemma 4 E4B, or Gemma 3n E4B as
 * fallback). Loading takes 10–20 s, so the instance stays warm for the process lifetime.
 */
class GemmaBrain(private val context: Context, private val modelFile: File) : Brain {

    private val lock = Mutex()
    private var llm: LlmInference? = null

    override suspend fun respond(utterance: String): BrainResult = withContext(Dispatchers.Default) {
        val raw = lock.withLock {
            val engine = llm ?: load().also { llm = it }
            engine.generateResponse(IntentPrompt.build(utterance))
        }
        Log.i(TAG, "Gemma: $raw")
        IntentParser.parse(raw)
    }

    private fun load(): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    companion object {
        private const val TAG = "SagoGemma"
        private const val MAX_TOKENS = 1024

        /** First `.task` model in the app's models dir, or null if none was pushed yet. */
        fun findModel(context: Context): File? =
            File(context.getExternalFilesDir(null), "models")
                .listFiles { f -> f.extension == "task" }
                ?.maxByOrNull { it.length() }
    }
}
