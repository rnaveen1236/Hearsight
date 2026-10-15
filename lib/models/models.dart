class DetectionResult {
  final String objectName;
  final double confidence;
  final double distanceMeters;
  final String directionClock;
  final List<double> boundingBox; // [x, y, width, height]

  DetectionResult({
    required this.objectName,
    required this.confidence,
    required this.distanceMeters,
    required this.directionClock,
    required this.boundingBox,
  });
}

/// D455 IMU state from native telemetry. Angles in degrees
/// (pitch > 0: camera tilted up, roll > 0: rolled clockwise from behind).
class ImuData {
  final bool running;
  final double pitchDeg;
  final double rollDeg;
  final List<double> gyro;  // rad/s, IMU frame (x right, y down, z forward)
  final List<double> accel; // m/s^2
  final double gyroHz;
  final double accelHz;

  const ImuData({
    this.running = false,
    this.pitchDeg = 0.0,
    this.rollDeg = 0.0,
    this.gyro = const [0.0, 0.0, 0.0],
    this.accel = const [0.0, 0.0, 0.0],
    this.gyroHz = 0.0,
    this.accelHz = 0.0,
  });

  factory ImuData.fromJson(Map<String, dynamic> j) {
    List<double> vec(dynamic v) =>
        (v as List<dynamic>?)?.map((e) => (e as num).toDouble()).toList() ?? const [0.0, 0.0, 0.0];
    return ImuData(
      running: j['running'] as bool? ?? false,
      pitchDeg: (j['pitch'] as num?)?.toDouble() ?? 0.0,
      rollDeg: (j['roll'] as num?)?.toDouble() ?? 0.0,
      gyro: vec(j['gyro']),
      accel: vec(j['accel']),
      gyroHz: (j['gyroHz'] as num?)?.toDouble() ?? 0.0,
      accelHz: (j['accelHz'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class TelemetryData {
  final double fps;
  final double p50LatencyMs;
  final double p95LatencyMs;
  final double p99LatencyMs;
  final double cpuUsage;
  final double gpuUsage;
  final double npuUsage;
  final double thermalState;
  final double batteryLevel;
  final int droppedFrames;
  final double usbTransportLatencyMs;
  final double centerDistanceMeters;
  final ImuData imu;

  TelemetryData({
    this.fps = 0.0,
    this.p50LatencyMs = 0.0,
    this.p95LatencyMs = 0.0,
    this.p99LatencyMs = 0.0,
    this.cpuUsage = 0.0,
    this.gpuUsage = 0.0,
    this.npuUsage = 0.0,
    this.thermalState = 0.0,
    this.batteryLevel = 100.0,
    this.droppedFrames = 0,
    this.usbTransportLatencyMs = 0.0,
    this.centerDistanceMeters = 0.0,
    this.imu = const ImuData(),
  });
}

enum PipelineStage {
  stage1,
  stage2,
  stage3,
  stage4,
}

class StageConfig {
  final PipelineStage currentStage;
  final bool isStage3Enabled; // Feature flag for stage 3

  StageConfig({
    required this.currentStage,
    this.isStage3Enabled = false,
  });
}
