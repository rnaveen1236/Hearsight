package com.hearsight.sago.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hearsight.sago.R
import com.hearsight.sago.assistant.SagoVoiceInteractionService
import com.hearsight.sago.brain.GemmaBrain
import com.hearsight.sago.router.calls.ContactAliases
import com.hearsight.sago.stt.WhisperStt

/** One-time setup: permissions, default assistant, model check, nicknames and a test button. */
class SetupActivity : AppCompatActivity() {

    private lateinit var checklist: TextView
    private lateinit var aliasList: TextView
    private lateinit var aliases: ContactAliases

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        checklist = findViewById(R.id.checklist)
        aliasList = findViewById(R.id.alias_list)
        aliases = ContactAliases(this)

        findViewById<Button>(R.id.btn_mic).setOnClickListener {
            requestPermissions(PERMISSIONS, REQ_PERMISSIONS)
        }
        findViewById<Button>(R.id.btn_assistant).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            if (!SagoVoiceInteractionService.trigger()) {
                Toast.makeText(this, "Set Sago as the digital assistant first.", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.alias_save).setOnClickListener {
            val name = findViewById<EditText>(R.id.alias_name)
            val contact = findViewById<EditText>(R.id.alias_contact)
            if (name.text.isNotBlank() && contact.text.isNotBlank()) {
                aliases.put(name.text.toString(), contact.text.toString())
                name.text.clear()
                contact.text.clear()
                refresh()
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
        val permissions = PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val assistant = VoiceInteractionService.isActiveService(
            this, ComponentName(this, SagoVoiceInteractionService::class.java),
        )
        val whisper = WhisperStt.modelFile(this)
        val gemma = GemmaBrain.findModel(this)
        val modelsDir = whisper.parentFile?.absolutePath

        checklist.text = buildString {
            appendLine("${mark(permissions)} Microphone, contacts & calls allowed")
            appendLine("${mark(assistant)} Sago is the digital assistant")
            appendLine("${mark(whisper.exists())} Whisper model present")
            appendLine("${mark(gemma != null)} Gemma model present${gemma?.let { " (${it.name})" } ?: " — Sago echoes until it is"}")
            if (!whisper.exists() || gemma == null) {
                appendLine()
                appendLine("Push models with:")
                if (!whisper.exists()) appendLine("adb push ${WhisperStt.MODEL_NAME} ${whisper.absolutePath}")
                if (gemma == null) appendLine("adb push <gemma>.task $modelsDir/")
            }
        }
        aliasList.text = aliases.all().entries.joinToString("\n") { "${it.key} → ${it.value}" }
    }

    private fun mark(ok: Boolean) = if (ok) "✅" else "⬜"

    private companion object {
        const val REQ_PERMISSIONS = 1
        val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
        )
    }
}
