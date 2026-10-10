# Sago — Implementation Plan

Implements the *Sago Complete Project Development Plan* (phases 0–9).
Sago is **not** a predefined-command assistant: it understands what the user means, plans the steps
itself, executes them in Android apps, and verifies the result before reporting success.

This is a build plan. Nothing here is claimed as implemented unless marked ✅.

---

## 0. Core idea in one loop

```text
speech ─▶ transcript ─▶ GOAL (what the user wants + how we know it's done)
                              │
          ┌───────────────────▼────────────────────┐
          │ OBSERVE  fresh screen context          │
          │ PLAN     Gemma proposes ONE next step  │
          │ VALIDATE schema · preconditions · risk │
          │ EXECUTE  API → intent → node → gesture │
          │ VERIFY   compare observed vs expected  │
          │ RECOVER  replan / safe retry / ask     │
          └───────────────────┬────────────────────┘
                              ▼
                 done (with evidence) → speak result
```

Fixed phrases are never the mechanism. A small **fast path** may short-circuit trivially
unambiguous system requests (back, home, volume), but every other request goes through this loop.

---

## 1. Module layout (`sago/`)

| Gradle module | Responsibility | Android deps? |
|---|---|---|
| `:core:model` | Data contracts (Goal, ScreenSnapshot, UiElement, ProposedStep, ActionResult, TaskMemory) | No — pure Kotlin |
| `:llm` | LiteRT-LM Gemma engine, model manager, prompt builders, structured-output parser | Yes |
| `:voice` | Mic capture, wake word, Whisper STT, Android TTS, audio focus | Yes |
| `:nlu` | Transcript normalizer, goal builder, reference resolver, clarification policy | Mostly pure |
| `:screen` | AccessibilityService, event hub, window selector, screen model builder, classifier, freshness, screenshot/MediaProjection, OCR, vision | Yes |
| `:navigation` | App registry, action executor (method ladder), node actions, gestures, scroll-finder, intents | Yes |
| `:agent` | Task loop, planner, verifier, recovery policy, task memory | Mostly pure |
| `:safety` | Action policy, confirmations, sensitive-content detection, redaction | Pure |
| `:telemetry` | Metrics, redacted logs | Yes |
| `:eval` | Offline evaluation harness (recorded screens + utterances → expected step) | JVM |
| `:app` | Assistant services (VoiceInteractionService), onboarding, permissions, settings, debug view | Yes |

Pure-Kotlin modules (`core:model`, `nlu`, `agent`, `safety`, `eval`) are unit-tested on the JVM
without a phone — this is where most logic should live.

### Reuse map

| Source | Take | Into |
|---|---|---|
| `sago/` (this repo) ✅ written, not yet built | VoiceInteractionService + session, Whisper JNI, recorder, Speaker, AppRegistry fuzzy match, CallManager, SystemControls, IntentParser | `:app`, `:voice`, `:navigation` |
| `opendroid/` (Apache-2.0) | LiteRTLMProvider, OnDeviceModelRegistry, ModelDownloadWorker, ModelArtifactIntegrity, LiteRtCompatibility; system/calendar/media/call/notification actions; WhatsApp/SMS automators; Call/Media verifiers; ActionRisk | `:llm`, `:navigation`, `:agent`, `:safety` (mark files "modified from OpenDroid") |
| Your existing Sago app (not in repo yet) | ScreenClassifier, BrowserAdapter, ScreenGuard, SagoScreenUnderstandingEngine, DynamicUIAgent, ContactResolver | Audit in Phase 0, merge into `:screen`, `:navigation`, `:safety` |

---

## 2. Data contracts (`:core:model`)

