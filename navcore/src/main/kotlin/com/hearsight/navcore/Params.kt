package com.hearsight.navcore

/**
 * Tunable parameters for the perception / planning pipeline. Units: metres, degrees, seconds.
 *
 * Defaults are tuned for the Intel RealSense D455 (95 mm stereo baseline, ideal range
 * 0.6-6 m, 87 x 58 degree depth FOV). For a D435 / D435i (50 mm baseline, about twice the
 * depth noise at a given distance) use noiseK = 0.012f.
 */
data class Params(
    // --- point cloud ---
    val minDepth: Float = 0.4f,          // D455 minimum depth at 424x240; closer readings are noise
    val maxDepth: Float = 6.0f,          // D455 ideal range ends at 6 m
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

    // --- depth-inertial odometry ---
    val odomStride: Int = 2,             // vertex-map resolution: depth image / stride
    val odomSourceStep: Int = 2,         // use every n-th vertex as an ICP source point
    val odomMaxDepth: Float = 5.0f,      // ignore noisier far points for alignment
    val odomIters: Int = 15,
    val odomGateStart: Double = 0.25,    // max point-to-point match distance, shrinking per iteration
    val odomGateMin: Double = 0.05,
    val odomHuber: Double = 0.02,        // robust-loss threshold on point-to-plane residuals
    val odomNormalDeg: Double = 30.0,    // max normal disagreement for a match
    val odomDegenerateRatio: Double = 0.03, // eigenvalue / inliers below this = unobservable direction
    val odomDegenerateRatioNoGyro: Double = 0.01, // lower without a gyro: nothing else predicts rotation
    val odomMinInliers: Int = 150,
    val odomMinInlierRatio: Double = 0.2,
    val odomMaxRmse: Double = 0.04,
    val odomGyroWeight: Double = 0.2,    // strength of the gyro rotation prior, per inlier
    val odomMaxStep: Double = 0.12,      // m per frame (3.6 m/s at 30 fps); more = failed alignment
    val odomMaxGyroDisagreeDeg: Double = 10.0,
    val odomKeyframeDist: Double = 0.30,
    val odomKeyframeDeg: Double = 12.0,
    val trailEvery: Int = 3,             // keep every n-th pose for the trail on the map
    val trailLength: Int = 40,

    // --- 3D map fused over time (world frame, follows the user) ---
    val fuseMap: Boolean = true,         // false = plan from the current frame only
    val mapSize: Float = 8.0f,           // window width and depth, metres
    val mapVoxel: Float = 0.10f,
    val mapZMin: Float = -3.0f,          // vertical range relative to the camera's start height
    val mapZMax: Float = 1.0f,
    val mapRecenter: Double = 1.0,       // shift the window once the user is this far off-centre
    val mapPointStep: Int = 2,           // integrate every n-th point
    val mapHit: Float = 0.7f,            // log-odds per observation
    val mapMiss: Float = 0.4f,           // log-odds per ray passing through (once per frame)
    val mapMinLogOdds: Float = -2.0f,
    val mapMaxLogOdds: Float = 3.5f,
    val mapOccupied: Float = 1.2f,       // obstacle / drop-off threshold (about 2 observations)
    val mapSeen: Float = 0.3f,           // floor threshold
    val mapDecay: Float = 0.99923f,      // per frame: half-life of about 30 s at 30 fps
    val mapClearMargin: Double = 0.05,   // a voxel is free if the camera sees this far (+2% of depth) past it
    val mapNoReturnDist: Double = 3.0,   // pixels with no depth clear voxels nearer than this (slowly)

    // --- occupancy grid (ground-aligned, egocentric) ---
    val cell: Float = 0.10f,
    val rangeFwd: Float = 5.0f,
    val halfWidth: Float = 2.5f,
    val floorTol: Float = 0.06f,         // |height| below this = walkable floor
    val obstacleMinH: Float = 0.10f,     // higher than this above floor = obstacle (curb up, box, person)
    val obstacleMaxH: Float = 2.0f,      // ignore ceilings / things above head height
    val dropMinH: Float = 0.10f,         // lower than this below floor = drop-off (step down, curb, hole)
    val noiseK: Float = 0.006f,          // depth noise grows ~z^2: thresholds += noiseK * dist^2 (D455)
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
