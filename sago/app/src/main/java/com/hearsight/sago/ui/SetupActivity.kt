package com.hearsight.sago.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hearsight.sago.R
import com.hearsight.sago.assistant.SagoVoiceInteractionService
import com.hearsight.sago.stt.WhisperStt

/** One-time setup: microphone, default assistant, model check, and a test button. */
class SetupActivity : AppCompatActivity() {

    private lateinit var checklist: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        checklist = findViewById(R.id.checklist)

        findViewById<Button>(R.id.btn_mic).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        findViewById<Button>(R.id.btn_assistant).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            if (!SagoVoiceInteractionService.trigger()) {
                Toast.makeText(this, "Set Sago as the digital assistant first.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val assistant = VoiceInteractionService.isActiveService(
            this, ComponentName(this, SagoVoiceInteractionService::class.java),
        )
        val model = WhisperStt.modelFile(this)

        checklist.text = buildString {
            appendLine("${mark(mic)} Microphone allowed")
            appendLine("${mark(assistant)} Sago is the digital assistant")
            appendLine("${mark(model.exists())} Whisper model present")
            if (!model.exists()) {
                appendLine()
                appendLine("Push it with:")
                appendLine("adb push ${WhisperStt.MODEL_NAME} ${model.absolutePath}")
            }
        }
    }

    private fun mark(ok: Boolean) = if (ok) "✅" else "⬜"

    private companion object {
        const val REQ_MIC = 1
    }
}
