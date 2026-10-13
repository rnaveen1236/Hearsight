# HearSight

A navigation aid for blind and low-vision people that runs entirely on an Android phone.
An Intel RealSense depth camera plugs into the phone over USB-C. The app turns the live
depth stream into a 3D map of the floor and obstacles around the user, plans a safe path
and guides the user along it with vibration, stereo beeps and speech.

It is a simplified, open re-creation of the "Understanding the World in 3D" pipeline
shown by [.lumen](https://www.dotlumen.com/technology): floor detection from 3D points,
an obstacle/cost map, human-centred path planning and turn-by-turn haptic guidance.

```
 RealSense D4xx ──USB-C OTG──► OnePlus phone (everything below runs on the phone)
                               │
   depth 424x240 @ 30 fps ────►│ 1. depth → 3D points                    (navcore/Geometry.kt)
   accelerometer (D435i/D455) ►│ 2. floor plane: IMU gravity gives the   (navcore/Gravity.kt)
                               │    tilt, only the height is searched;
                               │    else RANSAC, "nothing solid below the floor"
   gyroscope (D435i/D455) ────►│ 2b. odometry: gyro predicts rotation,   (navcore/Odometry.kt)
                               │    point-to-plane ICP refines it and
                               │    measures movement → pose + red trail
                               │ 2c. 3D map fused over time: voxels in   (navcore/FusedMap.kt)
                               │    world coordinates, free space from
                               │    the depth image, ~30 s memory
   colour (outdoor mode) ─────►│ 2d. segmentation: SegFormer-B0 on the   (app/Segmenter.kt,
                               │    aligned colour image, ~5 Hz, labels   navcore/Semantics.kt)
                               │    road / sidewalk / grass / person /
                               │    vehicle → fused into the map
                               │ 3. 2.5D grid: floor / obstacle /        (navcore/Grid.kt)
                               │    drop-off / unknown + safety margin
                               │ 4. Dijkstra → goal ahead → straight     (navcore/Planner.kt)
                               │    segments → pure-pursuit target
                               │ 5. cue: STRAIGHT / LEFT / RIGHT / STOP  (navcore/Guidance.kt)
                               ▼
               vibration · stereo beeps · speech · live map   (app/)
```

## Project layout

| Path | What it is |
|---|---|
| `navcore/` | The whole navigation pipeline in pure Kotlin (no Android dependencies), with unit tests and a ray-cast depth camera simulator |
| `app/` | Android app: RealSense over USB, navigation thread, haptics/audio/speech, live map |
| `app/src/realsense/` | Camera code, compiled only when `app/libs/librealsense.aar` exists |
| `app/src/norealsense/` | Stub used without the AAR, so the app still builds and runs in demo mode |
| `scripts/build_librealsense_aar.sh` | Builds Intel's Android wrapper from source |
| `scripts/export_segmentation_model.py` | Exports the segmentation model to `app/src/main/assets/segmentation.onnx` |

## What you need

- An **Intel RealSense D455**. Other D4xx cameras work, but the defaults are tuned for
  the D455 (see *Using a different camera* below).
- An **Android phone with USB-C OTG** (OnePlus phones support it; Android 8.0+).
- A **USB-C to USB-C data cable**. The D455 has a USB-C port, so no adapter is needed. A
  USB 3 cable is better, but the app's 424x240 @ 30 fps depth stream also fits in
  USB 2.0. Short charge-only cables won't work: they carry no data.
- **Android Studio** (recent stable version, with JDK 17) on a laptop to build the app.

On some OnePlus models OTG switches itself off after 10 minutes of inactivity:
*Settings → Additional settings → OTG connection → On*.

## Build and run

1. **Open the project** in Android Studio (open the repository root, not `app/`).
2. **Demo mode first:** run the `app` configuration on the phone. Without the RealSense
   library the app starts in demo mode: a simulated camera walks through a corridor with
   boxes and a step down. Use this to check the map, vibration, beeps and speech.
3. **Enable the camera:** build Intel's Android wrapper once:
   ```bash
   export ANDROID_HOME=~/Android/Sdk          # macOS: ~/Library/Android/sdk
   scripts/build_librealsense_aar.sh           # clones librealsense v2.55.1, builds the AAR
   ```
   Install the **NDK** and **CMake** first (Android Studio → Settings → Android SDK →
   SDK Tools). The script copies the result to `app/libs/librealsense.aar`. Sync Gradle
   and run again; the app now uses the camera. If Intel's wrapper doesn't build with the
   default tag, try another release tag as the first argument and see
   `doc/android.md` in the librealsense repository.
4. **Add the segmentation model (for outdoor use).** On a laptop with internet:
   ```bash
   python -m venv .venv && . .venv/bin/activate
   pip install torch transformers onnx onnxruntime onnxscript
   python scripts/export_segmentation_model.py
   ```
   This downloads SegFormer-B0 trained on Cityscapes (street scenes, 15 MB) from Hugging
   Face, exports it to `app/src/main/assets/segmentation.onnx` and checks the ONNX output
   against PyTorch. Rebuild the app. Without the file the app works the same, just
   without road and person detection.
5. **Plug in the camera.** Android asks to open HearSight for the RealSense: tick
   *"Always open"*. Switch on **Outdoor** when you're outside, then press **Start
   guidance**.

Run the pipeline tests on any machine with JDK 17: `./gradlew :navcore:test` (the
Android SDK must be installed, because Gradle configures the `app` module too).

## Mounting and using it

- Mount the camera at **chest or head height, looking forward and slightly down**
  (15–40°). The D455 has a standard 1/4" tripod thread underneath, so a chest-mount
  phone or GoPro harness with a tripod adapter works well. It finds its height and tilt
  every frame, so no calibration is needed. Keep the floor in view.
- The phone powers the D455 over USB, which drains its battery noticeably. Start with a
  full charge, or use a power bank with a USB-C hub that supports OTG plus charging.
- Earbuds are recommended for the stereo beeps; the phone can be in a pocket or a chest
  pouch.
- **Screen off is fine.** Guidance runs in a foreground service that holds a CPU wake
  lock, so it keeps going with the screen off or the app in the background. A
  notification shows while it's running; its **Stop** button ends guidance. On Android
  13+ allow notifications when asked, or the notification and its Stop button stay
  hidden (guidance still runs; stop it from the app).

| Cue | Vibration | Beep | Meaning |
|---|---|---|---|
| STRAIGHT | none | none | Path ahead is clear: keep walking |
| LEFT | two short pulses | left ear | Turn left; repeats faster for sharper turns |
| RIGHT | one long pulse | right ear | Turn right; repeats faster for sharper turns |
| STOP | two long pulses | high tone, centre | No safe route at least 2 m long |

Speech announces turn changes ("Slight left", "Turn right"), obstacles straight ahead
("Obstacle, 1.5 meters"), step-downs ("Step down ahead"), and "Camera lost. Stop." if
depth frames stop arriving for more than 1 second.

## How the pipeline works (and how it maps to the dotLumen demo)

1. **Depth → 3D points.** Every second pixel is turned into a 3D point with the camera's
   intrinsics (about 25k points per frame).
2. **Floor plane.**
   - **With an IMU (D435i, D455):** the gyroscope rotates the "up" direction with the
     head at 200 Hz, and the accelerometer slowly corrects the gyro's drift (a
     complementary filter). Accelerometer samples far from 1 g are walking jolts and are
     skipped. In a simulated 20° head tilt while walking, accelerometer-only filtering
     lags 12° behind; the fused estimate is within 0.1°. The up
     direction fixes the floor's tilt, so only its height is searched: a histogram of
     every point's distance below the camera, scored the same way as below. A
     least-squares fit can then correct the IMU by up to 5°, which absorbs small
     camera-to-IMU misalignment. This still finds the floor when only a thin strip is
     visible, for example standing about 1.3 m from a wall, where depth alone fails.
   - **Without an IMU (plain D435), or while the IMU is settling:** RANSAC finds the
     floor, scoring candidate planes as *points on the plane minus points clearly below
     it*. Nothing solid can be below the floor, so table
   tops and tilted planes lose. The fit uses the nearest points first, so a floor that
   continues lower after a step doesn't drag the plane. A tracker smooths the plane over
   time and only accepts a sudden jump after 5 consecutive frames. This also gives the
   camera height and pitch.
