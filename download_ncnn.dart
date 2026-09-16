import 'dart:io';
import 'package:archive/archive.dart';
import 'package:http/http.dart' as http;

Future<void> main() async {
  final cppDir = Directory('android/app/src/main/cpp');
  final assetsDir = Directory('android/app/src/main/assets');
  
  if (!cppDir.existsSync()) cppDir.createSync(recursive: true);
  if (!assetsDir.existsSync()) assetsDir.createSync(recursive: true);

  print('1. Downloading NCNN Android Vulkan SDK...');
  final ncnnUrl = 'https://github.com/Tencent/ncnn/releases/download/20240410/ncnn-20240410-android-vulkan.zip';
  final zipFile = File('ncnn-20240410-android-vulkan.zip');
  
  if (!zipFile.existsSync()) {
    final response = await http.get(Uri.parse(ncnnUrl));
    if (response.statusCode == 200) {
      zipFile.writeAsBytesSync(response.bodyBytes);
      print('Downloaded NCNN SDK.');
    } else {
      print('Failed to download NCNN SDK. Status: ${response.statusCode}');
      return;
    }
  } else {
    print('NCNN SDK Zip already exists. Skipping download.');
  }

  print('Extracting NCNN SDK...');
  final ncnnDir = Directory('android/app/src/main/cpp/ncnn-android-vulkan');
  if (!ncnnDir.existsSync()) {
    final bytes = zipFile.readAsBytesSync();
    final archive = ZipDecoder().decodeBytes(bytes);
    for (final file in archive) {
      final filename = file.name;
      if (file.isFile) {
        final data = file.content as List<int>;
        final outFile = File('android/app/src/main/cpp/$filename');
        outFile.createSync(recursive: true);
        outFile.writeAsBytesSync(data);
      } else {
        Directory('android/app/src/main/cpp/$filename').createSync(recursive: true);
      }
    }
    print('Extraction complete.');
  } else {
    print('NCNN directory already exists. Skipping extraction.');
  }

  print('2. Downloading YOLOv8-nano Weights...');
  final paramUrl = 'https://raw.githubusercontent.com/FeiGeChuanShu/ncnn-android-yolov8/main/app/src/main/assets/yolov8n.param';
  final binUrl = 'https://github.com/FeiGeChuanShu/ncnn-android-yolov8/raw/main/app/src/main/assets/yolov8n.bin';
  
  final paramFile = File('android/app/src/main/assets/yolov8n.param');
  final binFile = File('android/app/src/main/assets/yolov8n.bin');

  if (!paramFile.existsSync()) {
    final res = await http.get(Uri.parse(paramUrl));
    paramFile.writeAsBytesSync(res.bodyBytes);
    print('Downloaded yolov8n.param');
  }

  if (!binFile.existsSync()) {
    final res = await http.get(Uri.parse(binUrl));
    binFile.writeAsBytesSync(res.bodyBytes);
    print('Downloaded yolov8n.bin');
  }

  print('All NCNN assets are ready!');
}
