package com.hearsight.sago.pipeline

import android.content.Context
import com.hearsight.sago.audio.SpeechRecorder
import com.hearsight.sago.brain.Brain
import com.hearsight.sago.brain.BrainResult
import com.hearsight.sago.brain.EchoBrain
import com.hearsight.sago.stt.WhisperStt
import com.hearsight.sago.tts.Speaker

/**
 * One assistant turn: listen → Whisper → Brain → speak.
 *
 * Held as a process-wide singleton so the Whisper (and later Gemma) model stays
 * loaded between invocations instead of reloading on every "Hey Sago".
 */
class SagoPipeline private constructor(context: Context) {

    sealed interface Event {
        data object Listening : Event
        data class Heard(val text: String) : Event
        data object Thinking : Event
        data class Reply(val text: String) : Event
        data class Failed(val message: String) : Event
    }

    private val recorder = SpeechRecorder()
    private val stt = WhisperStt(context)
    private val brain: Brain = EchoBrain()
    private val speaker = Speaker(context)

    suspend fun runTurn(onEvent: (Event) -> Unit) {
        onEvent(Event.Listening)
        val audio = recorder.recordUtterance()
        if (audio.isEmpty()) {
            onEvent(Event.Failed("I didn't hear anything."))
            return
        }

        onEvent(Event.Thinking)
        val text = stt.transcribe(audio)
        if (text.isBlank()) {
            reply("Sorry, I didn't catch that.", onEvent)
            return
        }
        onEvent(Event.Heard(text))

        when (val result = brain.respond(text)) {
            is BrainResult.Answer -> reply(result.text, onEvent)
            is BrainResult.Action -> reply("Action ${result.name} isn't wired up yet.", onEvent)
        }
    }

    fun stopSpeaking() = speaker.stop()

    private suspend fun reply(text: String, onEvent: (Event) -> Unit) {
        onEvent(Event.Reply(text))
        speaker.speak(text)
    }

    companion object {
        @Volatile private var instance: SagoPipeline? = null

        fun get(context: Context): SagoPipeline =
            instance ?: synchronized(this) {
                instance ?: SagoPipeline(context.applicationContext).also { instance = it }
            }
    }
}
