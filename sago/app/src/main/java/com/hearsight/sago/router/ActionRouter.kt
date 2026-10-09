package com.hearsight.sago.router

import android.content.Context
import com.hearsight.sago.brain.BrainResult
import com.hearsight.sago.router.apps.AppLauncher
import com.hearsight.sago.router.apps.InstalledAppRegistry
import com.hearsight.sago.router.calls.CallManager
import com.hearsight.sago.router.system.SystemControls

/** What Sago says after an action, and whether the overlay should get out of the way. */
data class ActionOutcome(val spoken: String, val closeOverlay: Boolean = false)

/**
 * Executes the actions Gemma picks. Gemma decides; this class is the only place
 * that touches Android APIs on its behalf.
 */
class ActionRouter(context: Context) {

    private val apps = InstalledAppRegistry(context)
    private val launcher = AppLauncher(context, apps)
    private val calls = CallManager(context)
    private val system = SystemControls(context)

    suspend fun execute(action: BrainResult.Action): ActionOutcome {
        val p = action.parameters
        return when (action.name) {
            "OPEN_APP" -> launcher.open(p["app"].orEmpty())
            "APP_SEARCH" -> launcher.search(p["app"].orEmpty(), p["query"].orEmpty())
            "CALL_CONTACT" -> calls.call(p["contact"].orEmpty())
            "VOLUME" -> system.volume(p["direction"].orEmpty())
            "FLASHLIGHT" -> system.flashlight(p["state"].orEmpty())
            else -> ActionOutcome("I can't do ${action.name.lowercase().replace('_', ' ')} yet.")
        }
    }
}
