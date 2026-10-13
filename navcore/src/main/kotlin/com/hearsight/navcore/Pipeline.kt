package com.hearsight.navcore

/** Anything that produces depth frames: the RealSense on the phone, or [SimCamera]. */
interface DepthSource {
    fun start()
    /** Blocks until the next frame; null if none arrived in time. */
    fun read(): DepthImage?
    fun stop()
}

/** How the floor was found this frame. */
enum class FloorSource { IMU, VISION, NONE }

/** A past position of the user on the current map, with the way they were facing (+ = right). */
data class TrailPoint(val at: Pt, val headingDeg: Double)

class StepResult(
    val plane: Plane?,
    val grid: Grid?,
    val plan: Plan,
    val cue: Cue,
    val millis: Double,
    val floorSource: FloorSource = FloorSource.NONE,
    val odometry: OdometryResult? = null,
    /** Recent positions (oldest first) in the current ground frame: the red arrow trail. */
    val trail: List<TrailPoint> = emptyList(),
)

/** One perception -> planning -> guidance step per depth frame. */
class Pipeline(val params: Params = Params()) {
    private val tracker = PlaneTracker(params)
    private val guidance = Guidance(params)
    private var lastGoal: Pt? = null
    private val odometry = DepthInertialOdometry(params)
    val map = FusedMap(params)
    private val history = ArrayDeque<Rigid>()
    private var frame = 0L

    fun step(img: DepthImage): StepResult {
        val t0 = System.nanoTime()
        val pts = deproject(img, params)
        // With an IMU the floor's tilt is known and only its height is searched for;
        // otherwise (plain D435, or IMU not settled yet) fit the whole plane from depth.
        val imuPlane = img.gravityUp?.let { fitGroundPlaneWithGravity(pts, it, params) }
        val raw = imuPlane ?: fitGroundPlane(pts, params, tracker.plane)
        val plane = tracker.update(raw)
        val source = when {
            imuPlane != null -> FloorSource.IMU
            raw != null -> FloorSource.VISION
            else -> FloorSource.NONE
        }
        // Odometry first: the fused map needs this frame's pose.
        // Without an IMU, the floor normal defines "up" for the world frame (and the map's columns).
        val odo = odometry.update(img, plane?.let { doubleArrayOf(it.nx, it.ny, it.nz) })
        if (odo.tracking == Tracking.LOST) map.clear()  // pose jumped: the map would be misaligned
        val grid = plane?.let {
            if (params.fuseMap) {
                map.integrate(img, pts, odo.pose, it)
                map.toGrid(odo.pose, it)
            } else {
                buildGrid(pts, it, params)
            }
        }
        val pl = grid?.let { plan(it, params, lastGoal) } ?: Plan()
        lastGoal = pl.path.lastOrNull()
        val cue = guidance.update(pl, grid)

        if (frame++ % params.trailEvery == 0L) {
            history.addLast(odo.pose)
            while (history.size > params.trailLength) history.removeFirst()
        }
        val trail = plane?.let { trailOnMap(odo.pose, it) } ?: emptyList()
        return StepResult(plane, grid, pl, cue, (System.nanoTime() - t0) / 1e6, source, odo, trail)
    }

    /** Past camera positions expressed in the current camera's floor frame (fwd, lat). */
    private fun trailOnMap(current: Rigid, plane: Plane): List<TrailPoint> {
        val (fa, ra) = groundFrame(plane)
        val camFromWorld = current.inverse()
        return history.map { past ->
            val rel = camFromWorld.compose(past)  // current camera <- past camera
            val t = rel.t
            val f = M3.apply(rel.r, 0.0, 0.0, 1.0)
            val fwd = fa[0] * t[0] + fa[1] * t[1] + fa[2] * t[2]
            val lat = ra[0] * t[0] + ra[1] * t[1] + ra[2] * t[2]
            val hf = fa[0] * f[0] + fa[1] * f[1] + fa[2] * f[2]
            val hl = ra[0] * f[0] + ra[1] * f[1] + ra[2] * f[2]
            TrailPoint(Pt(fwd, lat), Math.toDegrees(Math.atan2(hl, hf)))
        }
    }
}