3. **2.5D grid** (10 cm cells, 5 m ahead, ±2.5 m sideways). Each point is classified by
   its height above the floor:
   - floor (within ±6 cm);
   - obstacle (10 cm – 2 m high; this includes head-height obstacles a cane misses);
   - drop-off (more than 10 cm below the floor: steps, curbs, holes).

   Thresholds grow with distance to follow RealSense depth noise. A distance transform
   gives each cell its clearance. Cells within 30 cm of an obstacle are blocked, and a
   soft cost keeps the path away from the rest. This is the grey/black map in the video.
4. **Path.** Dijkstra from the user's feet. The goal is the cell that best combines "far
   ahead", "close to where the user faces" and "cheap to reach". A bonus for staying on
   last frame's side stops a centred obstacle from making the route flip left/right. Grid
   zig-zags are pulled into straight segments (the green line).
5. **Steering.** Pure pursuit: the point on the path 1.2 m away (the purple dot) gives
   the steering angle. It is smoothed, with a dead band and hysteresis, so the user isn't
   buzzed for every small wobble.

6. **Odometry (where am I, which way am I facing).** Depth-inertial odometry tracks
   the head's 6-DoF pose:
   - **Prediction:** the gyroscope (200 Hz) is integrated between depth frames to
     predict the rotation, plus constant velocity for the translation.
   - **Alignment:** point-to-plane ICP aligns the frame with a keyframe. Matches are
     found by projecting points into the keyframe's depth image, which is cheap enough
     for a phone. Normals are taken over a 3-pixel span to beat D455 depth noise.
   - **Gyro as a soft prior:** facing a single wall, a small turn and a sideways step
     look alike; the prior stops ICP trading one for the other.
   - **Degenerate directions:** any direction the geometry can't measure is flagged
     `degenerate` and keeps the prediction instead of drifting. A bare corridor is the
     classic case: forward motion is invisible to depth.
   - **Sanity limits:** a frame that moves more than 12 cm, or disagrees with the gyro by
     more than 10°, is reported `lost`, never as motion.
   - **Keyframes:** a new one after 30 cm or 12°, which limits drift.
   - **Step counter for corridors:** the accelerometer's vertical bounce counts steps.
     Cadence × step length gives walking speed, which replaces the prediction's
     horizontal-forward part. Depth alignment still overrides it wherever it can
     measure; where it can't (a bare corridor), the step-based distance is used. The
     step length starts at 0.65 m and is learned from walks where depth measured all
     motion. In simulation a 4.5 m walk down a bare corridor measures 4.0 m with steps,
     versus 0 m without.

   It outputs heading, distance walked and the red arrow trail under the user on the
   map. In simulation: 0.1–0.6% position error and under 1° rotation error over 1.8–4 m
   walks, and 7% position error during a fast 60°/s head turn.

