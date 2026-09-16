import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'dart:convert';
import 'dart:async';
import '../models/models.dart';

class AppState extends ChangeNotifier {
  static const List<String> cocoClasses = [
    'Person', 'Bicycle', 'Car', 'Motorcycle', 'Airplane', 'Bus', 'Train', 'Truck', 'Boat', 'Traffic Light',
    'Fire Hydrant', 'Stop Sign', 'Parking Meter', 'Bench', 'Bird', 'Cat', 'Dog', 'Horse', 'Sheep', 'Cow',
    'Elephant', 'Bear', 'Zebra', 'Giraffe', 'Backpack', 'Umbrella', 'Handbag', 'Tie', 'Suitcase', 'Frisbee',
    'Skis', 'Snowboard', 'Sports Ball', 'Kite', 'Baseball Bat', 'Baseball Glove', 'Skateboard', 'Surfboard',
    'Tennis Racket', 'Bottle', 'Wine Glass', 'Cup', 'Fork', 'Knife', 'Spoon', 'Bowl', 'Banana', 'Apple',
    'Sandwich', 'Orange', 'Broccoli', 'Carrot', 'Hot Dog', 'Pizza', 'Donut', 'Cake', 'Chair', 'Couch',
    'Potted Plant', 'Bed', 'Dining Table', 'Toilet', 'TV', 'Laptop', 'Mouse', 'Remote', 'Keyboard', 'Cell Phone',
    'Microwave', 'Oven', 'Toaster', 'Sink', 'Refrigerator', 'Book', 'Clock', 'Vase', 'Scissors', 'Teddy Bear',
    'Hair Drier', 'Toothbrush'
  ];

  static const MethodChannel _commandChannel = MethodChannel('com.hsv2/command');
  static const EventChannel _telemetryChannel = EventChannel('com.hsv2/telemetry');
  static const EventChannel _usbStatusChannel = EventChannel('com.hsv2/usb_status');

  int? _textureId;
  int? get textureId => _textureId;

  int? _depthTextureId;
  int? get depthTextureId => _depthTextureId;

  // Telemetry
  TelemetryData _telemetry = TelemetryData();
  TelemetryData get telemetry => _telemetry;

  // Pipeline Status
  bool _isPipelineRunning = false;
  bool get isPipelineRunning => _isPipelineRunning;

  // Camera Connection Status
  String _usbStatus = kIsWeb ? "CONNECTED" : "DISCONNECTED";
  String get usbStatus => _usbStatus;
  bool get isCameraConnected => _usbStatus == "CONNECTED";

  // Stage Config
  StageConfig _stageConfig = StageConfig(currentStage: PipelineStage.stage1);
  StageConfig get stageConfig => _stageConfig;

  // Detections
  List<DetectionResult> _detections = [];
  List<DetectionResult> get detections => _detections;

  // Mock Timer for Web
  Timer? _mockWebTimer;

  AppState() {
    if (!kIsWeb) {
      _startListeningToUsbStatus();
    }
  }

  Future<void> initNativeTexture() async {
    if (_textureId != null) return;
    try {
      final int id = await _commandChannel.invokeMethod('createTexture');
      _textureId = id;
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to create native texture: $e');
    }
  }

  Future<void> initDepthTexture() async {
    if (_depthTextureId != null) return;
    try {
      final int id = await _commandChannel.invokeMethod('createDepthTexture');
      _depthTextureId = id;
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to create native depth texture: $e');
    }
  }

  void _startListeningToUsbStatus() {
    _usbStatusChannel.receiveBroadcastStream().listen((dynamic event) {
      _usbStatus = event.toString();
      notifyListeners();
    }, onError: (dynamic error) {
      debugPrint("USB Status error: $error");
    });
  }

  void setStageConfig(StageConfig config) async {
    _stageConfig = config;
    if (kIsWeb) {
      notifyListeners();
      return;
    }
    
    try {
      await _commandChannel.invokeMethod('setStage', {'stage': config.currentStage.index + 1});
    } on PlatformException catch (e) {
      debugPrint("Failed to set stage: '${e.message}'.");
    }
    notifyListeners();
  }

