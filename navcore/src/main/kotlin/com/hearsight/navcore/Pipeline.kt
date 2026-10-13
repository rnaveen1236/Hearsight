package com.hearsight.navcore

/** Anything that produces depth frames: the RealSense on the phone, or [SimCamera]. */
interface DepthSource {
    fun start()
    /** Blocks until the next frame; null if none arrived in time. */
    fun read(): DepthImage?
    fun stop()
}

class StepResult(
    val plane: Plane?,
    val grid: Grid?,
    val plan: Plan,
    val cue: Cue,
    val millis: Double,
)

/** One perception -> planning -> guidance step per depth frame. */
class Pipeline(val params: Params = Params()) {
    private val tracker = PlaneTracker(params)
    private val guidance = Guidance(params)
    private var lastGoal: Pt? = null

    fun step(img: DepthImage): StepResult {
        val t0 = System.nanoTime()
        val pts = deproject(img, params)
        val plane = tracker.update(fitGroundPlane(pts, params, tracker.plane))
        val grid = plane?.let { buildGrid(pts, it, params) }
        val pl = grid?.let { plan(it, params, lastGoal) } ?: Plan()
        lastGoal = pl.path.lastOrNull()
        val cue = guidance.update(pl, grid)
        return StepResult(plane, grid, pl, cue, (System.nanoTime() - t0) / 1e6)
    }
}
