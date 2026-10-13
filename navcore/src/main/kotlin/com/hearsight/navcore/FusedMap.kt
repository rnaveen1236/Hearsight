package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 3D map fused over time, in the odometry's world frame (X right, Y forward, Z up), in a
 * window that follows the user.
 *
 * - **Obstacles** live in a 3D voxel grid of log-odds: overhangs, table edges and head-height
 *   obstacles keep their true height.
 * - **Floor and drop-offs** are 2D column layers (log-odds per column).
 * - **Free space (projective):** every occupied voxel in view is projected into the current
 *   depth image. If the camera now sees past it, its occupancy drops, so a person who walks
 *   away is erased. Pixels with no depth return within [Params.mapNoReturnDist] (open space
 *   beyond range) clear it more slowly. Voxels out of view, or hidden behind something
 *   nearer, keep their state: obstacles beside and behind the user are remembered. This
 *   touches only occupied voxels, far cheaper than tracing every ray.
 * - **No ghost heads:** when the lower part of an occupied column is seen to be empty (a
 *   person walked off), its out-of-view upper part is cleared with it. A shelf above the
 *   view is kept: nothing was ever occupied below it.
 * - **Semantics:** labelled frames add per-column votes for the ground type (sidewalk, road,
 *   grass) from floor-level points, and for the object type (person, vehicle, structure) from
 *   points above the floor. Ground votes persist like the floor; object votes fade quickly so
 *   they follow moving people.
 * - **Forgetting:** all evidence decays slowly ([Params.mapDecay]) to forgive odometry drift.
 *
 * [toGrid] samples the map into the usual egocentric planning [Grid].
 */
class FusedMap(private val p: Params = Params()) {
    private val res = p.mapVoxel.toDouble()
    private val nxy = (p.mapSize / p.mapVoxel).toInt()
    private val nz = ((p.mapZMax - p.mapZMin) / p.mapVoxel).toInt()
    private val zMin = p.mapZMin.toDouble()

    private var originX = 0.0   // world x of the window's cell (0, 0) corner
    private var originY = 0.0
    private var occ = FloatArray(nxy * nxy * nz)
    private var floorEv = FloatArray(nxy * nxy)
    private var dropEv = FloatArray(nxy * nxy)
    private var groundEv = FloatArray(nxy * nxy * Surface.COUNT)
    private var objectEv = FloatArray(nxy * nxy * Surface.COUNT)
    var initialized = false
        private set

    fun clear() {
        occ.fill(0f); floorEv.fill(0f); dropEv.fill(0f); groundEv.fill(0f); objectEv.fill(0f)
        initialized = false
    }

    /**
     * Adds one frame: [img] the depth image, [pts] its points in camera coordinates,
     * [pose] world <- camera, [plane] the current floor.
     */
    fun integrate(img: DepthImage, pts: PointCloud, pose: Rigid, plane: Plane) {
        val cx = pose.t[0]; val cy = pose.t[1]
        if (!initialized) {
            originX = cx - nxy * res / 2
            originY = cy - nxy * res / 2
            initialized = true
        } else {
            recenter(cx, cy)
        }
        clearFreeSpace(img, pose)
        if (p.mapDecay < 1f) {
            val k = p.mapDecay
            for (i in floorEv.indices) { floorEv[i] *= k; dropEv[i] *= k }
            for (i in groundEv.indices) groundEv[i] *= k
        }

        val (fa, _) = groundFrame(plane)
        for (i in 0 until pts.size step p.mapPointStep) {
            val x = pts.x(i); val y = pts.y(i); val z = pts.z(i)
            val h = plane.height(x, y, z)
            val fwd = fa[0] * x + fa[1] * y + fa[2] * z
            val noise = p.noiseK * fwd * fwd
            val w = pose.apply(x.toDouble(), y.toDouble(), z.toDouble())

            val col = column(w[0], w[1])
            if (col < 0) continue
            when {
                abs(h) < p.floorTol + noise -> {
                    floorEv[col] = min(floorEv[col] + p.mapHit, p.mapMaxLogOdds)
                    dropEv[col] = max(dropEv[col] - p.mapMiss, p.mapMinLogOdds)
                }
                h > p.obstacleMinH + noise && h < p.obstacleMaxH -> {
                    val v = voxel(w[0], w[1], w[2])
                    if (v >= 0) occ[v] = min(occ[v] + p.mapHit, p.mapMaxLogOdds)
                }
                h < -(p.dropMinH + noise) -> {
                    dropEv[col] = min(dropEv[col] + p.mapHit, p.mapMaxLogOdds)
                    floorEv[col] = max(floorEv[col] - p.mapMiss, p.mapMinLogOdds)
                }
            }
        }
    }