```kotlin
data class Goal(
    val text: String,                    // "Open YouTube and find machine learning tutorials"
    val targetApp: String?,              // "YouTube"
    val entities: Map<String, String>,   // query=machine learning tutorials
    val constraints: List<String>,       // ["free", "shorter", "only Tamil"]
    val successCondition: String,        // "YouTube search results for the query are visible"
    val language: String,                // "ta-en"
    val ambiguity: String?,              // non-null → ask before acting
    val originalTranscript: String,
)

data class UiElement(
    val index: Int, val role: Role, val label: String, val state: String?,
    val value: String?,                  // null when sensitive
    val actions: Set<UiAction>, val region: Region, val container: String?,
    val listPosition: String?, val source: Source,   // ACCESSIBILITY | OCR | VISION
    val confidence: Float, val nodeRef: NodeRef?, val bounds: Rect,
)

data class ScreenSnapshot(
    val app: String, val packageName: String, val title: String?, val type: ScreenType,
    val elements: List<UiElement>, val focused: Int?, val keyboardOpen: Boolean,
    val dialogOnTop: Boolean, val scrollable: Boolean, val visualSummary: String?,
    val fingerprint: String, val observedAtMs: Long, val status: Freshness, // READY|UPDATING|STALE|UNAVAILABLE
    val sensitive: Boolean,
)

data class ProposedStep(                 // what Gemma proposes; app validates
    val thought: String,
    val action: ActionType,              // OPEN_APP, CLICK, TYPE, SUBMIT, SCROLL, BACK, HOME, WAIT, READ, ASK_USER, DONE, FAIL …
    val target: Int?,                    // element index
    val text: String?,
    val precondition: String,            // "search field visible"
    val expectedResult: String,          // "results list shows query"
    val needsConfirmation: Boolean,
)

sealed interface ActionResult {
    data class Changed(val before: String, val after: ScreenSnapshot, val events: List<String>) : ActionResult
    data class NoEffect(val reason: String) : ActionResult
    data class Blocked(val reason: String) : ActionResult       // permission, secure window, policy
    data class Failed(val reason: String) : ActionResult
}

data class TaskMemory(
    val goal: Goal, val steps: List<StepRecord>, val visibleItems: List<String>,
    val pending: String?, val lastSnapshotFingerprint: String?, val unresolved: String?,
)
```

---

## 3. Phase-by-phase implementation

### Phase 0 — Audit (1–2 days)

| Task | Output |
|---|---|
| Push your existing Sago app to GitHub; inventory its classes | `docs/AUDIT.md` table: component · works? · reuse/merge/replace |
| Build `sago/` (this repo) in Android Studio; fix compile errors | Baseline APK |
| Verify the Gemma file: E2B/E4B, `.task` vs `.litertlm`, **image input yes/no**, latency, RAM | Model facts sheet |
| Measure Whisper (Tamil/English/Tanglish) WER on 50 recorded utterances | STT baseline |
| Record current behaviour on 10 sample requests | Baseline video/log |

**Already known:** OpenDroid screenshot bug (missing `canTakeScreenshot`), self-overlay bug in both
OpenDroid and your Sago, OpenDroid LiteRT provider is text-only (see `OPENDROID_ANALYSIS.md`).

**Exit:** prioritized backlog; baseline build runs on the OnePlus 15.

### Phase 1 — Voice interaction (2–3 days) · `:voice`

| Class | Detail |
|---|---|
| `MicController` | Permission flow, start/stop, 1 s silence end, 12 s max, cancel, audio-focus loss → cancel |
| `WakeWordService` | Mic foreground service + QS tile; Porcupine/openWakeWord; added after the button flow works |
| `WhisperStt` ✅ (written) | Keep original transcript + language guess |
| `Speaker` ✅ (written) | Tamil / en-IN by script; QUEUE_FLUSH on interrupt |
| `SpeechPolicy` | Short acknowledgements; milestone narration only; error phrases |

**Tests:** permission denied → spoken guidance; silence → "I didn't hear anything"; interruption → cancel cleanly.
**Exit:** 50-utterance set recognised with measured WER; no crash on deny/interrupt.

### Phase 2 — Natural-language understanding (3–4 days) · `:nlu`, `:llm`

