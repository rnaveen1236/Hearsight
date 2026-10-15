#ifndef HSV2_IMU_H
#define HSV2_IMU_H

#include <atomic>
#include <mutex>
#include <string>

#include <librealsense2/rs.hpp>

/*
 * D455 IMU (BMI055) streaming on the motion-module sensor, independent of
 * the colour/depth rs2::pipeline. Gyro and accel run on their own callback
 * (librealsense thread) and feed a complementary filter for pitch/roll.
 *
 * Axes are the D455 IMU frame (librealsense): x right, y down, z forward.
 * At rest and level, accel reads about (0, -9.81, 0).
 *   pitch > 0 : camera tilted up (looking above the horizon)
 *   roll  > 0 : camera rolled clockwise (as seen from behind)
 */
struct ImuSnapshot {
    bool running{false};
    float gyro[3]{};          // rad/s
    float accel[3]{};         // m/s^2
    float pitchDeg{0.0f};     // complementary filter
    float rollDeg{0.0f};
    float accelPitchDeg{0.0f};  // accel-only angles (for verification)
    float accelRollDeg{0.0f};
    float gyroHz{0.0f};       // measured sample rates
    float accelHz{0.0f};
    int gyroProfileHz{0};     // configured rates
    int accelProfileHz{0};
    unsigned long long gyroCount{0};
    unsigned long long accelCount{0};
};

class ImuStreamer {
public:
    // Opens and starts gyro + accel on the device's motion sensor.
    bool start(const rs2::device& device, std::string& error);
    void stop();

    ImuSnapshot snapshot();

private:
    void onFrame(const rs2::frame& frame);

    std::mutex mutex_;
    rs2::sensor sensor_;
    bool started_{false};

    ImuSnapshot state_;
    bool filterInit_{false};
    double lastGyroTs_{0.0};     // ms, hardware timestamps
    bool haveAccel_{false};
    float gravity_[3]{};         // low-passed accel

    // Rate measurement window.
    double rateWindowStart_{0.0};
    unsigned long long rateGyro0_{0};
    unsigned long long rateAccel0_{0};
};

#endif  // HSV2_IMU_H