  void togglePipeline() async {
    if (_isPipelineRunning) {
      if (kIsWeb) {
        _stopWebMock();
      } else {
        try {
          await _commandChannel.invokeMethod('stopPipeline');
        } on PlatformException catch (e) {
          debugPrint("Failed to stop pipeline: '${e.message}'.");
        }
      }
      _isPipelineRunning = false;
    } else {
      _isPipelineRunning = true;
      if (kIsWeb) {
        _startWebMock();
      } else {
        try {
          await _commandChannel.invokeMethod('startPipeline');
          _startListeningToTelemetry();
        } on PlatformException catch (e) {
          debugPrint("Failed to start pipeline: '${e.message}'.");
          _isPipelineRunning = false;
        }
      }
    }
    notifyListeners();
  }

  void _startWebMock() {
    _mockWebTimer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      if (!_isPipelineRunning) return;
      _telemetry = TelemetryData(
        fps: 15.0 + (DateTime.now().millisecondsSinceEpoch % 5) / 10,
        p50LatencyMs: 30.0,
        p95LatencyMs: 45.0,
        p99LatencyMs: 60.0,
        cpuUsage: 25.0,
        gpuUsage: 10.0,
        npuUsage: 40.0,
        thermalState: 35.0,
        batteryLevel: 98.0,
        droppedFrames: 0,
        usbTransportLatencyMs: 2.0,
        centerDistanceMeters: 1.25,
      );

      // Removed static mock detection per user request
      _detections = [];
      notifyListeners();
    });
  }

  void _stopWebMock() {
    _mockWebTimer?.cancel();
    _mockWebTimer = null;
    _detections = [];
  }

  void _startListeningToTelemetry() {
    _telemetryChannel.receiveBroadcastStream().listen((dynamic event) {
      if (!_isPipelineRunning) return;
      try {
        final Map<String, dynamic> data = jsonDecode(event as String);
        _telemetry = TelemetryData(
          fps: (data['fps'] as num?)?.toDouble() ?? 0.0,
          p50LatencyMs: (data['p50'] as num?)?.toDouble() ?? 0.0,
          p95LatencyMs: (data['p95'] as num?)?.toDouble() ?? 0.0,
          p99LatencyMs: (data['p99'] as num?)?.toDouble() ?? 0.0,
          cpuUsage: (data['cpu'] as num?)?.toDouble() ?? 0.0,
          gpuUsage: (data['gpu'] as num?)?.toDouble() ?? 0.0,
          npuUsage: (data['npu'] as num?)?.toDouble() ?? 0.0,
          thermalState: (data['thermal'] as num?)?.toDouble() ?? 0.0,
          batteryLevel: (data['battery'] as num?)?.toDouble() ?? 100.0,
          centerDistanceMeters: (data['centerDistance'] as num?)?.toDouble() ?? 0.0,
          droppedFrames: (data['droppedFrames'] as num?)?.toInt() ?? 0,
        );

        if (data.containsKey('detections')) {
          final List<dynamic> detList = data['detections'];
          _detections = detList.map((d) {
            final bboxList = (d['bbox'] as List<dynamic>?)?.map((e) => (e as num).toDouble()).toList() ?? [0.0, 0.0, 0.0, 0.0];
            final int classId = d['class'] as int? ?? -1;
            final String className = (classId >= 0 && classId < cocoClasses.length) 
                ? cocoClasses[classId] 
                : 'Object $classId';
                
            return DetectionResult(
              objectName: className,
              confidence: (d['confidence'] as num?)?.toDouble() ?? 0.0,
              distanceMeters: (d['distanceMeters'] as num?)?.toDouble() ?? 0.0,
              directionClock: '',
              boundingBox: bboxList,
            );
          }).toList();
        } else {
          _detections = [];
        }
        notifyListeners();
      } catch (e) {
        debugPrint("Error parsing telemetry: $e");
      }
    }, onError: (dynamic error) {
      debugPrint("Telemetry error: $error");
    });
  }
}