7. **3D map fused over time.** Each frame is placed into an 8 × 8 m map that follows
   the user, using the odometry pose:
   - **Obstacles:** 3D voxels (10 cm), so overhangs and head-height obstacles keep
     their height.
   - **Floor and drop-offs:** tracked per map column.
   - **Clearing:** every occupied voxel in view is checked against the current depth
     image. If the camera now sees past it, it fades, so a person who walks away is
     erased within about 0.4 s. Out-of-view voxels are remembered, so a box beside you
     or behind you still blocks the path. When the lower part of a column is seen to be
     empty, its out-of-view upper part goes too, so a departed person doesn't leave a
     "ghost head" above the camera's view. A shelf above the view is kept, because
     nothing was ever under it.
   - **Forgetting:** everything fades with a 30 s half-life to forgive odometry drift.
     The map is cleared when tracking is lost.
   - **Planning:** the grid is sampled from this map, not from the current frame.
     `fuseMap = false` in `Params.kt` switches back to single-frame planning.

8. **Semantic segmentation (outdoor mode).**
   - **Model:** SegFormer-B0 trained on Cityscapes, running on the D455's colour
     camera. The colour image is aligned pixel for pixel to depth by librealsense.
   - **Runtime:** ONNX Runtime on the phone, on its own thread, whenever it's free
     (expect 100–250 ms per image, so about 5 updates a second). The depth loop never
     waits for it.
   - **Timing:** each label image goes back with its frame ID and is fused using that
     frame's own points and pose, so labels never land on the wrong geometry. Labels
     arriving more than 1 s late are dropped.
   - **Classes:** the 19 Cityscapes classes become walkway, road, terrain, person,
     vehicle, structure or sky. Floor-level points vote for each map column's ground
     type; points above the floor vote for the obstacle type. Low-confidence pixels
     (under 50%) are ignored.
   - **What the planner does with it:**
     - **Road:** extra cost per cell, so the planner stays on the sidewalk when it can.
     - **Grass:** mild extra cost.
     - **People:** 60 cm keep-out, instead of 30 cm around walls.
     - **Vehicles:** 80 cm keep-out.
   - **Speech:** "Person ahead", "Vehicle ahead", "Road ahead", and "Caution, path
     crosses road" when the only route uses the road.
   - **Safety:** labels never remove an obstacle; geometry still decides what's solid.
   - **Map colours:** road amber, grass green, people yellow, vehicles orange.
   - **Outdoor only:** Cityscapes has street scenes only, so the model runs only with
     the **Outdoor** switch on. Indoors it would likely call the floor "road".

