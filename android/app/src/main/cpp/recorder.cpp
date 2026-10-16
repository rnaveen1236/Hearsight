#include "recorder.h"

#include <cstddef>
#include <cstring>

bool Recorder::start(const std::string& path, const RecHeader& h) {
    stop();
    file_ = std::fopen(path.c_str(), "wb");
    if (!file_) return false;
    // Large stdio buffer: one write per ~frame.
    std::setvbuf(file_, nullptr, _IOFBF, 1 << 20);
    h_ = h;
    std::memcpy(h_.magic, "HSVREC2", 8);
    std::fwrite(&h_, sizeof(h_), 1, file_);
    path_ = path;
    frames_ = 0;
    dropped_ = 0;
    bytes_ = sizeof(h_);
    quit_ = false;
    thread_ = std::thread(&Recorder::loop, this);
    active_ = true;
    return true;
}

void Recorder::stop() {
    if (!active_ && !thread_.joinable()) return;
    active_ = false;
    {
        std::lock_guard<std::mutex> lk(m_);
        quit_ = true;
    }
    cv_.notify_all();
    if (thread_.joinable()) thread_.join();
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
}

void Recorder::push(double t, const uint8_t* gray, const uint16_t* depthFull, const VoResult& r,
                    const float pose[3], const RecImu& imu) {
    if (!active_) return;
    Item it;
    it.f.t = t;
    it.f.voOk = r.ok ? 1 : 0;
    for (int i = 0; i < 3; ++i) { it.f.vel[i] = r.vel[i]; it.f.pose[i] = pose[i]; }
    it.f.angRate = r.angRate;
    it.f.inliers = r.inliers;
    it.f.tracked = r.tracked;
    it.f.withDepth = r.withDepth;
    it.f.imuValid = imu.valid ? 1 : 0;
    for (int i = 0; i < 3; ++i) { it.f.phoneUp[i] = imu.up[i]; it.f.gyro[i] = imu.gyro[i]; }
    it.gray.assign(gray, gray + static_cast<size_t>(h_.grayW) * h_.grayH);
    it.depth.resize(static_cast<size_t>(h_.depW) * h_.depH);
    const int dw = h_.reg.dw;
    for (int y = 0; y < h_.depH; ++y)
        for (int x = 0; x < h_.depW; ++x)
            it.depth[static_cast<size_t>(y) * h_.depW + x] = depthFull[static_cast<size_t>(2 * y) * dw + 2 * x];
    {
        std::lock_guard<std::mutex> lk(m_);
        if (q_.size() >= kMaxQueue) { dropped_++; return; }
        q_.push_back(std::move(it));
    }
    cv_.notify_one();
}

void Recorder::loop() {
    for (;;) {
        Item it;
        {
            std::unique_lock<std::mutex> lk(m_);
            cv_.wait(lk, [&] { return quit_ || !q_.empty(); });
            if (q_.empty()) break;   // quit and drained
            it = std::move(q_.front());
            q_.pop_front();
        }
        std::fwrite(&it.f, sizeof(it.f), 1, file_);
        std::fwrite(it.gray.data(), 1, it.gray.size(), file_);
        std::fwrite(it.depth.data(), 2, it.depth.size(), file_);
        bytes_ += sizeof(it.f) + it.gray.size() + 2 * it.depth.size();
        frames_++;
    }
}

bool RecReader::open(const char* path) {
    f = std::fopen(path, "rb");
    if (!f) return false;
    if (std::fread(&h, sizeof(h), 1, f) != 1) return false;
    if (std::memcmp(h.magic, "HSVREC2", 7) == 0) version = 2;
    else if (std::memcmp(h.magic, "HSVREC1", 7) == 0) version = 1;
    return version > 0;
}

bool RecReader::next(RecFrame& fr, std::vector<uint8_t>& gray, std::vector<uint16_t>& depth) {
    // Version 1 frames end before the IMU fields.
    const size_t frameSize = version == 1 ? offsetof(RecFrame, imuValid) : sizeof(RecFrame);
    fr = RecFrame{};
    if (std::fread(&fr, frameSize, 1, f) != 1) return false;
    gray.resize(static_cast<size_t>(h.grayW) * h.grayH);
    depth.resize(static_cast<size_t>(h.depW) * h.depH);
    if (std::fread(gray.data(), 1, gray.size(), f) != gray.size()) return false;
    return std::fread(depth.data(), 2, depth.size(), f) == depth.size();
}
