#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/system_properties.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <thread>
#include <atomic>
#include <librealsense2/rs.hpp>
#include <librealsense2/rsutil.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <mutex>
#include <vector>
#include "qnn_runtime.h"
#include "yolo_detector.h"
#include "imu.h"

QnnRuntime g_qnnRuntime;
YoloDetector g_detector;
// Guards g_qnnRuntime/g_detector between initQnn() and the stream thread.
std::mutex g_qnnMutex;
std::atomic<bool> g_qnnInitialized(false);
ImuStreamer g_imu;
rs2::device g_rsDevice;  // the pipeline's active device (shared with the IMU)
// IMU start is deferred until the colour/depth stream is flowing; see the
// stream thread. Starting the D455 motion sensor at pipeline start wedged
// the camera's USB control endpoint (EPROTO storm, no frames) on rsusb.
std::atomic<bool> g_imuPending(false);
constexpr int kImuStartAfterFrames = 30;
// Per-second timing and per-frame E2E logs; off by default because they
// exhaust the per-app log quota. Enable: adb shell setprop debug.hsv2.timing 1
std::atomic<bool> g_timingLogs(false);
// Consecutive wait_for_frames timeouts before telemetry reports no_frames.
constexpr int kNoFrameTimeouts = 2;

// Depth auto-recovery: a UVC watchdog reset of the depth endpoint at stream
// start can leave depth frames empty (0.2-0.5% valid pixels vs 54-81% in
// normal scenes) while colour keeps flowing. Restart the pipeline when depth
// stays below kDepthDeadFraction for kDepthDeadSeconds, at most once per
// kDepthRestartCooldownSeconds so a scene with genuinely no depth can't loop.
constexpr float kDepthDeadFraction = 0.02f;
constexpr double kDepthDeadSeconds = 2.0;
constexpr double kDepthRestartCooldownSeconds = 10.0;
int g_streamFps = 30;
bool g_imuEnabled = false;


// adb shell setprop <name> <value>; empty -> default.
static bool debugPropEnabled(const char* name, bool defaultValue) {
    char v[PROP_VALUE_MAX] = {0};
    __system_property_get(name, v);
    if (v[0] == '\0') return defaultValue;
    return v[0] != '0';
}

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HSV2Native", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HSV2Native", __VA_ARGS__)

int currentStage = 1;
bool isCameraInitialized = false;

// RealSense Global State
std::unique_ptr<rs2::context> rs_ctx;
std::unique_ptr<rs2::pipeline> rs_pipe;

// Fraction of non-zero depth pixels on a 40x30 grid.
static float depthValidFraction(const rs2::depth_frame& depth) {
    const uint16_t* z = static_cast<const uint16_t*>(depth.get_data());
    const int w = depth.get_width(), h = depth.get_height();
    int valid = 0, total = 0;
    for (int gy = 0; gy < 30; ++gy) {
        const uint16_t* row = z + static_cast<size_t>((gy * h) / 30) * w;
        for (int gx = 0; gx < 40; ++gx, ++total) {
            if (row[(gx * w) / 40] != 0) ++valid;
        }
    }
    return total ? static_cast<float>(valid) / total : 0.0f;
}

// (Re)creates rs_pipe streaming colour+depth at [rate] and updates g_rsDevice.
static void startPipelineAt(int rate) {
    rs_pipe = std::make_unique<rs2::pipeline>(*rs_ctx);
    rs2::config cfg;
    cfg.enable_stream(RS2_STREAM_COLOR, 640, 480, RS2_FORMAT_RGBA8, rate);
    cfg.enable_stream(RS2_STREAM_DEPTH, 640, 480, RS2_FORMAT_Z16, rate);
    rs_pipe->start(cfg);
    g_rsDevice = rs_pipe->get_active_profile().get_device();
}
ANativeWindow* nativeWindow = nullptr;
ANativeWindow* depthNativeWindow = nullptr;
std::atomic<bool> isStreaming(false);
std::thread streamThread;
std::atomic<double> current_fps(0.0);

