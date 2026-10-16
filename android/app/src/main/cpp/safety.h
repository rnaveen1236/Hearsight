#ifndef HSV2_SAFETY_H
#define HSV2_SAFETY_H

#include <cstdint>
#include <random>
#include <vector>

/*
 * Layer 1 reactive safety: works from the live depth frame only (plus an
 * optional gravity vector), independent of the map, tracking and the model.
 *
 * Depth camera frame: x right, y down, z forward (metres).
 *  1. Sample the depth image on a grid and back-project the points.
 *  2. Fit the floor plane with RANSAC (normal near "up": gravity if given,
 *     otherwise the camera's -y axis).
 *  3. Height of every point above the floor.
 *  4. Walking corridor on the floor straight ahead of the camera.
 *  5. Obstacles in the corridor at body height and head height.
 *  6. Drop-offs (floor falling away) and unknown floor ahead.
 *  7. STOP / CAUTION with frame confirmation and a short hold.
 */
enum class SafetyState { Clear = 0, Caution = 1, Stop = 2 };
enum class SafetyReason { None = 0, Obstacle, HeadHeight, DropOff, UnknownFloor, NoFloor };

struct SafetyResult {
    SafetyState state{SafetyState::Clear};
    SafetyReason reason{SafetyReason::None};
    float distance{-1};        // metres along the corridor to the hazard

    // Diagnostics.
    bool floorFound{false};
    float cameraHeight{0};     // metres above the floor
    float cameraPitchDeg{0};   // + = looking down
    float obstacleDist{-1};
    float headDist{-1};
    float dropDist{-1};
    float unknownDist{-1};
    int points{0};
    double ms{0};
};

struct DepthIntrinsics {
    float fx, fy, ppx, ppy;
    int width, height;
};

class SafetyLayer {
public:
    // [depth] Z16, [units] metres per unit. [tSec] frame time.
    SafetyResult process(const uint16_t* depth, const DepthIntrinsics& in,
                         float units, double tSec);

    // Gravity "down" direction in the depth camera frame (unit vector).
    // Optional: improves the floor fit when an IMU is available.
    void setGravity(float gx, float gy, float gz);
    void clearGravity() { haveGravity_ = false; }

    void reset();

    // ---- Tunables (metres / seconds) ----
    int sampleStep{4};
    float minRange{0.3f}, maxRange{4.5f};
    float corridorHalfWidth{0.45f};
    float corridorNear{0.3f}, corridorFar{3.0f};
    float bodyMinH{0.10f}, bodyMaxH{1.40f}, headMaxH{2.00f};
    float stopDist{1.2f}, cautionDist{2.5f};
    float dropDepth{0.12f};               // below floor = drop
    float dropStopDist{1.5f}, dropCautionDist{2.5f};
    float unknownCautionDist{1.2f};
    float floorInlier{0.04f};
    float maxFloorTiltDeg{35.0f};         // allowed normal deviation from "up"
    int ransacIters{120};
    // Floor continuity: a tracked floor may change height by at most this
    // much per frame, so a lower level ahead is a drop, not a new floor.
    float floorTrackTolerance{0.04f};
    float floorTrackTiltDeg{6.0f};
    // Learned camera height above the floor (eye height) and tolerance. On
    // flat ground the height only changes by head bob (a few cm); a head
    // pitch rotates the plane but keeps the height, while a wrong fit that
    // mixes two levels changes it.
    float heightTolerance{0.05f};
    double holdSec{0.5};
    double floorMemorySec{1.0};

private:
    struct P { float x, y, z; int u, v; };
    struct Plane { float nx, ny, nz, d; bool valid{false}; };  // n·p + d = 0, n = up

    bool fitFloor(const std::vector<P>& pts, Plane& out);
    float minPointsAt(float along) const;

    std::vector<P> pts_;
    // Camera-height learning over the first stable frames.
    std::vector<float> heightSamples_;
    float learnedHeight_{-1};
    std::mt19937 rng_{1234};
    Plane floor_;
    double floorTime_{-1e9};
    bool haveGravity_{false};
    float gx_{0}, gy_{1}, gz_{0};

    // Confirmation / hold.
    int history_[3]{0, 0, 0};
    int histIdx_{0};
    SafetyResult held_;
    double heldUntil_{-1e9};
};

#endif  // HSV2_SAFETY_H
