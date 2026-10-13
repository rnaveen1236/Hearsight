package com.hearsight.navcore

/** Tunable parameters for the perception / planning pipeline. Units: metres, degrees, seconds. */
data class Params(
    // --- point cloud ---
    val minDepth: Float = 0.25f,
    val maxDepth: Float = 6.0f,
    val stride: Int = 2,                 // pixel subsampling of the depth image

    // --- ground plane (RANSAC) ---
    val ransacIters: Int = 150,
    val ransacSample: Int = 4000,
    val ransacThresh: Double = 0.04,
    val maxTiltDeg: Double = 55.0,       // max angle between floor normal and camera "up"
    val minCameraHeight: Double = 0.4,
    val maxCameraHeight: Double = 2.4,
    val minInlierRatio: Double = 0.08,
    val nearQuantile: Double = 0.35,     // fit the floor on the nearest 35% of points first
    val planeBlend: Double = 0.4,        // low-pass filter between frames
    val planeJumpDeg: Double = 12.0,     // bigger changes than this are a "jump"...
    val planeJumpHeight: Double = 0.2,
    val planeJumpFrames: Int = 5,        // ...and are only accepted after persisting this many frames

    // --- IMU gravity (D435i / D455) ---
    val imuMaxDeviationDeg: Double = 5.0, // floor fit may correct the IMU "up" by at most this much
    val imuMinInlierRatio: Double = 0.03, // tilt is known, so less visible floor is enough
    val gravityMinNorm: Double = 7.8,    // accelerometer samples outside this range (m/s^2)
    val gravityMaxNorm: Double = 11.8,   // are walking jolts, not gravity: ignored
    val gravityAlpha: Double = 0.05,     // low-pass filter on the gravity direction
    val gravityMinSamples: Int = 10,

    // --- occupancy grid (ground-aligned, egocentric) ---
    val cell: Float = 0.10f,
    val rangeFwd: Float = 5.0f,
    val halfWidth: Float = 2.5f,
    val floorTol: Float = 0.06f,         // |height| below this = walkable floor
    val obstacleMinH: Float = 0.10f,     // higher than this above floor = obstacle (curb up, box, person)
    val obstacleMaxH: Float = 2.0f,      // ignore ceilings / things above head height
    val dropMinH: Float = 0.10f,         // lower than this below floor = drop-off (step down, curb, hole)
    val noiseK: Float = 0.012f,          // depth noise grows ~z^2: thresholds += noiseK * dist^2
    val minPoints: Int = 3,              // points needed to call a cell obstacle / drop-off

    // --- costs ---
    val bodyRadius: Float = 0.30f,       // hard keep-out around obstacles
    val inflationScale: Float = 0.25f,   // soft cost decay beyond body radius
    val inflationWeight: Float = 3.0f,
    val unknownCost: Float = 3.0f,

    // --- planning / steering ---
    val lateralPenalty: Double = 0.6,    // goal score = fwd - lateralPenalty*|lat| - costWeight*pathCost
    val costWeight: Double = 0.02,
    val goalCommitment: Double = 0.4,    // score penalty per metre the goal moves sideways between frames
    val minGoalDist: Double = 2.0,       // no route at least this long -> STOP
    val lookahead: Double = 1.2,         // pure-pursuit lookahead distance
    val smoothWindow: Int = 5,

    // --- guidance ---
    val deadbandDeg: Double = 8.0,       // within this = "straight"
    val hysteresisDeg: Double = 4.0,
    val sharpTurnDeg: Double = 40.0,
    val warnDist: Double = 1.8,          // warn about hazards in the walking corridor this close
    val corridorHalfWidth: Double = 0.35,
    val speakInterval: Double = 2.5,     // seconds between repeats of the same spoken message
    val angleEma: Double = 0.5,          // steering angle smoothing (1 = none)
)