    /**
     * Adds per-pixel labels [sem] for an earlier frame: [pts], [pose] and [plane] must be that
     * frame's, so labels land on exactly the geometry they were computed for.
     */
    fun integrateSemantics(pts: PointCloud, sem: SemanticImage, pose: Rigid, plane: Plane) {
        if (!initialized) return
        val k = p.objectLabelDecay
        for (i in objectEv.indices) objectEv[i] *= k
        val (fa, _) = groundFrame(plane)
        val nc = Surface.COUNT
        for (i in 0 until pts.size step p.mapPointStep) {
            val label = pts.label(i, sem)
            if (label == Surface.UNKNOWN || label == Surface.SKY) continue
            val x = pts.x(i); val y = pts.y(i); val z = pts.z(i)
            val h = plane.height(x, y, z)
            val fwd = fa[0] * x + fa[1] * y + fa[2] * z
            val noise = p.noiseK * fwd * fwd
            val w = pose.apply(x.toDouble(), y.toDouble(), z.toDouble())
            val col = column(w[0], w[1])
            if (col < 0) continue
            if (abs(h) < p.floorTol + noise && label.isGround()) {
                groundEv[col * nc + label.ordinal] = min(groundEv[col * nc + label.ordinal] + 1f, p.semMaxEvidence)
            } else if (h > p.obstacleMinH + noise && h < p.obstacleMaxH && label.isObject()) {
                objectEv[col * nc + label.ordinal] = min(objectEv[col * nc + label.ordinal] + 1f, p.semMaxEvidence)
            }
        }
    }

    /**
     * Lowers the occupancy of voxels the camera can now see through, and decays all voxels.
     * Runs over the voxel array once; only voxels with positive evidence are projected.
     */
    private fun clearFreeSpace(img: DepthImage, pose: Rigid) {
        val camFromWorld = pose.inverse()
        val r = camFromWorld.r; val t = camFromWorld.t
        val layer = nxy * nxy
        val k = p.mapDecay
        lowestSeenThrough.fill(Int.MAX_VALUE)
        hiddenAbove.clear()
        for (v in occ.indices) {
            val o = occ[v]
            if (o <= 0f) {
                if (o < 0f) occ[v] = o * k
                continue
            }
            occ[v] = o * k
            // voxel centre in the camera frame
            val iz = v / layer; val col = v - iz * layer
            val iy = col / nxy; val ix = col - iy * nxy
            val wx = originX + (ix + 0.5) * res
            val wy = originY + (iy + 0.5) * res
            val wz = zMin + (iz + 0.5) * res
            val z = r[6] * wx + r[7] * wy + r[8] * wz + t[2]
            if (z < 0.3) continue
            val x = r[0] * wx + r[1] * wy + r[2] * wz + t[0]
            val y = r[3] * wx + r[4] * wy + r[5] * wz + t[1]
            val u = (img.fx * x / z + img.cx).toInt()
            val vv = (img.fy * y / z + img.cy).toInt()
            if (u < 0 || u >= img.width || vv < 0 || vv >= img.height) {
                hiddenAbove += v  // out of view: remember (unless its column turns out empty below)
                continue
            }
            val measured = img.depth[vv * img.width + u]
            val miss = when {
                measured > z + p.mapClearMargin + 0.02 * z -> p.mapMiss            // seen through it
                measured <= 0f && z < p.mapNoReturnDist -> p.mapMiss * 0.5f       // no return: open space
                else -> 0f                                                       // hit or hidden behind something
            }
            if (miss > 0f) {
                occ[v] = max(occ[v] - miss, p.mapMinLogOdds)
                if (iz < lowestSeenThrough[col]) lowestSeenThrough[col] = iz
            }
        }
        // Out-of-view voxels sitting above a part of their column that was just seen empty.
        for (i in 0 until hiddenAbove.size) {
            val v = hiddenAbove[i]
            val iz = v / layer; val col = v - iz * layer
            if (iz > lowestSeenThrough[col]) occ[v] = max(occ[v] - p.mapMiss, p.mapMinLogOdds)
        }
    }

    private val lowestSeenThrough = IntArray(nxy * nxy)
    private val hiddenAbove = IntList()

