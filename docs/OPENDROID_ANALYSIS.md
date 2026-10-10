# OpenDroid vs. Sago screen plan

What `opendroid/` (upstream commit `6ff5a06`, Apache-2.0) gives Sago, what is missing, and what to change.
Based on reading the source, not on running it.

## 1. What OpenDroid is

A Kotlin/Compose Android agent (Hilt, Room, WorkManager, minSdk 26, targetSdk 36):

```text
Voice (SpeechRecognizer) / chat ─▶ IntentClassifier ─▶ PlanManager (LLM builds a full plan up front)
   ─▶ user approves plan (OFF / AUTO / YOLO modes) ─▶ AgentLoop runs steps
   ─▶ ActionDispatcher ─▶ 129 named actions (SystemActions, CommunicationActions, MediaActions …)
   ─▶ OpenDroidAccessibilityService (find text, click, type, scroll, tap coordinates, screenshot)
   ─▶ ReEvaluationEngine re-plans on failure
LLMs: cloud (OpenAI, Claude, Gemini, DeepSeek, …) or on-device (LiteRT-LM .litertlm, Ollama, AICore Gemma)
```

## 2. Reuse / adapt / replace

| OpenDroid part | Verdict | Why |
|---|---|---|
| `core/llm/LiteRTLMProvider`, `OnDeviceModelRegistry`, `ModelDownloadWorker`, `ModelArtifactIntegrity`, `LiteRtCompatibility` | **Reuse** | Gemma 4 E2B/E4B `.litertlm` download (resumable, SHA-256, runtime check) and on-device inference already done |
| `actions/*` system, calendar, alarm, media, call, SMS, notification actions | **Reuse** (selected) | Concrete Android API/intent implementations — the "System API / Intent" rungs of Sago's capability ladder |
| `accessibility/WhatsAppAutomator`, `SmsAutomator`, `TelegramAutomator`, `CallFlowVerifier`, `MediaPlaybackVerifier` | **Reuse as app adapters** | Working recipes + verification for common apps |
| `core/agent/ContactResolver`, `AliasResolver` | **Compare/merge** | Sago already has a phonetic Tamil-aware resolver; take the better parts |
| `core/agent/ActionRisk`, `AutoApprovalPolicy`, `neverAutoApprove` | **Adapt** | Good base for ScreenGuard, but **remove YOLO** (it bypasses `neverAutoApprove`) |
| `AccessibilityNodeTraversal.screenText()` | **Replace** | Flat text dump: no roles, states, positions, visibility filter or element numbers |
| `OpenDroidAccessibilityService` observation via `rootInActiveWindow` | **Replace** | Only the active window; no window filtering → reads its own floating button/app (same bug as Sago's "THINKING…" answer) |
| `PlanManager` plan-everything-first + `ReEvaluationEngine` | **Replace with observe→act loop** | Plans before seeing intermediate screens; breaks on popups/ads/unknown layouts. Sago needs one action per observation |
| `VisionEngine` | **Adapt** | Sends screenshots to a vision LLM; the on-device LiteRT provider here is **text-only**, so it effectively needs a cloud model |
| `core/voice/WakeWordDetector` | **Replace** | Continuous `SpeechRecognizer` loop — battery-heavy, often cloud, weak offline Tamil. Use Porcupine/openWakeWord + Whisper (Sago) |
| `TextToSpeechEngine` (ElevenLabs option) | **Replace** with Android TTS | Keep everything offline; Tamil voice |
| Floating button overlay | **Replace** with Sago `VoiceInteractionService` session | Sago is the system assistant |
| `social/*`, `SOCIAL_*`, `BOOK_UBER/OLA`, `ORDER_FOOD`, `SMART_HOME`, macros/routines UI | **Drop** for now | Out of scope for Hearsight |
| `PAY_UPI`, `CHECK_BALANCE`, `SPLIT_BILL`, `INSTALL_APP`, `RESTART_DEVICE`, `DELETE_FILE`, `CLEAR_BROWSER_DATA` | **Remove / block** | Sago never performs payments or destructive actions autonomously |

## 3. Bugs and risks found in the code

| # | Finding | Location | Fix |
|---|---|---|---|
| 1 | `takeScreenshot()` is called but the service config lacks `android:canTakeScreenshot="true"` → screenshots always fail, so `ANALYZE_SCREENSHOT` / vision silently return nothing | `res/xml/accessibility_service_config.xml`, `OpenDroidAccessibilityService.takeScreenshotAndEncode` | Add the attribute |
| 2 | Observation uses `rootInActiveWindow` only; own overlay/app not excluded | `getScreenText()` | Walk `windows`, skip own package and `TYPE_ACCESSIBILITY_OVERLAY`, pick top non-own app window; or use `VoiceInteractionSession.onHandleAssist` structure |
| 3 | Screen text has no structure (roles, states, bounds, numbering) | `AccessibilityNodeTraversal` | Sago screen model (numbered elements) |
| 4 | No settle/verify after generic UI actions (`findAndClick` returns "dispatched", not "worked") | `AccessibilityNodeTraversal`, `ActionDispatcher` | Event-driven settle + verifier for every action |
| 5 | YOLO mode auto-runs `neverAutoApprove` actions (payments, calls, deletes) | `AutoApprovalPolicy.shouldAutoApprove` | Remove YOLO for Sago; risky actions always need spoken "yes" |
| 6 | `typeAllMask` + `flagDefault` config processes every event | accessibility config | Explicit event list + debounce |
| 7 | Wide permission set (SMS read/send, background location, WRITE_SETTINGS, Shizuku, storage) | `AndroidManifest.xml` | Keep only what Sago's features need |

## 4. How it maps to the Sago screen plan

| Sago plan component | OpenDroid has | Gap |
|---|---|---|
| Event Hub | Raw events → floating button state | Debounce, settle signal, toasts, dialogs, page-load |
| Screen model (numbered elements) | Flat text | **Build** |
| Context selector (screen / scene / knowledge) | IntentClassifier (action vs chat) | Scene + temporal routing |
| Goal agent (observe → think → guard → act → check) | Plan-first AgentLoop | **Rebuild loop**; reuse dispatcher + actions |
| Capability router (API → intent → adapter → UI → gesture → vision) | ActionDispatcher with fallbacks | Formal ladder + verification |
| App/browser adapters | WhatsApp/SMS/Telegram automators, `OPEN_URL`, `WEB_SEARCH` | Browser page model, headings/links, tabs, forms |
| Verifier | Call + media verifiers | Generic verifier for all actions |
| Guard | ActionRisk + neverAutoApprove | Sensitive-app/password/OTP rules, no-content logging, remove YOLO |
| Vision (Set-of-Marks, grounded) | VisionEngine (cloud-oriented) | On-device vision, Set-of-Marks, grounding, privacy filter |
| Voice | SpeechRecognizer + ElevenLabs | Sago: wake word + Whisper + Android TTS (Tamil) |
| On-device Gemma | **Yes** (LiteRT-LM, model download, integrity) | Vision input not wired |

## 5. Integration plan

1. **Take from OpenDroid into `sago/`** (with "modified from OpenDroid" headers, Apache-2.0 §4):
   LiteRT-LM provider + model registry/downloader/integrity; selected system/calendar/media/call/notification actions; WhatsApp/SMS/Telegram automators and the call/media verifiers; ActionRisk.
2. **Build in Sago**: screen model + window filtering (fixes the self-overlay bug), event hub with settle, observe→act goal agent, generic verifier, ScreenGuard (no YOLO), browser adapter.
3. **Keep Sago's** VoiceInteractionService, wake word, Whisper STT, Android TTS, Tamil handling, Hearsight scene/safety.
4. **Drop**: social, ride/food ordering, payments, smart home, floating button, cloud TTS.
5. **Fix immediately if OpenDroid is run as-is**: `canTakeScreenshot="true"`, window filtering in `getScreenText()`, disable YOLO.