The whole pipeline (floor, odometry, fused map, planning, guidance) takes about 15 ms per
frame on a desktop JVM at 424x240. Expect roughly 30–50 ms on a recent phone, with
segmentation running alongside on two other cores.

### D455 specifics

| | D455 | What the app does with it |
|---|---|---|
| Depth stream | 424x240 @ 30 fps | Works over USB 2.0 too; every second pixel is used (about 25k points) |
| Depth range | about 0.4–6 m | `minDepth` 0.4 m, `maxDepth` 6 m |
| Depth noise | about half a D435's (95 mm vs 50 mm stereo baseline) | `noiseK` 0.006, so a 15 cm curb is seen from 2.5 m away; with D435-level noise allowance it isn't |
| IMU | Accelerometer at 63 Hz, gyroscope at 200 Hz | Gravity sets the floor tilt (step 2); the gyro predicts rotation for odometry (step 6) |
| Field of view | 87° × 58° | Used if the device intrinsics can't be read |

### Using a different camera

For a D435 or D435i set `noiseK = 0.012f` in `Params.kt`; its depth is about twice as
noisy. A plain D435 has no IMU, so the app uses depth-only floor detection automatically.

### Tuning

All thresholds are in `navcore/src/main/kotlin/com/hearsight/navcore/Params.kt`:

| Parameter | Default | Effect |
|---|---|---|
| `bodyRadius` | 0.30 m | Hard keep-out distance around obstacles |
| `obstacleMinH` | 0.10 m | Smallest bump counted as an obstacle |
| `dropMinH` | 0.10 m | Smallest step down counted as a drop-off |
| `lookahead` | 1.2 m | Steering target distance: shorter is more responsive, longer is smoother |
| `deadbandDeg` | 8° | Steering angle still treated as "straight" |
| `minGoalDist` | 2.0 m | Below this route length the user is told to STOP |
| `imuMaxDeviationDeg` | 5° | How far the depth fit may correct the IMU's tilt |
| `gravityAlpha` | 0.05 | Gravity smoothing: lower is steadier, higher reacts faster to head tilt |