    /** Samples the map into the egocentric planning grid of the current camera [pose] and [plane]. */
    fun toGrid(pose: Rigid, plane: Plane): Grid {
        val rows = Math.round(p.rangeFwd / p.cell)
        val cols = Math.round(2 * p.halfWidth / p.cell)
        val n = rows * cols
        val obstacle = BooleanArray(n)
        val dropoff = BooleanArray(n)
        val floorMask = BooleanArray(n)
        val unknown = BooleanArray(n)
        val ground = ByteArray(n)
        val objectType = ByteArray(n)
        val (fa, ra) = groundFrame(plane)
        // the point on the floor right below the camera
        val footX = -plane.d * plane.nx; val footY = -plane.d * plane.ny; val footZ = -plane.d * plane.nz

        for (r in 0 until rows) for (c in 0 until cols) {
            val f = (r + 0.5) * p.cell
            val l = (c + 0.5) * p.cell - p.halfWidth
            val w = pose.apply(footX + f * fa[0] + l * ra[0], footY + f * fa[1] + l * ra[1], footZ + f * fa[2] + l * ra[2])
            val col = column(w[0], w[1])
            val k = r * cols + c
            if (col < 0) { unknown[k] = true; continue }
            val obs = columnOccupied(col)
            val fl = floorEv[col]; val dr = dropEv[col]
            obstacle[k] = obs
            dropoff[k] = !obs && dr > p.mapOccupied && dr > fl
            floorMask[k] = !obs && !dropoff[k] && fl > p.mapSeen
            unknown[k] = !obs && fl <= p.mapSeen && dr <= p.mapSeen
            ground[k] = strongest(groundEv, col) { it.isGround() }
            if (obs) objectType[k] = strongest(objectEv, col) { it.isObject() }
        }
        return finishGrid(rows, cols, p, obstacle, dropoff, floorMask, unknown, ground, objectType)
    }

    private fun strongest(ev: FloatArray, col: Int, filter: (Surface) -> Boolean): Byte {
        var best = Surface.UNKNOWN
        var bestV = p.semMinEvidence
        for (s in Surface.values()) if (filter(s)) {
            val v = ev[col * Surface.COUNT + s.ordinal]
            if (v >= bestV) { bestV = v; best = s }
        }
        return best.code
    }

    /** Number of occupied voxels, for diagnostics. */
    fun occupiedVoxels() = occ.count { it > p.mapOccupied }

    private fun columnOccupied(col: Int): Boolean {
        val layer = nxy * nxy
        var v = col
        for (z in 0 until nz) {
            if (occ[v] > p.mapOccupied) return true
            v += layer
        }
        return false
    }

    private fun column(x: Double, y: Double): Int {
        val ix = floor((x - originX) / res).toInt()
        val iy = floor((y - originY) / res).toInt()
        if (ix < 0 || ix >= nxy || iy < 0 || iy >= nxy) return -1
        return iy * nxy + ix
    }

    private fun voxel(x: Double, y: Double, z: Double): Int {
        val col = column(x, y)
        if (col < 0) return -1
        val iz = floor((z - zMin) / res).toInt()
        if (iz < 0 || iz >= nz) return -1
        return iz * nxy * nxy + col
    }

    /** Shift the window (by whole cells) when the user gets close to its edge. */
    private fun recenter(x: Double, y: Double) {
        val half = nxy * res / 2
        val sx = floor((x - (originX + half)) / res).toInt()
        val sy = floor((y - (originY + half)) / res).toInt()
        if (abs(sx) * res < p.mapRecenter && abs(sy) * res < p.mapRecenter) return

        val layer = nxy * nxy
        val newOcc = FloatArray(occ.size)
        val newFloor = FloatArray(layer)
        val newDrop = FloatArray(layer)
        val nc = Surface.COUNT
        val newGround = FloatArray(layer * nc)
        val newObject = FloatArray(layer * nc)
        for (iy in 0 until nxy) {
            val oy = iy + sy
            if (oy !in 0 until nxy) continue
            for (ix in 0 until nxy) {
                val ox = ix + sx
                if (ox !in 0 until nxy) continue
                val dst = iy * nxy + ix
                val src = oy * nxy + ox
                newFloor[dst] = floorEv[src]
                newDrop[dst] = dropEv[src]
                for (c in 0 until nc) {
                    newGround[dst * nc + c] = groundEv[src * nc + c]
                    newObject[dst * nc + c] = objectEv[src * nc + c]
                }
                for (z in 0 until nz) {
                    newOcc[z * layer + dst] = occ[z * layer + src]
                }
            }
        }
        occ = newOcc; floorEv = newFloor; dropEv = newDrop; groundEv = newGround; objectEv = newObject
        originX += sx * res
        originY += sy * res
    }
}

/** Minimal growable int list (avoids boxing in the per-frame loop). */
internal class IntList {
    private var a = IntArray(256)
    var size = 0
        private set
    operator fun plusAssign(x: Int) {
        if (size == a.size) a = a.copyOf(size * 2)
        a[size++] = x
    }
    operator fun get(i: Int) = a[i]
    fun clear() { size = 0 }
}
