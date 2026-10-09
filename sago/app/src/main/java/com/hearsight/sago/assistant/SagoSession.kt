package com.hearsight.sago.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.widget.TextView
import com.hearsight.sago.R
import com.hearsight.sago.pipeline.SagoPipeline
import com.hearsight.sago.pipeline.SagoPipeline.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The overlay shown on top of whatever app is open, and one listen → reply turn. */
class SagoSession(context: Context) : VoiceInteractionSession(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var turn: Job? = null

    private var status: TextView? = null
    private var transcript: TextView? = null
    private var reply: TextView? = null

    override fun onCreateContentView(): View {
        val view = layoutInflater.inflate(R.layout.session_overlay, null)
        status = view.findViewById(R.id.status)
        transcript = view.findViewById(R.id.transcript)
        reply = view.findViewById(R.id.reply)
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        transcript?.text = ""
        reply?.text = ""

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status?.text = "Open the Sago app and allow the microphone."
            return
        }

        turn?.cancel()
        turn = scope.launch {
            val pipeline = SagoPipeline.get(context)
            try {
                pipeline.runTurn(::render)
                delay(CLOSE_DELAY_MS)
                hide()
            } catch (e: Exception) {
                Log.e(TAG, "Turn failed", e)
                render(Event.Failed(e.message ?: "Something went wrong."))
            }
        }
    }

    override fun onHide() {
        turn?.cancel()
        SagoPipeline.get(context).stopSpeaking()
        super.onHide()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(event: Event) {
        when (event) {
            Event.Listening -> status?.setText(R.string.status_listening)
            Event.Thinking -> status?.setText(R.string.status_thinking)
            is Event.Heard -> transcript?.text = event.text
            is Event.Reply -> {
                status?.setText(R.string.status_ready)
                reply?.text = event.text
            }
            is Event.Failed -> {
                status?.setText(R.string.status_ready)
                reply?.text = event.message
            }
        }
    }

    private companion object {
        const val TAG = "SagoSession"
        const val CLOSE_DELAY_MS = 1500L
    }
}
