#ifndef HSV2_VO_H
#define HSV2_VO_H

#include <cstdint>
#include <random>
#include <vector>

/*
 * RGB-D visual odometry (Phase 3): the user's own motion from the colour
 * camera + depth, CPU only.
 *
 *  1. Grey image at half resolution, 3-level pyramid.
 *  2. Shi-Tomasi corners on a grid; pyramidal Lucas-Kanade tracking with a
 *     forward-backward check.
 *  3. Each tracked point gets a 3D position from depth (previous and current
 *     frame); a RANSAC rigid fit gives the camera motion. Points on moving
 *     objects (people) disagree with the dominant motion and are rejected.
 *
 * Camera frame: x right, y down, z forward (metres). The first frame defines
 * the world frame.
 */
struct VoCamera {
    float fx, fy, cx, cy;   // full-resolution colour intrinsics (pixels)
    int width, height;      // full resolution
};

// Depth (metres) at full-resolution colour pixel (u, v); <= 0 if unknown.
using VoDepthFn = float (*)(void* ctx, float u, float v);

struct VoResult {
    bool ok{false};          // motion estimated this frame
    int tracked{0};          // points tracked from the previous frame
    int withDepth{0};        // of which have depth in both frames
    int inliers{0};          // agreeing with the camera motion
    float vel[3]{0, 0, 0};   // camera velocity, current camera axes (m/s), smoothed
    float speed{0};          // |vel|
    float angRate{0};        // rotation rate (rad/s), smoothed
    double ms{0};
};

// Depth registered to the colour image at half resolution (z-buffer):
// every [step]-th depth pixel is back-projected, moved into the colour camera
// ([rotColMajor] / [trans] = depth->colour extrinsics, librealsense layout)
// and projected. out[(v/2)*(cw/2) + u/2] = nearest depth (m), 0 = none.
struct DepthToColor {
    float dfx, dfy, dcx, dcy; int dw, dh;   // depth intrinsics
    float cfx, cfy, ccx, ccy; int cw, ch;   // colour intrinsics
    float rot[9];                           // column-major
    float trans[3];
};
void registerDepthHalf(const uint16_t* depth, float units, const DepthToColor& c, int step,
                       std::vector<float>& out);
// Lookup with a 3x3 nearest-valid search; (u, v) full-res colour pixels.
float depthHalfAt(const std::vector<float>& map, int cw, int ch, float u, float v);

class VisualOdometry {
public:
    void setCamera(const VoCamera& cam);
    // [rgba]: full-resolution RGBA8, row stride width*4.
    VoResult process(const uint8_t* rgba, double tSec, VoDepthFn depth, void* ctx);
    void reset();

    // Current camera pose in the world frame: p_world = R * p_cam + t.
    const float* poseR() const { return Rw_; }
    const float* poseT() const { return tw_; }

    // Tunables.
    int maxPoints{250};
    int gridCols{16}, gridRows{12};
    float minEigen{0.0008f};     // corner strength (per-pixel normalised)
    float fbMaxErr{1.0f};        // forward-backward check (half-res pixels)
    int ransacIters{80};
    int minInliers{12};
    float velSmooth{0.5f};       // EMA weight of the new measurement

private:
    struct Level { int w, h; std::vector<float> img; };
    struct Track { float x, y; float P[3]; bool hasP; };

    void buildPyramid(const uint8_t* rgba, std::vector<Level>& pyr) const;
    bool trackPoint(const std::vector<Level>& from, const std::vector<Level>& to,
                    float x, float y, float& ox, float& oy) const;
    void detect(const std::vector<Level>& pyr);
    bool backProject(float hx, float hy, VoDepthFn depth, void* ctx, float P[3]) const;

    VoCamera cam_{};
    std::vector<Level> prev_, cur_;
    std::vector<Track> tracks_;
    bool havePrev_{false};
    double prevT_{0};
    float Rw_[9]{1, 0, 0, 0, 1, 0, 0, 0, 1};
    float tw_[3]{0, 0, 0};
    float vel_[3]{0, 0, 0};
    float angRate_{0};
    std::mt19937 rng_{42};
};

#endif  // HSV2_VO_H