## 3-day plan

- **Day 1:** build and run demo mode; build the AAR; get live camera frames with the
  map showing the floor (check the camera height and pitch in the status line).
  On a D435i or D455, hold the camera level and still. The status line should show
  `floor: imu` and `IMU up (0.00, -1.00, 0.00)` give or take a few hundredths. Tilt
  it down and the third number should go negative. If the numbers look different,
  the IMU axes don't match the depth camera's: fix the axis order in
  `RealSenseCamera.readAccel`. The app falls back to depth-only floor detection when
  the up vector is implausible.
  Then check the gyroscope and odometry. Turn right on the spot: `heading` should go
  positive, about +90° after a quarter turn. If it goes negative, the gyro axes are
  flipped: negate them in `RealSenseCamera.readGyro`. Walk 3 m in a furnished room:
  the status distance should read about 3 m, with the red trail behind you on the map.
  Then check the map memory: walk slowly past a chair on your left. Once it's out of
  view, it should still be on the map beside you, red, and the path should keep clear
  of it. Have someone step in front of the camera and walk away: they should vanish
  from the map within about half a second.
  Walk 10 steps: the status line should show `steps … m/s` and a step length that
  settles near yours after walking in a furnished room. Turn the screen off mid-walk:
  cues should continue, and the notification's **Stop** should end guidance.
  Outdoors, with the model added and **Outdoor** on: the status line shows `seg … ms`.
  The road shows amber on the map and people yellow, and walking towards the road edge
  says "Road ahead".
- **Day 2:** mount the camera; walk indoor corridors and around furniture; tune
  `Params` (obstacle height, body radius, dead band) until the cues feel right.
- **Day 3:** test steps, doorways and people walking past; blindfolded trials *with a
  sighted spotter*; record what fails.

## Limitations: read before testing with users

This is a prototype, **not a safety device**. Always test with a sighted spotter, and
never rely on it instead of a cane or guide dog.

- The fused map is only as good as the odometry. Drift (corridors, long walks) shifts
  remembered obstacles slightly; they fade after about 30 s. Black, glass and very shiny
  objects return no depth: the map can't see them, and an object it saw earlier fades
  if the camera later gets no return from it within 3 m.
- In a bare corridor or open hall, depth can't see forward motion (status shows
  `degenerate`): distance then comes from the step counter. That is about 10% short in
  simulation, and depends on the step length having been learned in a furnished
  space first. Shuffling or very slow steps may not be counted. It also drifts slowly
  over long walks: there's no loop closure.
- The camera can't see the ground right at the user's feet. Very low obstacles there,
  glass, mirrors and black surfaces can be missed (RealSense limits).
- In bright sunlight the D4xx depth is noisier. Outdoor use needs more testing.
- Segmentation is a prototype too:
  - Cityscapes was filmed from a car, not from a pedestrian's chest, so expect mistakes.
  - There's no crosswalk class: a crossing is "road".
  - It can't judge traffic or signals.
  - **Never use this to decide when to cross a road.**
- There is no destination: it follows the open space ahead, like the demo.

## Next steps

1. **Visual odometry term.** Add the D455's colour or infrared image (with the IR
   projector off or interleaved) to measure forward motion in corridors directly,
   instead of relying on steps.
2. **Better segmentation.** Fine-tune on pedestrian-view data (e.g. Mapillary Vistas,
   which has crosswalks and curbs) and add indoor classes so it can run everywhere.
3. **Build a haptic headband.** An ESP32 with 3–5 vibration motors over BLE would give
   directional cues on the forehead, like the .lumen glasses.
