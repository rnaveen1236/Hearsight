import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:provider/provider.dart';
import 'package:camera/camera.dart';
import '../providers/app_state.dart';
import '../models/models.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  CameraController? _cameraController;
  bool _isCameraInitialized = false;
  String _cameraError = "";

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _initMockWebCamera();
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        Provider.of<AppState>(context, listen: false).initNativeTexture();
        Provider.of<AppState>(context, listen: false).initDepthTexture();
      });
    }
  }

  Future<void> _initMockWebCamera() async {
    try {
      final cameras = await availableCameras();
      if (cameras.isNotEmpty) {
        _cameraController = CameraController(
          cameras.first,
          ResolutionPreset.medium,
          enableAudio: false,
        );
        await _cameraController!.initialize();
        if (mounted) {
          setState(() {
            _isCameraInitialized = true;
          });
        }
      } else {
        if (mounted) {
          setState(() {
            _cameraError = "No cameras found by the browser.";
          });
        }
      }
    } catch (e) {
      debugPrint("Error initializing web camera: $e");
      if (mounted) {
        setState(() {
          _cameraError = "Camera Error: $e\nCheck browser permissions.";
        });
      }
    }
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // 1. Camera Feeds (RGB and Depth Side-by-Side)
          Positioned.fill(
            child: kIsWeb && _isCameraInitialized && _cameraController != null
                ? SizedBox(
                    width: double.infinity,
                    height: double.infinity,
                    child: FittedBox(
                      fit: BoxFit.cover,
                      child: SizedBox(
                        width: _cameraController!.value.previewSize?.height ?? 1,
                        height: _cameraController!.value.previewSize?.width ?? 1,
                        child: CameraPreview(_cameraController!),
                      ),
                    ),
                  )
                : (!kIsWeb && state.textureId != null && state.isCameraConnected)
                    ? Stack(
                        fit: StackFit.expand,
                        children: [
                          Column(
                            children: [
                              // Top Half: RGB Feed
                              Expanded(
                                child: ClipRect(
                                child: LayoutBuilder(
                                  builder: (context, constraints) => Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    FittedBox(
                                      fit: BoxFit.cover,
                                      clipBehavior: Clip.hardEdge,
                                      child: SizedBox(
                                        width: 640,
                                        height: 480,
                                        child: state.textureId != null ? Texture(textureId: state.textureId!) : const SizedBox(),
                                      ),
                                    ),
                                    ..._detectionLabels(
                                      context,
                                      state.detections,
                                      constraints.biggest,
                                      MediaQuery.of(context).padding.top,
                                    ),
                                    // Moving-object alert (time-to-collision), below the status bar.
                                    if (state.telemetry.alert != 'none')
                                      Positioned(
                                        top: MediaQuery.of(context).padding.top + 4,
                                        left: 0,
                                        right: 0,
                                        child: Center(child: _motionAlertBanner(state.telemetry)),
                                      ),
                                    // IMU panel: compact, top-left under the alert banner
                                    // (the top of the view is ceiling; the bottom shows
                                    // the floor, steps and low obstacles).
                                    Positioned(
                                      top: 120,
                                      left: 8,
                                      // Tap to zero (level the mount offset).
                                      child: GestureDetector(
                                        onTap: () => context.read<AppState>().zeroImu(),
                                        child: _imuPanel(state.telemetry.imu),
                                      ),
                                    ),
                                  ],
                                ),
                                ),
                                ),
                              ),
                              // Bottom Half: Depth Feed
                              Expanded(
                                child: ClipRect(
                                child: FittedBox(
                                  fit: BoxFit.cover,
                                  clipBehavior: Clip.hardEdge,
                                  child: SizedBox(
                                    width: 640,
                                    height: 480,
                                    child: state.depthTextureId != null ? Texture(textureId: state.depthTextureId!) : const SizedBox(),
                                  ),
                                ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      )
                    : Container(
                        color: Colors.black87,
                        child: const Center(
                          child: Text(
                            "WAITING FOR NATIVE REALSENSE FEED",
                            style: TextStyle(color: Colors.white54, letterSpacing: 2),
                          ),
                        ),
                      ),
          ),
          
          // 2. Removed static detections mapped in position (replaced by overlay in CustomPaint)

          // 3. Floating Control Panel Overlay
          Positioned(
            left: 16,
            bottom: 16,
            right: 16,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.7),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.white24),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Status & Metrics Column
                  Expanded(child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.usb,
                            color: _getStatusColor(state.usbStatus),
                            size: 16,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            state.usbStatus,
                            style: TextStyle(color: _getStatusColor(state.usbStatus), fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text('FPS ${state.telemetry.fps.toStringAsFixed(0)} · ${state.telemetry.p95LatencyMs.toStringAsFixed(0)} ms${state.recording ? ' · REC' : ''}',
                          style: TextStyle(color: state.recording ? Colors.redAccent : Colors.white70)),
                      if (state.streamError != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(state.streamError!, style: const TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
                        ),
                    ],
                  )),
                  
                  // Removed Stage Config Dropdown per user request
                  
                  // View switch (camera / overlay / segmentation) + Start / Stop
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                  IconButton(
                    tooltip: state.recording ? 'Stop recording' : 'Record walk',
                    onPressed: state.isPipelineRunning ? () => context.read<AppState>().toggleRecording() : null,
                    icon: Icon(state.recording ? Icons.stop_circle : Icons.fiber_manual_record,
                        color: state.recording ? Colors.redAccent : Colors.white70),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => context.read<AppState>().cycleViewMode(),
                    icon: const Icon(Icons.layers),
                    label: Text(AppState.viewModeLabels[state.viewMode]),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      foregroundColor: Colors.white,
                      side: const BorderSide(color: Colors.white54),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton.icon(
                    onPressed: state.isCameraConnected ? () => context.read<AppState>().togglePipeline() : null,
                    icon: Icon(state.isPipelineRunning ? Icons.stop : Icons.play_arrow),
                    label: Text(state.isPipelineRunning ? 'STOP' : 'START'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      backgroundColor: state.isPipelineRunning ? Colors.red : Colors.teal,
                      foregroundColor: Colors.white,
                    ),
                  ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Color _getStatusColor(String status) {
    if (status == 'CONNECTED') return Colors.greenAccent;
    if (status == 'REQUESTING PERMISSION') return Colors.orangeAccent;
    if (status == 'DISCONNECTED' || status == 'PERMISSION DENIED') return Colors.redAccent;
    return Colors.grey;
  }

  // STOP (red) / CAUTION (amber) banner: Layer 1 safety or moving objects.
  Widget _motionAlertBanner(TelemetryData t) {
    final stop = t.alert == 'stop';
    final dist = t.alertDist > 0 ? ' ${t.alertDist.toStringAsFixed(1)} m' : '';
    final ttc = t.alertTtc > 0 ? ' · ${t.alertTtc.toStringAsFixed(1)} s' : '';
    final what = switch (t.alertReason) {
      'obstacle' => 'obstacle ahead$dist',
      'head' => 'head-height obstacle$dist',
      'dropoff' => 'step down$dist',
      'stairs' => 'stairs ahead$dist',
      'unknown' => 'unknown ground$dist',
      'nofloor' => 'cannot see the floor',
      'moving' => 'object approaching$ttc',
      _ => 'hazard',
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: stop ? Colors.redAccent : Colors.orangeAccent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '${stop ? 'STOP' : 'CAUTION'} · $what',
        style: const TextStyle(color: Colors.black, fontSize: 18, fontWeight: FontWeight.bold),
      ),
    );
  }

  // Live D455 IMU readout: orientation + sample rates.
  Widget _imuPanel(ImuData imu) {
    const style = TextStyle(color: Colors.white, fontSize: 11, fontFeatures: [FontFeature.tabularFigures()]);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: imu.running ? Colors.cyanAccent.withValues(alpha: 0.6) : Colors.white24),
      ),
      child: imu.running
          // One line; tap to zero.
          ? Text('IMU${imu.source.isNotEmpty ? ' ${imu.source}' : ''}  P ${imu.pitchDeg.toStringAsFixed(0)}°  R ${imu.rollDeg.toStringAsFixed(0)}°',
              style: style.copyWith(color: Colors.cyanAccent, fontSize: 11))
          : const Text('IMU off', style: style),
    );
  }



  static const _labelStyle = TextStyle(
    color: Colors.black,
    fontSize: 14,
    fontWeight: FontWeight.bold,
  );

  // Class name + distance tags for the native-drawn boxes. bbox is
  // [x, y, w, h] in 640x480 camera pixels; the feed is shown with
  // BoxFit.cover in a [view]-sized area, so map through the same scale/crop.
  // Labels are placed greedily in confidence order without overlapping:
  // above the box, else stepping down inside it, else just below it; a
  // label with no free spot is skipped. All stay below [topInset].
  List<Widget> _detectionLabels(BuildContext context,
      List<DetectionResult> detections, Size view, double topInset) {
    const srcW = 640.0, srcH = 480.0;
    const labelHeight = 24.0, labelMaxWidth = 220.0, padX = 6.0, gap = 2.0;
    final scale = (view.width / srcW) > (view.height / srcH)
        ? view.width / srcW
        : view.height / srcH;
    final dx = (view.width - srcW * scale) / 2;
    final dy = (view.height - srcH * scale) / 2;
    final maxY = view.height - labelHeight;

    final sorted = detections.where((d) => d.boundingBox.length >= 4).toList()
      ..sort((a, b) => b.confidence.compareTo(a.confidence));

    // Measure with the same theme font and text scale the Text widget uses.
    final measureStyle = DefaultTextStyle.of(context).style.merge(_labelStyle);
    final textScaler = MediaQuery.textScalerOf(context);

    final placed = <Rect>[];
    final widgets = <Widget>[];

    for (final det in sorted) {
      final left = det.boundingBox[0] * scale + dx;
      final top = det.boundingBox[1] * scale + dy;
      final bottom = (det.boundingBox[1] + det.boundingBox[3]) * scale + dy;

      // Box entirely cropped out of view: no label.
      if (bottom < topInset || top > view.height) continue;

      final distance = det.distanceMeters > 0
          ? '${det.distanceMeters.toStringAsFixed(1)} m'
          : '-- m';
      // Approaching objects show their closing speed.
      final motion = det.approaching ? ' · ↓${det.speed.toStringAsFixed(1)} m/s' : '';
      final text = '${det.objectName} · $distance$motion';

      // Fit the background to the text.
      final painter = TextPainter(
        text: TextSpan(text: text, style: measureStyle),
        maxLines: 1,
        textDirection: TextDirection.ltr,
        textScaler: textScaler,
      )..layout();
      // +2 px so rounding never triggers the ellipsis.
      final width = (painter.width.ceilToDouble() + 2 * padX + 2).clamp(0.0, labelMaxWidth);
      final x = left.clamp(0.0, (view.width - width).clamp(0.0, double.infinity));

      // Candidate rows: above the box, then down through the box, then below.
      final candidates = <double>[top - labelHeight];
      for (var y = top; y <= bottom - labelHeight; y += labelHeight + gap) {
        candidates.add(y);
      }
      candidates.add(bottom + gap);

      Rect? spot;
      for (final y in candidates) {
        if (y < topInset || y > maxY) continue;
        final r = Rect.fromLTWH(x, y, width, labelHeight);
        if (!placed.any((p) => p.inflate(gap).overlaps(r))) {
          spot = r;
          break;
        }
      }
      if (spot == null) continue;
      placed.add(spot);

      widgets.add(Positioned(
        left: spot.left,
        top: spot.top,
        child: Container(
          width: width,
          height: labelHeight,
          padding: const EdgeInsets.symmetric(horizontal: padX),
          alignment: Alignment.centerLeft,
          color: det.approaching
              ? (det.ttc > 0 && det.ttc < 2.0 ? Colors.redAccent : Colors.orangeAccent)
              : Colors.greenAccent,
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: _labelStyle,
          ),
        ),
      ));
    }

    return widgets;
  }
}