| Class | Detail |
|---|---|
| `TranscriptNormalizer` | Tanglish spelling variants, number words, filler removal; keeps the original |
| `EntityResolver` | Fuzzy match against **installed apps** and **contacts**; returns candidates + scores |
| `GoalBuilder` | Gemma → `Goal` JSON (schema below); uses current app + task memory |
| `ClarificationPolicy` | Ask when top-2 candidates are close, a required slot is missing, or the goal is risky + vague |
| `StructuredOutput` | Strict JSON parse → schema validation → one repair retry → else ask user |

GoalBuilder prompt contract:
```text
Current app: Settings. Recent task: none.
User said: "Phone settings-ku po, Wi-Fi off pannu"
Return JSON: {"goal","target_app","entities","constraints","success_condition","ambiguity"}
```

**Tests (`:eval`):** 150 utterances (English/Tamil/Tanglish, informal, ASR errors) → expected Goal fields.
**Exit:** ≥ 90% correct goals; paraphrases of the same request yield the same goal.

### Phase 3 — Universal navigation engine (4–6 days) · `:navigation`

**Method ladder** (`ActionExecutor`):
1. Android API / intent (launch app, settings panels, media session, alarm…)
2. Accessibility node action (`ACTION_CLICK`, `SET_TEXT`, `SCROLL_*`, `IME_ENTER`, `EXPAND`, `SET_PROGRESS`)
3. Nearest clickable ancestor / custom action
4. Gesture at element bounds (tap, long-press, swipe)
5. Vision-located target (Phase 4) → gesture
6. `Blocked`/`Failed` with reason

| Class | Detail |
|---|---|
| `AppRegistry` ✅ (written) | PackageManager query, fuzzy names, live refresh |
| `NodeActions` | Re-resolve `nodeRef` right before acting |
| `Gestures` | dispatchGesture library |
| `ScrollFinder` | ≤ 15 scrolls, stop on unchanged fingerprint |
| `SettleWaiter` | Expected event / 300 ms quiet / 2 s timeout |
| `SystemNav` | back, home, recents, notifications, quick settings |
| `FailureDetector` | Missing target, loading, blocking dialog, unexpected screen, launch failure, denial |

**Exit:** 15 navigation tasks across ≥ 6 apps (incl. 2 with no Sago-specific code) succeed without scripted sequences.

### Phase 4 — Live screen understanding (4–6 days) · `:screen`

| Class | Detail |
|---|---|
| `SagoAccessibilityService` | Explicit event list; forwards to `EventHub`; config includes `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot` |
| `EventHub` | Filter, debounce (300 ms), settle signal, change classification |
| `WindowSelector` | **Excludes Sago's own windows/overlays**; picks top app window; tracks dialog/keyboard |
| `ScreenModelBuilder` | Nodes → numbered `UiElement`s (wrapper collapse, roles, labels, states, regions, list positions), ≤ 40 in prompt view |
| `ScreenClassifier` | Merge from your existing app |
| `AssistStructureSource` | `VoiceInteractionSession.onHandleAssist` structure of the app behind Sago (needs "Use text from screen") |
| `VisualSource` | Accessibility `takeScreenshot()` on demand; **MediaProjection** only if continuous visual capture is required (per-session consent, `mediaProjection` FGS type) |
| `OcrEngine` | ML Kit (Latin) + Tesseract `tam` |
| `VisionDescriber` | Gemma vision if the model supports images, else a small VLM; Set-of-Marks for unlabeled controls |
| `FreshnessTracker` | READY/UPDATING/STALE/UNAVAILABLE; refresh before selecting an element if stale |
| `Redactor` | Password/OTP/PIN/card fields never leave `:screen` unredacted |

Refresh rules: accessibility state on meaningful events (target ≈ 500 ms, measure it); visual summary only on change or demand (target 1–2 s); never per frame.

**Exit:** "what's on my screen" correct in 10 apps; **never describes Sago itself**; stale context detected after UI changes.

### Phase 5 — Reasoning ↔ execution loop (3–4 days) · `:agent`

