package com.hearsight.navcore

import kotlin.math.exp
import kotlin.math.min

/** What a surface or object is, as far as walking is concerned. */
enum class Surface {
    UNKNOWN,
    /** Sidewalk, path, indoor floor: where a pedestrian should walk. */
    WALKWAY,
    ROAD,
    /** Grass, soil, gravel: walkable but not preferred. */
    TERRAIN,
    PERSON,
    VEHICLE,
    /** Buildings, walls, fences, poles, signs, vegetation. */
    STRUCTURE,
    SKY;

    val code: Byte get() = ordinal.toByte()

    companion object {
        private val all = values()
        fun of(code: Byte): Surface = all.getOrElse(code.toInt()) { UNKNOWN }
        val COUNT = all.size
    }
}

/**
 * Per-pixel labels for one camera frame, at any resolution: [labelAt] scales depth-image
 * pixel coordinates to this image (the colour stream is aligned to depth, so only the
 * resolution differs).
 */
class SemanticImage(val width: Int, val height: Int, val labels: ByteArray) {
    fun labelAt(u: Int, v: Int, imgW: Int, imgH: Int): Surface {
        val x = min(width - 1, u * width / imgW)
        val y = min(height - 1, v * height / imgH)
        return Surface.of(labels[y * width + x])
    }
}

/** Cityscapes train-ID classes (19) mapped onto walking-relevant [Surface]s. */
object Cityscapes {
    val CLASSES = listOf(
        "road", "sidewalk", "building", "wall", "fence", "pole", "traffic light", "traffic sign",
        "vegetation", "terrain", "sky", "person", "rider", "car", "truck", "bus", "train",
        "motorcycle", "bicycle",
    )

    val TO_SURFACE: Array<Surface> = arrayOf(
        Surface.ROAD, Surface.WALKWAY, Surface.STRUCTURE, Surface.STRUCTURE, Surface.STRUCTURE,
        Surface.STRUCTURE, Surface.STRUCTURE, Surface.STRUCTURE, Surface.STRUCTURE, Surface.TERRAIN,
        Surface.SKY, Surface.PERSON, Surface.PERSON, Surface.VEHICLE, Surface.VEHICLE, Surface.VEHICLE,
        Surface.VEHICLE, Surface.VEHICLE, Surface.VEHICLE,
    )
}

/** Pre- and post-processing for a semantic segmentation network (SegFormer conventions). */
object SegmentationCodec {
    /** ImageNet normalisation used by SegFormer. */
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * RGB bytes (w x h, interleaved) -> normalised NCHW float tensor of [inW] x [inH],
     * resized with nearest-neighbour sampling.
     */
    fun preprocess(rgb: ByteArray, w: Int, h: Int, inW: Int, inH: Int): FloatArray {
        val out = FloatArray(3 * inW * inH)
        val plane = inW * inH
        for (y in 0 until inH) {
            val sy = min(h - 1, (y * h) / inH)
            for (x in 0 until inW) {
                val sx = min(w - 1, (x * w) / inW)
                val s = 3 * (sy * w + sx)
                val d = y * inW + x
                for (c in 0 until 3) {
                    out[c * plane + d] = ((rgb[s + c].toInt() and 0xFF) / 255f - MEAN[c]) / STD[c]
                }
            }
        }
        return out
    }

    /**
     * Logits [classes, h, w] (batch 1, NCHW) -> labels. Pixels whose softmax confidence is
     * below [minConfidence] become [Surface.UNKNOWN].
     */
    fun decode(
        logits: FloatArray,
        classes: Int,
        h: Int,
        w: Int,
        toSurface: Array<Surface> = Cityscapes.TO_SURFACE,
        minConfidence: Float = 0.5f,
    ): SemanticImage {
        val plane = h * w
        require(logits.size >= classes * plane) { "logits too small: ${logits.size} < ${classes * plane}" }
        val labels = ByteArray(plane)
        for (i in 0 until plane) {
            var best = 0
            var bestV = logits[i]
            for (c in 1 until classes) {
                val v = logits[c * plane + i]
                if (v > bestV) { bestV = v; best = c }
            }
            var sum = 0f
            for (c in 0 until classes) sum += exp(logits[c * plane + i] - bestV)
            val conf = 1f / sum
            labels[i] = if (conf < minConfidence) Surface.UNKNOWN.code else toSurface.getOrElse(best) { Surface.UNKNOWN }.code
        }
        return SemanticImage(w, h, labels)
    }
}

/** Ground types count as such only on floor-level points; objects only above the floor. */
internal fun Surface.isGround() = this == Surface.WALKWAY || this == Surface.ROAD || this == Surface.TERRAIN
internal fun Surface.isObject() = this == Surface.PERSON || this == Surface.VEHICLE || this == Surface.STRUCTURE
