package com.hearsight.sago.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Sago's system-assistant entry point. Android keeps this bound while Sago is the
 * default digital assistant; long-press power and (Day 3) the wake word both end in
 * [showSession], which asks [SagoSessionService] for a new [SagoSession].
 */
class SagoVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        active = this
        Log.i(TAG, "Sago is the active assistant")
    }

    override fun onShutdown() {
        if (active === this) active = null
        super.onShutdown()
    }

    companion object {
        private const val TAG = "SagoVIS"

        /** Set while Android has Sago bound as the assistant; null otherwise. */
        @Volatile var active: SagoVoiceInteractionService? = null
            private set

        /** Opens a Sago session from anywhere in the app (setup test button, wake word). */
        fun trigger(): Boolean {
            val service = active ?: return false
            service.showSession(Bundle(), 0)
            return true
        }
    }
}
