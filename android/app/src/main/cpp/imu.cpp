#include "imu.h"

#include <android/log.h>

#include <cmath>
#include <vector>

#define LOG_TAG "HSV2_IMU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr float kRadToDeg = 57.2957795f;
// Weight of the accel angle per accel sample (~100 Hz): time constant ~0.5 s.
constexpr float kAccelBlend = 0.02f;
// Low-pass on accel before deriving angles.
constexpr float kGravityLowPass = 0.2f;

// Use the highest rates the device offers. Lower D455 rates (gyro 200 /
// accel 100) stalled the camera's USB link on rsusb; the highest rates
// (as used by the working MediaPipe build) stream alongside colour/depth.
constexpr int kPreferredGyroHz = -1;
constexpr int kPreferredAccelHz = -1;

rs2::stream_profile pickProfile(const std::vector<rs2::stream_profile>& profiles,
                                rs2_stream stream, int preferredHz) {
    rs2::stream_profile best;
    for (const auto& p : profiles) {
        if (p.stream_type() != stream || p.format() != RS2_FORMAT_MOTION_XYZ32F) continue;
        if (p.fps() == preferredHz) return p;
        if (!best || p.fps() > best.fps()) best = p;
    }
    return best;
}

// Angles from the accel ("up") vector in the IMU frame (x right, y down, z forward).
void accelAngles(const float a[3], float& pitchDeg, float& rollDeg) {
    pitchDeg = std::atan2(a[2], std::sqrt(a[0] * a[0] + a[1] * a[1])) * kRadToDeg;
    rollDeg = std::atan2(-a[0], -a[1]) * kRadToDeg;
}

}  // namespace


bool ImuStreamer::start(const rs2::device& device, std::string& error) {
    stop();

    try {
        for (auto&& s : device.query_sensors()) {
            rs2::motion_sensor motion = s.as<rs2::motion_sensor>();
            if (!motion) continue;

            auto profiles = motion.get_stream_profiles();
            rs2::stream_profile gyro = pickProfile(profiles, RS2_STREAM_GYRO, kPreferredGyroHz);
            rs2::stream_profile accel = pickProfile(profiles, RS2_STREAM_ACCEL, kPreferredAccelHz);

            if (!gyro || !accel) {
                error = "Motion sensor has no gyro/accel MOTION_XYZ32F profiles";
                return false;
            }

            {
                std::lock_guard<std::mutex> lock(mutex_);
                state_ = ImuSnapshot{};
                state_.gyroProfileHz = gyro.fps();
                state_.accelProfileHz = accel.fps();
                filterInit_ = false;
                haveAccel_ = false;
                lastGyroTs_ = 0.0;
                rateWindowStart_ = 0.0;
                rateGyro0_ = rateAccel0_ = 0;
            }

            motion.open(std::vector<rs2::stream_profile>{gyro, accel});
            motion.start([this](rs2::frame f) { onFrame(f); });

            sensor_ = s;  // keep the sensor (and its callback) alive
            started_ = true;

            {
                std::lock_guard<std::mutex> lock(mutex_);
                state_.running = true;
            }

            LOGI("D455 IMU started: gyro %d Hz, accel %d Hz", gyro.fps(), accel.fps());
            return true;
        }
    } catch (const rs2::error& e) {
        error = std::string("IMU start failed: ") + e.what();
        return false;
    }

    error = "No motion sensor on this device (D455 expected)";
    return false;
}


void ImuStreamer::stop() {
    if (!started_) return;

    try {
        sensor_.stop();
        sensor_.close();
    } catch (const rs2::error& e) {
        LOGE("IMU stop: %s", e.what());
    }

    sensor_ = rs2::sensor();
    started_ = false;

    std::lock_guard<std::mutex> lock(mutex_);
    state_.running = false;
    LOGI("D455 IMU stopped");
}


ImuSnapshot ImuStreamer::snapshot() {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_;
}


void ImuStreamer::onFrame(const rs2::frame& frame) {
    rs2::motion_frame mf = frame.as<rs2::motion_frame>();
    if (!mf) return;

    const rs2_vector v = mf.get_motion_data();
    const double ts = mf.get_timestamp();  // ms
    const rs2_stream stream = mf.get_profile().stream_type();

    std::lock_guard<std::mutex> lock(mutex_);

    if (stream == RS2_STREAM_GYRO) {
        state_.gyro[0] = v.x; state_.gyro[1] = v.y; state_.gyro[2] = v.z;
        state_.gyroCount++;

        if (filterInit_ && lastGyroTs_ > 0.0) {
            float dt = static_cast<float>((ts - lastGyroTs_) / 1000.0);
            if (dt > 0.0f && dt < 0.1f) {
                // +x rotation tilts z (forward) up; +z rotation rolls clockwise.
                state_.pitchDeg += v.x * dt * kRadToDeg;
                state_.rollDeg += v.z * dt * kRadToDeg;
            }
        }
        lastGyroTs_ = ts;

    } else if (stream == RS2_STREAM_ACCEL) {
        state_.accel[0] = v.x; state_.accel[1] = v.y; state_.accel[2] = v.z;
        state_.accelCount++;

        const float a[3] = {v.x, v.y, v.z};
        if (!haveAccel_) {
            for (int i = 0; i < 3; ++i) gravity_[i] = a[i];
            haveAccel_ = true;
        } else {
            for (int i = 0; i < 3; ++i) gravity_[i] += kGravityLowPass * (a[i] - gravity_[i]);
        }

        accelAngles(gravity_, state_.accelPitchDeg, state_.accelRollDeg);

        if (!filterInit_) {
            state_.pitchDeg = state_.accelPitchDeg;
            state_.rollDeg = state_.accelRollDeg;
            filterInit_ = true;
        } else {
            // Blend toward the accel angle, taking the short way round for roll.
            state_.pitchDeg += kAccelBlend * (state_.accelPitchDeg - state_.pitchDeg);
            float dr = state_.accelRollDeg - state_.rollDeg;
            if (dr > 180.0f) dr -= 360.0f;
            if (dr < -180.0f) dr += 360.0f;
            state_.rollDeg += kAccelBlend * dr;
            if (state_.rollDeg > 180.0f) state_.rollDeg -= 360.0f;
            if (state_.rollDeg < -180.0f) state_.rollDeg += 360.0f;
        }
    } else {
        return;
    }

    // Measured sample rates over ~1 s windows (hardware time).
    if (rateWindowStart_ == 0.0) {
        rateWindowStart_ = ts;
        rateGyro0_ = state_.gyroCount;
        rateAccel0_ = state_.accelCount;
    } else if (ts - rateWindowStart_ >= 1000.0) {
        double sec = (ts - rateWindowStart_) / 1000.0;
        state_.gyroHz = static_cast<float>((state_.gyroCount - rateGyro0_) / sec);
        state_.accelHz = static_cast<float>((state_.accelCount - rateAccel0_) / sec);
        rateWindowStart_ = ts;
        rateGyro0_ = state_.gyroCount;
        rateAccel0_ = state_.accelCount;
    }
}
