package com.hearsight.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.hearsight.navcore.Command
import com.hearsight.navcore.Cue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns cues into what the user feels and hears:
 *  - vibration: LEFT = two short pulses, RIGHT = one long pulse, STOP = two long pulses
 *  - stereo beeps (earbuds): panned towards the side to turn to, faster for sharper turns
 *  - speech for hazards and turn changes (rate-limited upstream)
 * Silence means "keep walking straight". If cues stop arriving, it tells the user to stop.
 */
class Feedback(context: Context) : TextToSpeech.OnInitListener {
    @Volatile var vibrate = true
    @Volatile var speech = true
    @Volatile var beeps = true
    @Volatile var active = false
        set(value) { field = value; lastUpdate = SystemClock.elapsedRealtime(); lostAnnounced = false }

    @Volatile private var cue = Cue(Command.STOP)
    @Volatile private var lastUpdate = 0L
    @Volatile private var lostAnnounced = false
    @Volatile private var running = true

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ttsReady = false

    private val sampleRate = 44100
    private val audio = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(sampleRate)  // 0.25 s of stereo 16-bit
        .build()
        .also { it.play() }

    private val loop = Thread(::cueLoop, "hearsight-feedback").also { it.start() }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(1.15f)
            ttsReady = true
        }
    }

    fun update(c: Cue) {
        cue = c
        lastUpdate = SystemClock.elapsedRealtime()
        if (lostAnnounced) lostAnnounced = false
        c.say?.let(::speak)
    }

    fun speak(text: String) {
        if (speech && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hearsight")
    }

    fun shutdown() {
        running = false
        loop.join(1000)
        tts.shutdown()
        audio.release()
        vibrator.cancel()
    }

    private fun cueLoop() {
        while (running) {
            var next = 150L
            if (active) {
                val c = cue
                if (SystemClock.elapsedRealtime() - lastUpdate > 1000) {
                    if (!lostAnnounced) {
                        lostAnnounced = true
                        speak("Camera lost. Stop.")
                        buzz(longArrayOf(0, 600, 200, 600))
                    }
                    next = 500
                } else when (c.command) {
                    Command.STOP -> { buzz(longArrayOf(0, 400, 100, 400)); beep(0.0, 880.0, 250); next = 1000 }
                    Command.LEFT -> { buzz(longArrayOf(0, 60, 80, 60)); beep(-1.0, 520.0, 120); next = interval(c) }
                    Command.RIGHT -> { buzz(longArrayOf(0, 220)); beep(1.0, 520.0, 120); next = interval(c) }
                    Command.STRAIGHT -> next = 150
                }
            }
            try { Thread.sleep(next) } catch (_: InterruptedException) { return }
        }
    }

    /** Sharper turns repeat faster: 900 ms for a slight turn down to 400 ms. */
    private fun interval(c: Cue) = (900 - 500 * c.urgency).toLong()

    private fun buzz(pattern: LongArray) {
        if (vibrate && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /** Short tone, equal-power panned: pan -1 = left ear, +1 = right ear. Blocks while writing. */
    private fun beep(pan: Double, freq: Double, ms: Int) {
        if (!beeps) return
        val n = sampleRate * ms / 1000
        val angle = (pan.coerceIn(-1.0, 1.0) + 1) * PI / 4
        val gl = cos(angle); val gr = sin(angle)
        val buf = ShortArray(2 * n)
        for (i in 0 until n) {
            val env = minOf(1.0, i / (0.01 * sampleRate)) * minOf(1.0, (n - i) / (0.03 * sampleRate))
            val s = sin(2 * PI * freq * i / sampleRate) * env * 0.4 * Short.MAX_VALUE
            buf[2 * i] = (s * gl).toInt().toShort()
            buf[2 * i + 1] = (s * gr).toInt().toShort()
        }
        audio.write(buf, 0, buf.size)
    }
}
