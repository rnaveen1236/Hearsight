package com.hearsight.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.hearsight.navcore.SegmentationCodec
import com.hearsight.navcore.SemanticImage
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * Semantic segmentation with ONNX Runtime: SegFormer-B0 (Cityscapes) exported by
 * scripts/export_segmentation_model.py to assets/segmentation.onnx.
 * Input: RGB colour aligned to depth. Output: per-pixel [com.hearsight.navcore.Surface] labels.
 */
class Segmenter private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val inputName: String,
    val inputWidth: Int,
    val inputHeight: Int,
) : Closeable {
    companion object {
        const val ASSET = "segmentation.onnx"
        private const val TAG = "Segmenter"

        /** Loads the model from assets, or returns null if it isn't there or can't be loaded. */
        fun load(context: Context): Segmenter? {
            val bytes = try {
                context.assets.open(ASSET).use { it.readBytes() }
            } catch (_: Exception) {
                Log.i(TAG, "No $ASSET in assets: segmentation off (see scripts/export_segmentation_model.py)")
                return null
            }
            return try {
                fromBytes(bytes, useXnnpack = true)
            } catch (t: Throwable) {
                Log.e(TAG, "Can't load $ASSET", t)
                null
            }
        }

        /** Creates a session from an ONNX model; input size comes from the model ([1, 3, H, W]). */
        fun fromBytes(bytes: ByteArray, useXnnpack: Boolean): Segmenter {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)  // leave cores for depth processing
                if (useXnnpack) {
                    try { addXnnpack(mapOf("intra_op_num_threads" to "2")) } catch (t: Throwable) { Log.i(TAG, "XNNPACK unavailable", t) }
                }
            }
            val session = env.createSession(bytes, options)
            val name = session.inputNames.first()
            val shape = (session.inputInfo.getValue(name).info as TensorInfo).shape
            val h = shape.getOrNull(2)?.takeIf { it > 0 }?.toInt() ?: 256
            val w = shape.getOrNull(3)?.takeIf { it > 0 }?.toInt() ?: 448
            return Segmenter(env, session, name, w, h)
        }
    }

    /** Labels for an RGB image (interleaved bytes, [w] x [h]). */
    fun segment(rgb: ByteArray, w: Int, h: Int): SemanticImage {
        val input = SegmentationCodec.preprocess(rgb, w, h, inputWidth, inputHeight)
        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val out = result.get(0) as OnnxTensor
                val s = out.info.shape  // [1, classes, h/4, w/4]
                val buf = out.floatBuffer
                val logits = FloatArray(buf.remaining()).also { buf.get(it) }
                return SegmentationCodec.decode(logits, s[1].toInt(), s[2].toInt(), s[3].toInt())
            }
        }
    }

    override fun close() {
        session.close()
    }
}
