# Sago (Android)

Voice assistant for Hearsight. Architecture and plan: [`../docs/SAGO_ARCHITECTURE.md`](../docs/SAGO_ARCHITECTURE.md).

## Build & run (Day 1)

1. Open this `sago/` folder in Android Studio. Install **NDK** and **CMake 3.22.1** from the SDK Manager
   if prompted. The first build downloads whisper.cpp (`v1.7.5`) from GitHub.
2. Run on the phone (arm64 only).
3. Push the Whisper model:
   ```bash
   adb push ggml-small.bin /sdcard/Android/data/com.hearsight.sago/files/models/ggml-small.bin
   ```
4. In the Sago app: **Allow microphone** → **Set Sago as digital assistant**
   (Default apps → Digital assistant app → Sago).
5. Long-press power (or tap **Test Sago**) and speak. Day 1 echoes back what it heard.

Logs: `adb logcat -s SagoVIS SagoSession SagoWhisper`.

Uninstall removes the app, its permissions, the assistant setting and the model.
