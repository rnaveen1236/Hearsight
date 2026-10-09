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
5. Push a Gemma model (Day 2). Any `.task` file in the models dir works, e.g. Gemma 4 E4B or Gemma 3n E4B
   from huggingface.co/litert-community:
   ```bash
   adb push gemma-3n-E4B-it-int4.task /sdcard/Android/data/com.hearsight.sago/files/models/
   ```
6. Long-press power (or tap **Test Sago**) and try:
   - "What is machine learning?"
   - "WhatsApp open pannu"
   - "Amma-ku call pannu" (add the nickname on the setup screen first)
   - "Volume konjam reduce pannu", "torch on pannu"
   - "YouTube-la Anirudh songs search pannu"

   Without a Gemma model Sago just echoes what it heard.

Logs: `adb logcat -s SagoVIS SagoSession SagoWhisper SagoGemma`.

Uninstall removes the app, its permissions, the assistant setting and the model.
