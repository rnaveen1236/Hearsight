package com.hearsight.sago.router.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.hearsight.sago.router.ActionOutcome

/** Phone controls Android lets any app change directly. Home/back/lock come with Accessibility (Day 3). */
class SystemControls(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)

    fun volume(direction: String): ActionOutcome {
        val (adjust, spoken) = when (direction.lowercase()) {
            "up" -> AudioManager.ADJUST_RAISE to "Volume up."
            "down" -> AudioManager.ADJUST_LOWER to "Volume down."
            "mute" -> AudioManager.ADJUST_MUTE to "Muted."
            "unmute" -> AudioManager.ADJUST_UNMUTE to "Unmuted."
            else -> return ActionOutcome("Should I turn the volume up or down?")
        }
        // Two steps per command so "konjam reduce" is audible without overshooting.
        val steps = if (adjust == AudioManager.ADJUST_RAISE || adjust == AudioManager.ADJUST_LOWER) 2 else 1
        repeat(steps) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI) }
        return ActionOutcome(spoken)
    }

    fun flashlight(state: String): ActionOutcome {
        val on = when (state.lowercase()) {
            "on" -> true
            "off" -> false
            else -> return ActionOutcome("Should I turn the torch on or off?")
        }
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionOutcome("This phone has no torch I can control.")
        cameras.setTorchMode(id, on)
        return ActionOutcome(if (on) "Torch on." else "Torch off.")
    }
}
