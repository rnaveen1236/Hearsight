package com.hearsight.sago.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Records one utterance at 16 kHz mono (Whisper's input format).
 *
 * Endpointing is a simple energy gate for the MVP: stop after [silenceMs] of quiet
 * once speech has started. Silero VAD replaces this later.
 */
class SpeechRecorder(
    private val silenceMs: Int = 1000,
    private val noSpeechTimeoutMs: Int = 4000,
    private val maxMs: Int = 12_000,
    private val speechRms: Float = 0.015f,
) {

    /** Returns PCM floats in [-1, 1], or an empty array if nobody spoke. */
    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO.
    suspend fun recordUtterance(): FloatArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING,
            maxOf(minBuf, FRAME * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }

        val out = FloatArrayBuilder(SAMPLE_RATE * maxMs / 1000)
        val frame = ShortArray(FRAME)
        val frameMs = FRAME * 1000 / SAMPLE_RATE
        var elapsed = 0
        var quietMs = 0
        var heardSpeech = false

        record.startRecording()
        try {
            while (elapsed < maxMs) {
                ensureActive()
                val n = record.read(frame, 0, FRAME)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val v = frame[i] / 32768f
                    out.add(v)
                    sum += v * v
                }
                elapsed += frameMs
                val rms = sqrt(sum / n).toFloat()

                if (rms >= speechRms) {
                    heardSpeech = true
                    quietMs = 0
                } else {
                    quietMs += frameMs
                }
                if (heardSpeech && quietMs >= silenceMs) break
                if (!heardSpeech && elapsed >= noSpeechTimeoutMs) return@withContext FloatArray(0)
            }
        } finally {
            record.stop()
            record.release()
        }
        out.toArray()
    }

    private class FloatArrayBuilder(capacity: Int) {
        private var data = FloatArray(capacity)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray() = data.copyOf(size)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME = 480 // 30 ms
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
