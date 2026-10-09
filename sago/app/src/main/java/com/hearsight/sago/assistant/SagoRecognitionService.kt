package com.hearsight.sago.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Required by Android for Sago to be selectable as the digital assistant.
 *
 * MVP stub: other apps calling SpeechRecognizer get ERROR_CLIENT. Backing this with
 * WhisperStt is a later milestone.
 */
class SagoRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}
