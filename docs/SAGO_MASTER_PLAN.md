# Sago — Master Plan (Sago + JARVIS + OpenDroid combined)

One plan that takes the best part of each codebase for every Sago module.
It supersedes the reuse map in `docs/SAGO_IMPLEMENTATION_PLAN.md` (branch `claude/fervent-keller-rwwqdg`);
the phases, data contracts and exit criteria there stay valid and are referenced, not repeated.

| Source | What it is | License |
|---|---|---|
| **Sago** (`sago/`, this project) | VoiceInteractionService assistant, Whisper STT (Tamil/English/Tanglish), Android TTS, Gemma intent parser, app/call/system routers | Ours |
| **JARVIS** ([ansaribilal14/jarvis](https://github.com/ansaribilal14/jarvis) @ `833a21d`) | On-device phone agent: observe → decide → act → verify loop, structured screen model, verifier, guards, safety | MIT |
| **OpenDroid** (`opendroid/` @ `6ff5a06`) | Android agent with LiteRT-LM Gemma runtime, 129 actions, app automators | Apache-2.0 |

---

## 1. The decision in one table

| Sago module | Base | Take from the others | Build new |
|---|---|---|---|
| `:voice` | **Sago** (Whisper, Speaker, VoiceInteractionService) | — (both alternatives are weaker for offline Tamil) | Wake word (Porcupine/openWakeWord), `MicController`, `SpeechPolicy` |
| `:llm` | **OpenDroid** `LiteRTLMProvider`, `OnDeviceModelRegistry`, `ModelDownloadWorker`, `ModelArtifactIntegrity`, `LiteRtCompatibility` | JARVIS: stop sequences, cancel flag on STOP, keep-loaded + stateless calls, compact-prompt mode for small models | Gemma image input (if the model supports it) |
| `:nlu` | **Sago** `IntentParser`, `ContactAliases`, `InstalledAppRegistry` | JARVIS: JSON salvage pipeline from `Planner.kt` → `StructuredOutput`; OpenDroid: compare `ContactResolver`/`AliasResolver` | `TranscriptNormalizer` (Tanglish), `GoalBuilder`, `ClarificationPolicy` |
| `:screen` | **JARVIS** `JarvisAccessibilityService` (multi-window, screenshots), `ScreenObservation` (numbered elements, `center=(x,y)`, 320-node/48-depth caps), accessibility config | OpenDroid: none (flat text, active window only, screenshot flag missing) | **Exclude Sago's own windows/overlays**, `EventHub` debounce + settle, `FreshnessTracker`, `AssistStructureSource`, Tamil OCR, vision/Set-of-Marks |
| `:navigation` | **JARVIS** `ElementResolver` (viewId → exact text → contains → desc → point), gestures awaiting `GestureResultCallback`, UiTools | **OpenDroid** system/calendar/alarm/media/call/notification actions + WhatsApp/SMS/Telegram automators (API/intent rungs and app adapters); Sago `AppLauncher`, `CallManager`, `SystemControls` | Formal method ladder `ActionExecutor`, `ScrollFinder`, `SettleWaiter`, `FailureDetector` |
| `:agent` | **JARVIS** loop design: one step per fresh observation, `Verifier` verdicts, `AgentStateMachine`, guards (repeat ×3, failures ×5, step budget, LLM deadlines, global STOP), rule-rescue fallback | OpenDroid: `CallFlowVerifier`, `MediaPlaybackVerifier` as app-specific verifiers | Split into `TaskLoop`/`StepPlanner`/`StepValidator`/`Verifier`/`RecoveryPolicy`/`LoopGuard` (do **not** copy the 1k-line `AgentEngine.kt`); `TaskMemory`, `ReferenceResolver` |
| `:safety` | **JARVIS** `RiskClassifier` (context escalation), `PasswordPolicy`, `InjectionGuard`, `ConfirmationManager` | OpenDroid `ActionRisk` + `neverAutoApprove` list (**without YOLO**) | Spoken "yes" confirmation, Tamil injection patterns, banking/payment block list |
| `:eval` | **JARVIS** JVM test suites as templates (resolver, parser, risk, state machine) | — | 150-utterance NLU set, recorded-screen replay harness |
| `:app` | **Sago** `SetupActivity`, assistant role | JARVIS: "Doctor" health screen idea, foreground-service STOP | Onboarding for all permissions |

**Explicitly not taken**
- JARVIS: llama.cpp JNI + GGUF catalog (Sago runs Gemma on LiteRT-LM), cloud providers, Telegram/Discord remote, routines, skill builder/recorder, Compose screens.
- OpenDroid: plan-everything-first `PlanManager`, flat `screenText()`, `SpeechRecognizer` wake word, ElevenLabs, floating button, YOLO mode, social/ride/food/payments/smart-home actions, extra permissions.
- **No touch recording.** JARVIS shipped four touch recorders (v1.9–v2.1) and all failed on real devices (`docs/SKILLS_V3.md` there). If Sago ever needs user-taught flows, use "pick on screenshot" + declarative steps.

---

## 2. Combined architecture

```text
 Long-press power / wake word / QS tile
            │
 ┌──────────▼───────────── :app + :voice  (Sago) ───────────────────┐
 │ SagoVoiceInteractionService → SagoSession → SpeechRecorder        │
 │ WhisperStt (ta/en/Tanglish) ·  Speaker (Android TTS)              │
 └──────────┬────────────────────────────────────────────────────────┘
            ▼ transcript
 ┌──────────────────────── :nlu (Sago + JARVIS salvage) ─────────────┐
 │ Normalizer → EntityResolver (apps, contacts) → GoalBuilder (Gemma)│
 │ StructuredOutput: strict parse → JARVIS salvage → 1 retry → ask   │
 │ ClarificationPolicy                                               │
 └──────────┬────────────────────────────────────────────────────────┘
            ▼ Goal (+ success condition)
 ┌──────────────────────── :agent (JARVIS design) ───────────────────┐
 │ fast path (back/home/volume, read notifications) ─ no LLM         │
 │ TaskLoop:  OBSERVE → PLAN ONE STEP → VALIDATE → GUARD → EXECUTE   │
 │            → VERIFY → (continue | RECOVER | ASK | DONE w/ evidence)│
 │ AgentStateMachine · LoopGuard · TaskMemory · ReferenceResolver    │
 └───┬──────────────┬───────────────┬──────────────┬─────────────────┘
     ▼              ▼               ▼              ▼
  :llm           :screen         :navigation     :safety
  OpenDroid      JARVIS a11y     ladder:         JARVIS RiskClassifier
  LiteRT-LM      service +       1 API/intent    + OpenDroid ActionRisk
  Gemma (keep    numbered        (OpenDroid      PasswordPolicy,
  loaded,        screen model,   actions, Sago   InjectionGuard,
  stop/cancel)   +own-window     routers)        ConfirmationManager
                 filter, OCR,    2 node action   (spoken yes), redaction
                 vision          (JARVIS         no YOLO, ever
                                 resolver)
                                 3 gesture (awaited)
                                 4 vision target
                                 5 Blocked/Failed
```

Rules (from all three, non-negotiable):
1. One action per fresh observation; a plan only guides.
2. The model proposes; the app validates against an enum schema. No code execution.
3. "Done" only with verifier evidence; uncertain non-idempotent actions (send, submit, call) are never retried.
4. Screen text is untrusted data; password/OTP/PIN/card values never reach the prompt, logs, or speech.
5. Sago never describes or acts on its own windows.

---

## 3. Phases (combined)

Contracts (`Goal`, `UiElement`, `ScreenSnapshot`, `ProposedStep`, `ActionResult`, `TaskMemory`) and
exit criteria are as in `SAGO_IMPLEMENTATION_PLAN.md` §2–3. Below: what each phase now takes from where.

### Phase 0 — Audit + import (2–3 days)
- Build `sago/` on the OnePlus 15; fix compile errors → baseline APK.
- Add `third_party/jarvis/UPSTREAM.md` (repo, commit `833a21d`, MIT notice). Import only the files listed in §4, each with a header `Adapted from JARVIS (MIT)`. OpenDroid files keep `Modified from OpenDroid (Apache-2.0)`.
- Gemma facts sheet (E2B/E4B, `.task` vs `.litertlm`, image input, latency, RAM).
- Whisper WER on 50 utterances; record 10 baseline requests.
- Start `:core:model` contracts, `:safety` skeleton, `:eval` harness.
- **Exit:** baseline APK, facts sheet, backlog.

### Phase 1 — Voice (2–3 days) · Sago
Keep `WhisperStt`, `Speaker`, session services. Add `MicController` (1 s silence end, 12 s max, audio-focus cancel), then wake word. JARVIS idea: a global STOP that cancels mic, LLM and actions together.

### Phase 2 — NLU (3–4 days) · Sago + OpenDroid `:llm` + JARVIS salvage
- Move Gemma to OpenDroid's LiteRT-LM provider + downloader + SHA-256 integrity.
- `StructuredOutput` = strict parse → **JARVIS salvage** (balanced-object extraction, key aliases, truncated-JSON repair) → one repair retry → ask the user.
- Merge contact resolvers (Sago phonetic Tamil + OpenDroid alias resolver).
- **Exit:** ≥ 90 % correct goals on 150 utterances.

### Phase 3 — Navigation (4–5 days) · JARVIS + OpenDroid actions
- Rung 1 (API/intent): OpenDroid system/calendar/alarm/media/call/notification actions + Sago `AppLauncher`, `CallManager`, `SystemControls`.
- Rungs 2–4: JARVIS `ElementResolver` re-resolution right before acting, node actions, gestures awaiting `GestureResultCallback` (4 s guard).
- App adapters: OpenDroid WhatsApp/SMS/Telegram automators (draft only; human/spoken-yes sends).
- **Exit:** 15 tasks across ≥ 6 apps, incl. 2 with no app-specific code.

### Phase 4 — Screen understanding (4–5 days) · JARVIS base
- Port JARVIS accessibility service + config (`canTakeScreenshot`, `flagRetrieveInteractiveWindows`, explicit event list) and `ScreenObservation` → `ScreenSnapshot`.
- **New:** skip own package and `TYPE_ACCESSIBILITY_OVERLAY` windows; `EventHub` (300 ms debounce, settle), `FreshnessTracker`, fingerprint, `AssistStructureSource`, ML Kit + Tesseract `tam` OCR, optional vision.
- Compact prompt view for Gemma: ≤ 40 elements, ≤ 60 chars per label (JARVIS compact mode pattern).
- **Exit:** "what's on my screen" correct in 10 apps; never describes Sago.

### Phase 5 — Agent loop (3–4 days) · JARVIS design, clean classes
- `TaskLoop`, `StepPlanner`, `StepValidator`, `Verifier` (VERIFIED / UNVERIFIED / FAILED / COULD_NOT_VERIFY with an "after" note fed to the next step), `RecoveryPolicy`, `LoopGuard` (≤ 15 steps, same fingerprint/action ×3 → stop, failures ×5 → give up, 60 s budget).
- `AgentStateMachine` copied almost as-is.
- Rule-rescue: after 2 unparseable model outputs, a deterministic planner takes the step or Sago asks.
- OpenDroid `CallFlowVerifier` / `MediaPlaybackVerifier` plug in as app-specific verifiers.
- **Exit:** 10 multi-step tasks with popups; 0 false "done".

### Phase 6 — Conversation memory (2–3 days) · new
`TaskMemoryStore`, `ReferenceResolver` ("second one", "next", "same again"), `Reconciler`. JARVIS redaction rules apply before anything is stored.
- **Exit:** ≥ 85 % on 30 follow-ups.

### Phase 7 — Safety (2–3 days) · JARVIS + OpenDroid lists
- `ActionPolicy` = JARVIS `RiskClassifier` context escalation (messaging → confirm, finance app → block) + OpenDroid `neverAutoApprove` list. **No YOLO/auto-approve mode.**
- `PasswordPolicy`, `InjectionGuard` (add Tamil/Tanglish patterns), redaction (13–19 digit numbers, `otp/pin/password: x`).
- `ConfirmationManager` with spoken "yes" + on-screen Confirm/Deny; timeout = decline.
- Trim manifest permissions to what features use.
- **Exit:** 40-case red team → 0 unconfirmed risky actions, 0 leaks.

### Phase 8 — Performance (2–3 days)
Measure STT / NLU / planner / screen build / action / end-to-end. Keep Gemma loaded, stateless calls, stop sequences, cancel on STOP (JARVIS), timeouts on every stage, live progress in the session UI.

### Phase 9 — Cross-app validation (4–5 days)
Suites A–E from the implementation plan; add JARVIS-style JVM suites to CI (`testDebugUnitTest` on every push).

---

## 4. File import list

| From | File | To | Change needed |
|---|---|---|---|
| JARVIS | `accessibility/JarvisAccessibilityService.kt` | `:screen` `SagoAccessibilityService` | Own-window filter, EventHub hooks, drop skill-recorder hooks |
| JARVIS | `res/xml/accessibility_service_config.xml` | `:screen` | Drop `typeTouchInteractionStart` |
| JARVIS | `core/observer/ScreenObservation.kt` | `:screen` `ScreenModelBuilder` | Map to `UiElement`/`ScreenSnapshot`, add roles/regions/freshness |
| JARVIS | `core/skills/ElementResolver.kt` (+ `ElementTarget` from `SkillModels.kt`) | `:navigation` `NodeActions` | Use `nodeRef` from snapshot |
| JARVIS | gesture helpers in `core/tools/impl/UiTools.kt` | `:navigation` `Gestures` | — |
| JARVIS | `core/agent/Verifier.kt`, `AgentStateMachine.kt` | `:agent` | Verdicts compare against `ProposedStep.expectedResult` |
| JARVIS | JSON salvage in `core/planner/Planner.kt` | `:nlu` `StructuredOutput` | Aliases for Sago's action enum |
| JARVIS | `core/safety/RiskClassifier.kt`, `PasswordPolicy.kt`, `InjectionGuard.kt` | `:safety` | Indian banking/UPI packages, Tamil patterns |
| JARVIS | `core/agent/ConfirmationManager.kt` | `:safety` | Spoken yes via Sago session |
| JARVIS | `src/test/*` (UiResolve, RiskClassifier, AgentStateMachine, PlannerJson, InjectionGuard) | `:eval` / module tests | Re-target to Sago classes |
| OpenDroid | `core/llm/LiteRTLMProvider`, `OnDeviceModelRegistry`, `ModelDownloadWorker`, `ModelArtifactIntegrity`, `LiteRtCompatibility` | `:llm` | Remove Hilt, keep loaded |
| OpenDroid | `actions/*` (system, calendar, alarm, media, call, notification) | `:navigation` rung 1 | Drop risky/out-of-scope actions |
| OpenDroid | `accessibility/WhatsAppAutomator`, `SmsAutomator`, `TelegramAutomator` | `:navigation` adapters | Draft-only; send needs confirmation |
| OpenDroid | `CallFlowVerifier`, `MediaPlaybackVerifier` | `:agent` | Implement `Verifier` interface |
| OpenDroid | `core/agent/ActionRisk`, `neverAutoApprove` | `:safety` | Remove YOLO path |

---

## 5. Order and timeline

```text
P0 audit+import ─▶ P1 voice ─▶ P2 NLU (+:llm) ─┐
                                               ├─▶ P5 loop ─▶ P6 memory ─▶ P7 safety ─▶ P8 perf ─▶ P9 validation
P3 navigation ─▶ P4 screen ───────────────────┘
```

≈ 28–38 working days. Importing JARVIS/OpenDroid code saves most of the build time in P3–P5 and P7;
the time goes into adapting it to Sago's contracts and into what neither project has
(own-window filtering, Tamil NLU/OCR, follow-up memory, spoken confirmations).

## 6. Definition of done (first release)
- Voice → goal → verified loop works in ≥ 6 apps plus unfamiliar apps.
- Every success has verifier evidence; every failure is explained aloud.
- 0 unconfirmed risky actions, 0 sensitive leaks (red-team set).
- Sago never reads or acts on its own UI.
- Measured latency/resource report; per-app known limitations documented.
- Third-party notices for JARVIS (MIT) and OpenDroid (Apache-2.0) in the app's About screen.
