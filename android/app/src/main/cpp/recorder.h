#ifndef HSV2_RECORDER_H
#define HSV2_RECORDER_H

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "vo.h"

/*
 * Walk recorder for replay on the PC (record once, re-run fixes offline).
 *
 * File: RecHeader, then one RecFrame + payload per frame:
 *   gray  : grayW*grayH bytes   (half-res grey, exactly what VO uses)
 *   depth : depW*depH uint16    (every 2nd depth pixel = what the depth
 *                                registration at step 2 reads)
 * All little-endian, written by a background thread; frames are dropped (and
 * counted) if the disk can't keep up, never blocking the caller.
 */
#pragma pack(push, 1)
struct RecHeader {
    char magic[8];          // "HSVREC2\0" ("HSVREC1": no IMU fields in RecFrame)
    int32_t grayW, grayH;   // half-res colour
    int32_t depW, depH;     // depth subsampled by 2
    float depthUnits;
    VoCamera cam;           // full-res colour intrinsics
    DepthToColor reg;       // full-res depth/colour intrinsics + extrinsics
};
struct RecFrame {
    double t;               // capture time (s)
    // Phone results for comparison.
    uint8_t voOk;
    float vel[3];
    float angRate;
    int32_t inliers, tracked, withDepth;
    float pose[3];
    // Phone IMU at the frame time (version 2).
    uint8_t imuValid;
    float phoneUp[3];       // unit "up" in phone axes
    float gyro[3];          // rad/s, phone axes
};
#pragma pack(pop)


class Recorder {
public:
    ~Recorder() { stop(); }
    // Opens [path] and writes the header. False on error.
    bool start(const std::string& path, const RecHeader& h);
    void stop();
    bool active() const { return active_; }
    // Copies the frame into the queue (depth: full-res; subsampled here).
    void push(double t, const uint8_t* gray, const uint16_t* depthFull, const VoResult& r, const float pose[3],
              const RecImu& imu);

    int frames() const { return frames_; }
    int dropped() const { return dropped_; }
    double megabytes() const { return bytes_ / 1e6; }
    const std::string& path() const { return path_; }

private:
    struct Item { RecFrame f; std::vector<uint8_t> gray; std::vector<uint16_t> depth; };
    void loop();

    RecHeader h_{};
    std::FILE* file_{nullptr};
    std::string path_;
    std::thread thread_;
    std::mutex m_;
    std::condition_variable cv_;
    std::deque<Item> q_;
    bool quit_{false};
    std::atomic<bool> active_{false};
    std::atomic<int> frames_{0}, dropped_{0};
    std::atomic<long long> bytes_{0};
    static constexpr size_t kMaxQueue = 45;   // ~1.5 s
};

// Reader for the PC replay tool.
struct RecReader {
    std::FILE* f{nullptr};
    RecHeader h{};
    int version{0};
    bool open(const char* path);
    // Next frame; false at end of file.
    bool next(RecFrame& fr, std::vector<uint8_t>& gray, std::vector<uint16_t>& depth);
    ~RecReader() { if (f) std::fclose(f); }
};

#endif  // HSV2_RECORDER_H
