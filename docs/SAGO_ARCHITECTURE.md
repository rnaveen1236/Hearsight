# Sago — Architecture & Stage A Plan

Sago is the voice assistant for Hearsight, running on a **OnePlus 15 (CPH2745, Android 16 / OxygenOS 16)**.

**Stage A** (this plan): Sago is an installable APK set as the phone's *default digital assistant*.
No root, no flashing; uninstalling the APK removes Sago and its models completely.
**Stage B** (later, only if Stage A testing shows a real gap): build Sago into an AOSP system image.

---

## 1. Architecture

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    ONEPLUS 15 · Android 16 · OxygenOS                    │
│        Screen ON · Screen OFF · Locked · any app in the foreground       │
└──────────────┬───────────────────────────────────────┬───────────────────┘
               │ long-press power                      │ "Hey Sago" (Day 3)
               │                                       ▼
               │                        ┌──────────────────────────────┐
               │                        │ WakeWordService (mic FGS)    │
               │                        │ Porcupine → openWakeWord     │
               │                        └──────────────┬───────────────┘
               ▼                                       │ showSession()
┌──────────────────────────────────────────────────────▼───────────────────┐
│  ASSISTANT LAYER  (assistant/)                                           │
│  SagoVoiceInteractionService → SagoSessionService → SagoSession overlay  │
│  SagoRecognitionService (required for the Default-apps listing)          │
└──────────────────────────────────────┬───────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  SagoPipeline  (pipeline/)  — one turn, models kept warm between turns   │
│                                                                          │
│   SpeechRecorder ──▶ WhisperStt ──▶ Brain ──▶ (Action Router) ──▶ Speaker│
│   16 kHz mic,        whisper.cpp    Gemma     Day 2              Android │
│   energy endpoint    JNI, ta/en     Day 2                        TTS     │
│   (audio/)           (stt/, cpp/)   (brain/)                     (tts/)  │
└──────────────────────────────────────┬───────────────────────────────────┘
                                       │ BrainResult
                 ┌─────────────────────┴─────────────────────┐
                 ▼                                           ▼
          Answer(text) ──▶ TTS                  Action(name, params)
                                                             │
┌────────────────────────────────────────────────────────────▼─────────────┐
│  ACTION ROUTER  (router/, Day 2–3)                                       │
│                                                                          │
│  System        Apps                 Calls          Media      Notifs     │
│  volume,       InstalledAppRegistry ACTION_CALL +  Media-     Notif.     │
│  torch,        (PackageManager,     Contacts +     Session-   Listener-  │
│  home/back     fuzzy name match)    alias map      Manager    Service    │
│                     │                                                    │
│                     ▼                                                    │
│        Intent ──fail──▶ Deep link ──fail──▶ AccessibilityService         │
│                                             (find · click · type)        │
└──────────────────────────────────────────────────────────────────────────┘
                                       │
                          later: HearsightBridge (AIDL)
                 Hearsight Navigation Engine owns safety decisions;
                          Sago only speaks them.
```

### Rules

1. **Gemma decides, the Router executes.** The LLM never calls Android APIs directly.
2. **No per-app code** beyond the YouTube MVP script; apps are discovered, not hard-coded.
3. **Never bypass Android security.** Protected actions on the lock screen go through the keyguard.
4. **Models live in app-specific storage** (`/sdcard/Android/data/com.hearsight.sago/files/models/`) so uninstall removes them.

---

## 2. Three-day MVP plan

### Day 0 — downloads (evening before)
- [ ] Android Studio + NDK + CMake 3.22.1; `adb devices` sees the phone
- [ ] Whisper `ggml-small.bin` (multilingual, ~480 MB)
- [ ] Gemma 4 E4B LiteRT / MediaPipe `.task` (fallback: Gemma 3n E4B)
- [ ] Picovoice console: train "Hey Sago" for Android → `.ppn` + access key

### Day 1 — Sago becomes the assistant and can hear you  ✅ code in this branch
| Task | Done when |
|---|---|
| VoiceInteractionService + SessionService + RecognitionService + XML | Sago listed in **Default apps → Digital assistant** |
| Session overlay | Long-press power shows Sago over any app |
| Recorder + whisper.cpp JNI | "Amma-ku call pannu" appears as text |
| Android TTS | Sago speaks the reply (Day 1: echoes what you said) |

### Day 2 — Gemma + actions
| Task | Done when |
|---|---|
| `GemmaBrain` (MediaPipe LLM Inference) replacing `EchoBrain`; JSON `ACTION`/`ANSWER` prompt | "What is machine learning?" is answered |
| JSON parse with raw-text fallback | No crash on malformed output |
| `InstalledAppRegistry` + `OPEN_APP` | "WhatsApp open pannu", and a freshly installed app, both open |
| `CALL_CONTACT` + alias map | "Amma-ku call pannu" dials |
| Volume / mute / torch | All work by voice |

### Day 3 — wake word, media, YouTube, testing
| Task | Done when |
|---|---|
| Porcupine `WakeWordService` + Quick Settings tile | "Hey Sago" works screen on / off / locked |
| NotificationListener → MediaSession, notification summary | Play/pause/next in YouTube & Spotify |
| AccessibilityService: global actions + scripted YouTube search→play | "YouTube-la Anirudh song play pannu" plays |
| Test checklist below, bug fixes | All boxes ticked |

### Test checklist
```text
☐ Hey Sago — screen ON / screen OFF / locked
☐ Works over WhatsApp, YouTube, home
☐ English question → spoken answer
☐ Tanglish "WhatsApp open pannu"   ☐ Tamil "Amma-ku call pannu"
☐ Newly installed app opens
☐ Volume, mute, torch, home, back
☐ Play / pause / next              ☐ Read notifications
☐ YouTube search + play
☐ Uninstall → Sago fully gone
```

### Deferred to the rest of Stage A
WhatsApp send / generic UI planner · answer/reject calls, SMS · context ("send *this*") ·
HearsightBridge · Room memory · own openWakeWord model · Silero VAD · battery/thermal tuning ·
100-command accuracy suite · real `SagoRecognitionService` backed by Whisper.

---

## 3. Known Stage A limits
- After reboot, Android 14+ won't auto-start a microphone service — tap the Sago tile once.
- Sideloaded APK: **App info → ⋮ → Allow restricted settings** before enabling Accessibility / Notification access.
- OxygenOS: set **Battery → Unrestricted** for Sago and lock it in Recents.
- The green mic indicator stays on while the wake word is listening.
- While Sago is the assistant, it is also the system `SpeechRecognizer`; the Day 1 stub returns an error to other apps that use it.
- Banking/UPI and other secure screens block Accessibility by design.