| Class | Detail |
|---|---|
| `TaskLoop` | understand → observe → plan → validate → execute → verify → continue/recover |
| `StepPlanner` | Gemma: goal + success condition + last 5 steps + screen prompt → one `ProposedStep` |
| `StepValidator` | Action allowed? target exists and supports it? text present? preconditions plausible? |
| `Verifier` | Compares `expectedResult` with the new snapshot/events; produces evidence |
| `RecoveryPolicy` | Refresh → diagnose → safe retry only for idempotent actions → alternative → ask |
| `LoopGuard` | ≤ 15 steps, same fingerprint 3× → stop, 60 s budget, cancel on "stop" |

Rules: one step per observation; never report "done" without verifier evidence; never repeat a
non-idempotent action (send, submit, purchase) after an uncertain outcome.

**Exit:** 10 multi-step tasks incl. unexpected popups complete with verified evidence; 0 false "done".

### Phase 6 — Conversation memory (2–3 days) · `:agent`

| Class | Detail |
|---|---|
| `TaskMemoryStore` | Active goal, constraints, steps, visible items, pending step, last fingerprint |
| `ReferenceResolver` | "the second one", "next", "not this", "same again", "only free ones" → applied to memory + fresh screen |
| `Reconciler` | Memory vs fresh snapshot; if the list changed, re-identify items before acting |

**Exit:** follow-up set (30 cases) modifies the active task correctly ≥ 85%.

### Phase 7 — Permissions & safety (2–3 days) · `:safety`, `:app`

| Item | Implementation |
|---|---|
| Onboarding | Mic, assistant role, accessibility, "Use text from screen", notifications; explains each |
| `ActionPolicy` | Every `ProposedStep` → allow / confirm / block. Confirm: send, post, call, delete, purchase, settings-security, permission grants. Block: payments, passwords, OTP/PIN, CAPTCHA, banking apps |
| No YOLO | There is no mode that skips confirmations |
| Model output is a proposal | Only enum actions from the schema can run; no code execution |
| Revocation | Accessibility/capture revoked → context UNAVAILABLE, spoken explanation |
| Logs | Redacted: roles, timings, counts — never screen text or transcripts by default |

**Exit:** red-team set (40 cases) → 0 unconfirmed risky actions, 0 sensitive values spoken or logged.

### Phase 8 — Performance (2–3 days) · `:telemetry`

Measure first: STT, NLU, planner, screen build, OCR/vision, action, end-to-end, CPU/RAM/battery, crashes.
Then: keep Gemma loaded; prompt budget (≤ 40 elements, ≤ 60 chars/label, 5 steps history);
reuse unchanged snapshots; timeouts on every stage; inference off the main thread.

**Exit:** a measured performance report on the OnePlus 15 and targets set from real numbers.

### Phase 9 — Cross-app validation (4–5 days) · `:eval` + device

| Suite | Content |
|---|---|
| A NLU | 150 utterances, paraphrases, ASR errors |
| B Navigation | Settings, YouTube, WhatsApp (read-only), Chrome, Gmail, Maps, Clock, Files + **3 apps with no Sago code** |
| C Live screen | Changing lists, popups, loading, keyboard, visual text |
| D Context | 30 follow-up requests |
| E Failure/security | Denied permissions, missing elements, secure screens, stale context, offline, crash, cancel |

Metrics: task completion, element-selection accuracy, intent accuracy, verification accuracy,
recovery rate, clarification rate, latency, crash/resource use. Report per app, including failures and why.

**Exit:** repeatable evidence of what works and where it fails. No "fully universal" claim.

---

## 4. Order of work

```text
P0 audit ─▶ P1 voice ─▶ P2 NLU ─┐
                                ├─▶ P5 loop ─▶ P6 memory ─▶ P7 safety hardening ─▶ P8 perf ─▶ P9 validation
P3 navigation ─▶ P4 screen ─────┘
(:core:model, :safety basics and :eval are started in P0 and grow every phase)
```

Total ≈ 27–39 working days, matching the blueprint's phase estimates.

## 5. Definition of done (first release)

- Voice → goal → loop works for navigation + screen understanding in ≥ 6 apps plus unfamiliar apps.
- Every success is verified; every failure is explained.
- Safety: 0 unconfirmed risky actions, 0 sensitive leaks.
- Measured latency and resource report exists.
- Known limitations documented per app.
