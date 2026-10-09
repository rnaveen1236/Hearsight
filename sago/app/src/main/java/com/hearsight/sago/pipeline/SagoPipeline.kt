package com.hearsight.sago.pipeline

import android.content.Context
import com.hearsight.sago.audio.SpeechRecorder
import com.hearsight.sago.brain.Brain
import com.hearsight.sago.brain.BrainResult
import com.hearsight.sago.brain.EchoBrain
import com.hearsight.sago.brain.GemmaBrain
import com.hearsight.sago.router.ActionRouter
import com.hearsight.sago.stt.WhisperStt
import com.hearsight.sago.tts.Speaker

/**
 * One assistant turn: listen → Whisper → Brain → (Action Router) → speak.
 *
 * Held as a process-wide singleton so the Whisper and Gemma models stay
 * loaded between invocations instead of reloading on every "Hey Sago".
 */
class SagoPipeline private constructor(private val context: Context) {

    sealed interface Event {
        data object Listening : Event
        data class Heard(val text: String) : Event
        data object Thinking : Event
        data class Reply(val text: String) : Event
        data class Failed(val message: String) : Event
    }

    /** How the turn ended; the session closes faster after launching another app. */
    data class TurnResult(val closeOverlay: Boolean)

    private val recorder = SpeechRecorder()
    private val stt = WhisperStt(context)
    private val router = ActionRouter(context)
    private val speaker = Speaker(context)

    @Volatile private var brain: Brain? = null

    suspend fun runTurn(onEvent: (Event) -> Unit): TurnResult {
        onEvent(Event.Listening)
        val audio = recorder.recordUtterance()
        if (audio.isEmpty()) {
            onEvent(Event.Failed("I didn't hear anything."))
            return TurnResult(closeOverlay = false)
        }

        onEvent(Event.Thinking)
        val text = stt.transcribe(audio)
        if (text.isBlank()) {
            reply("Sorry, I didn't catch that.", onEvent)
            return TurnResult(closeOverlay = false)
        }
        onEvent(Event.Heard(text))

        return when (val result = brain().respond(text)) {
            is BrainResult.Answer -> {
                reply(result.text, onEvent)
                TurnResult(closeOverlay = false)
            }
            is BrainResult.Action -> {
                val outcome = router.execute(result)
                reply(outcome.spoken, onEvent)
                TurnResult(outcome.closeOverlay)
            }
        }
    }

    fun stopSpeaking() = speaker.stop()

    /** Gemma once its model has been pushed; until then the Day 1 echo keeps the loop testable. */
    private fun brain(): Brain {
        brain?.let { if (it is GemmaBrain) return it }
        val model = GemmaBrain.findModel(context)
        return (if (model != null) GemmaBrain(context, model) else EchoBrain()).also { brain = it }
    }

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
