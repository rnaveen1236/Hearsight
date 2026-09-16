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
                                child: Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    FittedBox(
                                      fit: BoxFit.cover,
                                      child: SizedBox(
                                        width: 640,
                                        height: 480,
                                        child: state.textureId != null ? Texture(textureId: state.textureId!) : const SizedBox(),
                                      ),
                                    ),
                                    // Center Crosshair and HUD aligned to RGB Feed
                                    Center(
                                      child: Column(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          const Icon(Icons.add, color: Colors.greenAccent, size: 48),
                                          const SizedBox(height: 8),
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                            decoration: BoxDecoration(
                                              color: Colors.black87,
                                              borderRadius: BorderRadius.circular(8),
                                              border: Border.all(color: Colors.greenAccent.withOpacity(0.5)),
                                            ),
                                            child: Column(
                                              children: [
                                                Text(
                                                  "${state.telemetry.centerDistanceMeters.toStringAsFixed(2)}m",
                                                  style: const TextStyle(
                                                    color: Colors.greenAccent,
                                                    fontSize: 24,
                                                    fontWeight: FontWeight.bold,
                                                  ),
                                                ),
                                                Text(
                                                  "Latency: ${state.telemetry.p50LatencyMs.toStringAsFixed(1)} ms",
                                                  style: const TextStyle(
                                                    color: Colors.yellowAccent,
                                                    fontSize: 12,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              // Bottom Half: Depth Feed
                              Expanded(
                                child: FittedBox(
                                  fit: BoxFit.cover,
                                  child: SizedBox(
                                    width: 640,
                                    height: 480,
                                    child: state.depthTextureId != null ? Texture(textureId: state.depthTextureId!) : const SizedBox(),
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
                  Column(
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
                      Text('FPS: ${state.telemetry.fps.toStringAsFixed(1)} | Latency: ${state.telemetry.p95LatencyMs.toStringAsFixed(1)}ms', style: const TextStyle(color: Colors.white70)),
                    ],
                  ),
                  
                  // Removed Stage Config Dropdown per user request
                  
                  // Start / Stop Button
                  ElevatedButton.icon(
                    onPressed: state.isCameraConnected ? () => context.read<AppState>().togglePipeline() : null,
                    icon: Icon(state.isPipelineRunning ? Icons.stop : Icons.play_arrow),
                    label: Text(state.isPipelineRunning ? 'STOP' : 'START'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: state.isPipelineRunning ? Colors.red : Colors.teal,
                      foregroundColor: Colors.white,
                    ),
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
}