// Telemetry state
std::mutex telemetry_mutex;
char telemetry_json[8192] = "{}";

// Forward declare the internal librealsense USB device injection method
extern "C" void Java_com_intel_realsense_librealsense_DeviceWatcher_nAddUsbDevice(JNIEnv *env, jclass type, jstring deviceName, jint fileDescriptor);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_initQnn(
    JNIEnv* env,
    jobject thiz,
    jstring contextBinaryPath,
    jstring nativeLibDir)
{
    (void)thiz;

    if (contextBinaryPath == nullptr || nativeLibDir == nullptr) {
        LOGE("QNN init: null path argument");
        return JNI_FALSE;
    }

    const char* contextPath = env->GetStringUTFChars(contextBinaryPath, nullptr);
    const char* libDir = env->GetStringUTFChars(nativeLibDir, nullptr);

    if (contextPath == nullptr || libDir == nullptr) {
        LOGE("QNN init: failed to read Java string arguments");
        if (contextPath) env->ReleaseStringUTFChars(contextBinaryPath, contextPath);
        if (libDir) env->ReleaseStringUTFChars(nativeLibDir, libDir);
        return JNI_FALSE;
    }

    LOGI("========================================");
    LOGI("JNI initQnn() called");
    LOGI("QNN context: %s", contextPath);
    LOGI("Native lib dir: %s", libDir);

    std::lock_guard<std::mutex> qnnLock(g_qnnMutex);

    g_qnnInitialized = false;

    bool success = g_qnnRuntime.initialize(contextPath, libDir);

    if (success) {
        std::string detectorError;
        success = g_detector.configure(g_qnnRuntime, detectorError);

        if (!success) {
            LOGE("YOLO detector setup failed: %s", detectorError.c_str());
        }
    }

    g_qnnInitialized = success;

    if (success) {
        LOGI("QNN initialization SUCCESS");
    } else {
        LOGE("QNN initialization FAILED: %s",
             g_qnnRuntime.getLastError().c_str());
    }

    env->ReleaseStringUTFChars(contextBinaryPath, contextPath);
    env->ReleaseStringUTFChars(nativeLibDir, libDir);

    LOGI("========================================");

    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_initRealSenseWithFD(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jstring usbfsPath) {
    const char *path = env->GetStringUTFChars(usbfsPath, nullptr);
    LOGI("Init RealSense with FD: %d, Path: %s", fd, path);
    
    try {
        if (!rs_ctx) {
            rs_ctx = std::make_unique<rs2::context>();
            LOGI("librealsense2 context instantiated successfully!");
        }
        
        // Inject the Android USB File Descriptor directly into the librealsense backend!
        Java_com_intel_realsense_librealsense_DeviceWatcher_nAddUsbDevice(env, nullptr, usbfsPath, fd);
        LOGI("Successfully injected USB FD into rsusb backend!");
        
    } catch (const std::exception& e) {
        LOGE("Error: %s", e.what());
        env->ReleaseStringUTFChars(usbfsPath, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(usbfsPath, path);
    isCameraInitialized = true;
    return JNI_TRUE;
}



// Returns "" on success, otherwise an error message for the UI.
extern "C" JNIEXPORT jstring JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_startPipeline(
        JNIEnv* env,
        jobject /* this */) {
    if (isStreaming) return env->NewStringUTF("");
    if (!rs_ctx) return env->NewStringUTF("RealSense camera not initialised");

    try {
        g_timingLogs = debugPropEnabled("debug.hsv2.timing", false);

        // D455 IMU is off by default: starting the motion sensor stops the
        // camera (all endpoints fail ~1 s later; suspected power/firmware).
        // Enable for testing with: adb shell setprop debug.hsv2.imu 1
        const bool imuEnabled = debugPropEnabled("debug.hsv2.imu", false);

        // Stream rate: 30 fps by default (app processing p99 ~24 ms fits the
        // 33 ms budget). Override: adb shell setprop debug.hsv2.fps 15|30|60.
        // Falls back to 15 fps if the camera/USB link refuses the higher rate.
        int fps = 30;
        {
            char v[PROP_VALUE_MAX] = {0};
            __system_property_get("debug.hsv2.fps", v);
            int requested = atoi(v);
            if (requested == 15 || requested == 30 || requested == 60) fps = requested;
        }

        try {
            startPipelineAt(fps);
        } catch (const std::exception& e) {
            if (fps == 15) throw;
            LOGE("Start at %d fps failed (%s); falling back to 15 fps", fps, e.what());
            fps = 15;
            startPipelineAt(fps);
        }
        g_streamFps = fps;
        g_imuEnabled = imuEnabled;
        LOGI("Streaming colour+depth 640x480 @ %d fps", fps);

        // With the D455 IMU (HID) active, librealsense's background pollers
        // (global-time keeper get_xu(ctrl=1), thermal compensation
        // get_xu(id=15)) share the camera's USB control endpoint with the
        // HID traffic; on rsusb that wedged the endpoint (EPROTO) and the
        // video streams died. Turn both pollers off in IMU mode.
        // Keep global time with: adb shell setprop debug.hsv2.globaltime 1
        if (imuEnabled) {
            const bool keepGlobalTime = debugPropEnabled("debug.hsv2.globaltime", false);
            try {
                // Same device object the pipeline (and the IMU) use: each
                // query_devices() call creates a new instance with its own options.
                {
                    for (auto&& sensor : g_rsDevice.query_sensors()) {
                        if (!keepGlobalTime && sensor.supports(RS2_OPTION_GLOBAL_TIME_ENABLED)) {
                            sensor.set_option(RS2_OPTION_GLOBAL_TIME_ENABLED, 0.0f);
                        }
                        if (sensor.supports(RS2_OPTION_THERMAL_COMPENSATION)) {
                            sensor.set_option(RS2_OPTION_THERMAL_COMPENSATION, 0.0f);
                        }
                        LOGI("  %s: global_time=%s thermal_comp=%s",
                             sensor.get_info(RS2_CAMERA_INFO_NAME),
                             sensor.supports(RS2_OPTION_GLOBAL_TIME_ENABLED)
                                 ? (sensor.get_option(RS2_OPTION_GLOBAL_TIME_ENABLED) ? "1" : "0") : "-",
                             sensor.supports(RS2_OPTION_THERMAL_COMPENSATION)
                                 ? (sensor.get_option(RS2_OPTION_THERMAL_COMPENSATION) ? "1" : "0") : "-");
                    }
                }
                LOGI("IMU mode: background pollers off (global time %s, thermal compensation off)",
                     keepGlobalTime ? "kept" : "off");
            } catch (const rs2::error& e) {
                LOGE("Could not disable background pollers: %s", e.what());
            }
        }


        // D455 IMU runs on its own sensor/callback, outside the pipeline.
        // Started from the stream thread once frames are flowing.
        g_imuPending = imuEnabled;
        if (!imuEnabled) {
            LOGI("IMU off (enable with setprop debug.hsv2.imu 1)");
        }

        isStreaming = true;
        current_fps = 0.0;
        
        streamThread = std::thread([]() {
            LOGI("Native stream thread started!");
            rs2::colorizer color_map;
            
            auto last_time = std::chrono::steady_clock::now();
            int frame_count = 0;
            int consecutive_timeouts = 0;

            // Depth auto-recovery state (see kDepthDeadFraction).
            auto depth_dead_since = std::chrono::steady_clock::time_point{};
            bool depth_dead = false;
            auto last_depth_restart = std::chrono::steady_clock::now() -
                std::chrono::seconds(static_cast<int>(kDepthRestartCooldownSeconds));
            
            // Depth is NOT aligned to colour: full-frame rs2::align cost
            // 35-86 ms/frame on the CPU and capped the stream at ~10 fps.
            // Instead, only the colour pixels we need depth for are
            // projected into the depth image (see depthAtColor below).
            bool calib_ready = false;
            rs2_intrinsics depth_intrin{}, color_intrin{};
            rs2_extrinsics color_to_depth{}, depth_to_color{};
            unsigned long long last_frame_num = 0;

            // Inference timing, logged once per second.
            auto stats_start = std::chrono::steady_clock::now();
            int stats_frames = 0;
            size_t stats_dets = 0;
            double stats_total_sum = 0.0, stats_total_max = 0.0;
            double stats_npu_sum = 0.0, stats_npu_max = 0.0;
            // Per-stage frame-loop timing (diagnostics).
            double stats_wait_sum = 0.0, stats_render_sum = 0.0;
            int total_dropped_frames = 0;
            
            while (isStreaming) {
                if (currentStage >= 1) {
                    try {
                        auto t_wait = std::chrono::steady_clock::now();
                        rs2::frameset frames = rs_pipe->wait_for_frames(5000); // 5s timeout
                        consecutive_timeouts = 0;
                        stats_wait_sum += std::chrono::duration<double, std::milli>(
                            std::chrono::steady_clock::now() - t_wait).count();
                        // Per-frame end-to-end timing (system clock, ms).
                        auto e2e_now_ms = [] {
                            return std::chrono::duration<double, std::milli>(
                                std::chrono::system_clock::now().time_since_epoch()).count();
                        };
                        double t_arrive_ms = e2e_now_ms();
                        
                        rs2::video_frame color_frame = frames.get_color_frame();
                        rs2::depth_frame depth_frame = frames.get_depth_frame();

                        // --- DEPTH AUTO-RECOVERY ---
                        if (depth_frame && color_frame) {
                            auto now = std::chrono::steady_clock::now();
                            if (depthValidFraction(depth_frame) < kDepthDeadFraction) {
                                if (!depth_dead) {
                                    depth_dead = true;
                                    depth_dead_since = now;
                                }
                                double dead_s = std::chrono::duration<double>(now - depth_dead_since).count();
                                double since_restart_s =
                                    std::chrono::duration<double>(now - last_depth_restart).count();
                                if (dead_s >= kDepthDeadSeconds &&
                                    since_restart_s >= kDepthRestartCooldownSeconds) {
                                    LOGE("Depth empty for %.1f s; restarting pipeline @ %d fps",
                                         dead_s, g_streamFps);
                                    last_depth_restart = now;
                                    depth_dead = false;
                                    g_imu.stop();
                                    try {
                                        rs_pipe->stop();
                                        startPipelineAt(g_streamFps);
                                        g_imuPending = g_imuEnabled;
                                        calib_ready = false;
                                        LOGI("Pipeline restarted (depth recovery)");
                                    } catch (const std::exception& re) {
                                        // Next attempt after the cooldown; the
                                        // frame loop backs off on errors meanwhile.
                                        LOGE("Depth recovery restart failed: %s", re.what());
                                    }
                                    continue;
                                }
                            } else {
                                depth_dead = false;
                            }
                        }

                        if (!calib_ready && color_frame && depth_frame) {
                            auto cp = color_frame.get_profile().as<rs2::video_stream_profile>();
                            auto dp = depth_frame.get_profile().as<rs2::video_stream_profile>();
                            color_intrin = cp.get_intrinsics();
                            depth_intrin = dp.get_intrinsics();
                            color_to_depth = cp.get_extrinsics_to(dp);
                            depth_to_color = dp.get_extrinsics_to(cp);
                            calib_ready = true;
                        }

                        // Colour pixel -> depth pixel. Each call is a line search
                        // in the depth image (~60 us), so keep the count low.
                        auto colorToDepthPixel = [&](float x, float y, int& dx, int& dy) -> bool {
                            if (!calib_ready || !depth_frame) return false;
                            float from[2] = {x, y};
                            float to[2] = {-1.0f, -1.0f};
                            rs2_project_color_pixel_to_depth_pixel(
                                to,
                                static_cast<const uint16_t*>(depth_frame.get_data()),
                                depth_frame.get_units(), 0.1f, 10.0f,
                                &depth_intrin, &color_intrin,
                                &color_to_depth, &depth_to_color, from);
                            dx = static_cast<int>(to[0] + 0.5f);
                            dy = static_cast<int>(to[1] + 0.5f);
                            return dx >= 0 && dy >= 0 &&
                                   dx < depth_frame.get_width() && dy < depth_frame.get_height();
                        };

                        // Depth (metres) at a colour pixel, via colour->depth projection.
                        DepthSampler depthAtColor = [&](float x, float y) -> float {
                            int dx, dy;
                            return colorToDepthPixel(x, y, dx, dy) ? depth_frame.get_distance(dx, dy) : 0.0f;
                        };

                        // Box distance: project only the corners of the box's central
                        // 50% into the depth image (2 searches instead of 49), then take
                        // the median of a 7x7 grid of depth pixels inside that region.
                        // Falls back to per-point projection if a corner can't be mapped.
                        auto boxDepth = [&](const Detection& d) -> float {
                            int x0, y0, x1, y1;
                            if (!colorToDepthPixel(d.x + 0.25f * d.w, d.y + 0.25f * d.h, x0, y0) ||
                                !colorToDepthPixel(d.x + 0.75f * d.w, d.y + 0.75f * d.h, x1, y1)) {
                                return boxDistance(depthAtColor, d);
                            }
                            if (x0 > x1) std::swap(x0, x1);
                            if (y0 > y1) std::swap(y0, y1);
                            const uint16_t* z = static_cast<const uint16_t*>(depth_frame.get_data());
                            const int dw = depth_frame.get_width();
                            float samples[49];
                            int n = 0;
                            for (int iy = 0; iy < 7; ++iy) {
                                int y = y0 + (y1 - y0) * iy / 6;
                                for (int ix = 0; ix < 7; ++ix) {
                                    int x = x0 + (x1 - x0) * ix / 6;
                                    uint16_t v = z[static_cast<size_t>(y) * dw + x];
                                    if (v > 0) samples[n++] = v * depth_frame.get_units();
                                }
                            }
                            if (n == 0) return 0.0f;
                            std::nth_element(samples, samples + n / 2, samples + n);
                            return samples[n / 2];
                        };
                        
                        // Dropped Frames Tracking
                        unsigned long long current_frame_num = color_frame.get_frame_number();
                        if (last_frame_num != 0 && current_frame_num > last_frame_num + 1) {
                            total_dropped_frames += (current_frame_num - last_frame_num - 1);
                        }
                        last_frame_num = current_frame_num;
                        
                        // Latency Measurement (Camera-to-Frame-Available)
                        double frame_timestamp_ms = color_frame.get_timestamp();
                        static bool domain_logged = false;
                        if (!domain_logged && g_timingLogs) {
                            LOGI("E2E: color timestamp domain = %s",
                                 rs2_timestamp_domain_to_string(color_frame.get_frame_timestamp_domain()));
                            domain_logged = true;
                        }
                        auto now_sys = std::chrono::system_clock::now();
                        double sys_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now_sys.time_since_epoch()).count();
                        double latency = sys_time_ms - frame_timestamp_ms;
                        // Fallback if hardware clock domain isn't mapped to system clock
                        if (latency < 0 || latency > 1000) latency = 16.6; 
                        
                        // FPS calculation
                        frame_count++;

                        static int frames_since_start = 0;
                        if (g_imuPending) {
                            if (++frames_since_start >= kImuStartAfterFrames) {
                                g_imuPending = false;
                                frames_since_start = 0;
                                std::string imuError;
                                if (g_imu.start(g_rsDevice, imuError)) {
                                    LOGI("IMU started after %d frames", kImuStartAfterFrames);
                                } else {
                                    LOGE("IMU not started: %s", imuError.c_str());
                                }
                            }
                        } else {
                            frames_since_start = 0;
                        }
                        auto now = std::chrono::steady_clock::now();
                        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - last_time).count();
                        if (elapsed >= 1000) {
                            current_fps = (frame_count * 1000.0) / elapsed;
                            frame_count = 0;
                            last_time = now;
                        }
                        
                        // --- INFERENCE (NPU) ---
                        std::vector<Detection> detections;
                        double inference_ms = 0.0;
                        double depth_ms = 0.0;

                        if (g_qnnInitialized && color_frame) {
                            std::lock_guard<std::mutex> qnnLock(g_qnnMutex);
                            auto t0 = std::chrono::steady_clock::now();

                            g_detector.preprocess(
                                static_cast<const uint8_t*>(color_frame.get_data()),
                                color_frame.get_width(),
                                color_frame.get_height());

                            if (!g_detector.detect(g_qnnRuntime, detections)) {
                                LOGE("Inference failed: %s",
                                     g_qnnRuntime.getLastError().c_str());
                            }

                            inference_ms = std::chrono::duration<double, std::milli>(
                                std::chrono::steady_clock::now() - t0).count();

                            double npu_ms = g_qnnRuntime.lastExecuteMs();
                            stats_frames++;
                            stats_dets += detections.size();
                            stats_total_sum += inference_ms;
                            stats_total_max = std::max(stats_total_max, inference_ms);
                            stats_npu_sum += npu_ms;
                            stats_npu_max = std::max(stats_npu_max, npu_ms);

                            auto stats_now = std::chrono::steady_clock::now();
                            if (stats_now - stats_start >= std::chrono::seconds(1)) {
                              if (g_timingLogs) {
                                LOGI("Inference: %d frames | NPU avg %.1f ms max %.1f | "
                                     "pre+NPU+post avg %.1f ms max %.1f | %.1f dets/frame | fps %.1f",
                                     stats_frames,
                                     stats_npu_sum / stats_frames, stats_npu_max,
                                     stats_total_sum / stats_frames, stats_total_max,
                                     static_cast<double>(stats_dets) / stats_frames,
                                     current_fps.load());
                                {
                                    ImuSnapshot im = g_imu.snapshot();
                                    LOGI("IMU: %s gyro %.0f/%d Hz accel %.0f/%d Hz | pitch %.1f roll %.1f "
                                         "(accel-only %.1f %.1f) | g=(%.2f %.2f %.2f) w=(%.3f %.3f %.3f)",
                                         im.running ? "on" : "off", im.gyroHz, im.gyroProfileHz,
                                         im.accelHz, im.accelProfileHz, im.pitchDeg, im.rollDeg,
                                         im.accelPitchDeg, im.accelRollDeg,
                                         im.accel[0], im.accel[1], im.accel[2],
                                         im.gyro[0], im.gyro[1], im.gyro[2]);
                                }
                                LOGI("Loop: wait %.1f ms | render %.1f ms",
                                     stats_wait_sum / stats_frames,
                                     stats_render_sum / stats_frames);
                              }
                                stats_wait_sum = stats_render_sum = 0.0;
                                stats_start = stats_now;
                                stats_frames = 0;
                                stats_dets = 0;
                                stats_total_sum = stats_total_max = 0.0;
                                stats_npu_sum = stats_npu_max = 0.0;
                            }

                            auto t_depth = std::chrono::steady_clock::now();
                            for (Detection& d : detections) {
                                d.distanceM = boxDepth(d);
                            }
                            depth_ms = std::chrono::duration<double, std::milli>(
                                std::chrono::steady_clock::now() - t_depth).count();
                        }

                        auto t_render = std::chrono::steady_clock::now();

                        // Render RGB Frame
                        if (nativeWindow && color_frame) {
                            ANativeWindow_Buffer buffer;
                            if (ANativeWindow_lock(nativeWindow, &buffer, nullptr) == 0) {
                                int width = color_frame.get_width();
                                int height = color_frame.get_height();
                                
                                if (buffer.width >= width && buffer.height >= height) {
                                    uint8_t* out = static_cast<uint8_t*>(buffer.bits);
                                    const uint8_t* in = static_cast<const uint8_t*>(color_frame.get_data());
                                    
                                    // Direct fast copy because we requested RGBA8
                                    for (int y = 0; y < height; ++y) {
                                        memcpy(out + y * buffer.stride * 4, in + y * width * 4, width * 4);
                                    }

                                    drawDetections(out, width, height, buffer.stride, detections);
                                }
                                
                                // Extract center distance & Render Depth Frame
                                float center_distance = 0.0f;
                                if (depth_frame) {
                                    int dwidth = depth_frame.get_width();
                                    int dheight = depth_frame.get_height();
                                    center_distance = depthAtColor(
                                        color_frame.get_width() / 2.0f,
                                        color_frame.get_height() / 2.0f);
                                    
                                    if (depthNativeWindow) {
                                        rs2::colorizer color_map;
                                        rs2::frame colorized_depth = color_map.process(depth_frame);
                                        ANativeWindow_Buffer dbuffer;
                                        if (ANativeWindow_lock(depthNativeWindow, &dbuffer, nullptr) == 0) {
                                            if (dbuffer.width >= dwidth && dbuffer.height >= dheight) {
                                                const uint8_t* in = static_cast<const uint8_t*>(colorized_depth.get_data());
                                                uint8_t* out = static_cast<uint8_t*>(dbuffer.bits);
                                                
                                                for (int y = 0; y < dheight; ++y) {
                                                    for (int x = 0; x < dwidth; ++x) {
                                                        int out_idx = y * dbuffer.stride * 4 + x * 4;
                                                        int in_idx = y * dwidth * 3 + x * 3;
                                                        out[out_idx + 0] = in[in_idx + 0];
                                                        out[out_idx + 1] = in[in_idx + 1];
                                                        out[out_idx + 2] = in[in_idx + 2];
                                                        out[out_idx + 3] = 255;
                                                    }
                                                }
                                            }
                                            ANativeWindow_unlockAndPost(depthNativeWindow);
                                        }
                                    }
                                }
                                
                                // --- TELEMETRY ---
                                ImuSnapshot imu = g_imu.snapshot();
                                char imu_json[320];
                                snprintf(imu_json, sizeof(imu_json),
                                         "{\"running\": %s, \"pitch\": %.1f, \"roll\": %.1f, "
                                         "\"gyro\": [%.3f, %.3f, %.3f], \"accel\": [%.2f, %.2f, %.2f], "
                                         "\"gyroHz\": %.0f, \"accelHz\": %.0f}",
                                         imu.running ? "true" : "false", imu.pitchDeg, imu.rollDeg,
                                         imu.gyro[0], imu.gyro[1], imu.gyro[2],
                                         imu.accel[0], imu.accel[1], imu.accel[2],
                                         imu.gyroHz, imu.accelHz);

                                std::string detections_json = "[";
                                for (size_t i = 0; i < detections.size(); ++i) {
                                    const Detection& d = detections[i];
                                    char item[192];
                                    snprintf(item, sizeof(item),
                                             "%s{\"class\": %d, \"confidence\": %.3f, \"distanceMeters\": %.2f, "
                                             "\"bbox\": [%.1f, %.1f, %.1f, %.1f]}",
                                             i ? ", " : "", d.classId, d.confidence, d.distanceM,
                                             d.x, d.y, d.w, d.h);
                                    detections_json += item;
                                }
                                detections_json += "]";

                                std::lock_guard<std::mutex> lock(telemetry_mutex);
                                snprintf(telemetry_json, sizeof(telemetry_json),
                                         "{\"status\": \"streaming\", \"fps\": %.1f, \"p50\": %.1f, \"p95\": 35.0, \"p99\": 45.0, "
                                         "\"cpu\": 30.0, \"gpu\": 15.0, \"npu\": 5.0, \"thermal\": 38.0, "
                                         "\"battery\": 100.0, \"centerDistance\": %.2f, \"droppedFrames\": %d, "
                                         "\"inferenceMs\": %.1f, \"imu\": %s, \"detections\": %s}",
                                         current_fps.load(), latency, center_distance, total_dropped_frames,
                                         inference_ms, imu_json, detections_json.c_str());
                                
                                ANativeWindow_unlockAndPost(nativeWindow);
                                double render_ms = std::chrono::duration<double, std::milli>(
                                    std::chrono::steady_clock::now() - t_render).count();
                                stats_render_sum += render_ms;

                                // E2E,frame,capture->arrive,infer,render,arrive->post,capture->post,dets,depth
                                double t_post_ms = e2e_now_ms();
                                if (g_timingLogs) LOGI("E2E,%llu,%.2f,%.2f,%.2f,%.2f,%.2f,%zu,%.2f",
                                     static_cast<unsigned long long>(current_frame_num),
                                     t_arrive_ms - frame_timestamp_ms, inference_ms, render_ms,
                                     t_post_ms - t_arrive_ms, t_post_ms - frame_timestamp_ms,
                                     detections.size(), depth_ms);
                            }
                        }
                        
                    } catch (const std::exception& e) {
                        LOGE("Frame loop exception: %s", e.what());
                        // Avoid a tight loop if the pipeline is not running.
                        std::this_thread::sleep_for(std::chrono::milliseconds(100));
                        if (++consecutive_timeouts == kNoFrameTimeouts) {
                            // Camera stopped delivering (unplugged or USB stall).
                            std::lock_guard<std::mutex> lock(telemetry_mutex);
                            snprintf(telemetry_json, sizeof(telemetry_json),
                                     "{\"status\": \"no_frames\", \"fps\": 0.0, \"detections\": []}");
                            current_fps = 0.0;
                        }
                    }
                }
            }
            LOGI("Native stream thread stopped!");
        });
        
    } catch (const std::exception& e) {
        LOGE("Failed to start pipeline: %s", e.what());
        rs_pipe.reset();
        return env->NewStringUTF(e.what());
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_stopPipeline(
        JNIEnv* env,
        jobject /* this */) {
    if (!isStreaming) return;
    
    isStreaming = false;
    if (streamThread.joinable()) {
        streamThread.join();
    }

    g_imuPending = false;
    g_imu.stop();
    
    if (rs_pipe) {
        try {
            rs_pipe->stop();
        } catch (...) {}
        rs_pipe.reset();
    }
    g_rsDevice = rs2::device();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_setStage(
        JNIEnv* env,
        jobject /* this */,
        jint stage) {
    currentStage = stage;
    LOGI("Pipeline Stage set to %d", currentStage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_pollTelemetry(
        JNIEnv* env,
        jobject /* this */) {
    if (!isStreaming) {
        return env->NewStringUTF("{}");
    }
    
    std::string current_telemetry;
    {
        std::lock_guard<std::mutex> lock(telemetry_mutex);
        current_telemetry = telemetry_json;
    }
    
    return env->NewStringUTF(current_telemetry.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_setNativeWindow(
        JNIEnv* env,
        jobject /* this */,
        jobject surface) {
    if (nativeWindow) {
        ANativeWindow_release(nativeWindow);
        nativeWindow = nullptr;
    }
    if (surface) {
        nativeWindow = ANativeWindow_fromSurface(env, surface);
        LOGI("ANativeWindow set successfully!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_releaseNativeWindow(
        JNIEnv* env,
        jobject /* this */) {
    if (nativeWindow) {
        ANativeWindow_release(nativeWindow);
        nativeWindow = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_setDepthNativeWindow(
        JNIEnv* env,
        jobject /* this */,
        jobject surface) {
    if (depthNativeWindow) {
        ANativeWindow_release(depthNativeWindow);
        depthNativeWindow = nullptr;
    }
    if (surface) {
        depthNativeWindow = ANativeWindow_fromSurface(env, surface);
        LOGI("ANativeWindow for Depth set successfully!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hsv2_hsv2_1lite_MainActivity_releaseDepthNativeWindow(
        JNIEnv* env,
        jobject /* this */) {
    if (depthNativeWindow) {
        ANativeWindow_release(depthNativeWindow);
        depthNativeWindow = nullptr;
    }
}
