package com.hearsight.sago.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.UUID

/** Android TextToSpeech, picking Tamil or Indian English from the reply's script. */
class Speaker(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private val pending = mutableMapOf<String, CompletableDeferred<Unit>>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) = finish(id)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = finish(id)
            override fun onError(id: String, errorCode: Int) = finish(id)
        })
    }

    /** Suspends until the utterance has finished playing. */
    suspend fun speak(text: String) {
        if (text.isBlank() || !ready.await()) return
        tts.language = if (text.any { it in '஀'..'௿' }) TAMIL else INDIAN_ENGLISH
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        synchronized(pending) { pending[id] = done }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        done.await()
    }

    fun stop() = tts.stop()

    fun shutdown() = tts.shutdown()

    private fun finish(id: String) {
        synchronized(pending) { pending.remove(id) }?.complete(Unit)
    }

    private companion object {
        val TAMIL: Locale = Locale.forLanguageTag("ta-IN")
        val INDIAN_ENGLISH: Locale = Locale.forLanguageTag("en-IN")
    }
}
