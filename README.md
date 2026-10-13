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

## What you need

- An **Intel RealSense D435, D435i, D455 or similar D4xx** camera.
- An **Android phone with USB-C OTG** (OnePlus phones support it; Android 8.0+).
- A **USB-C to USB-C cable** (or the RealSense USB cable plus a USB-C OTG adapter). A
  USB 3 cable and port is better, but the app's 424x240 @ 30 fps depth stream also fits
  in USB 2.0.
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
4. **Plug in the camera.** Android asks to open HearSight for the RealSense: tick
   *"Always open"*. Then press **Start guidance**.

Run the pipeline tests on any machine with JDK 17: `./gradlew :navcore:test` (the
Android SDK must be installed, because Gradle configures the `app` module too).

## Mounting and using it

- Mount the camera at **chest or head height, looking forward and slightly down**
  (15–40°). It finds its height and tilt from the floor every frame, so no calibration
  is needed. Keep the floor in view.
- Earbuds are recommended for the stereo beeps; the phone can be in a pocket or a chest
  pouch. The screen stays on while the app is open.

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
   - **With an IMU (D435i, D455):** the accelerometer stream is low-pass filtered into
     an "up" direction. Samples far from 1 g are walking jolts and are skipped. The up
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

The whole step takes about 7 ms per frame on a desktop JVM. Expect roughly 15–30 ms on
a recent phone.

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
- **Day 2:** mount the camera; walk indoor corridors and around furniture; tune
  `Params` (obstacle height, body radius, dead band) until the cues feel right.
- **Day 3:** test steps, doorways and people walking past; blindfolded trials *with a
  sighted spotter*; record what fails.

## Limitations: read before testing with users

This is a prototype, **not a safety device**. Always test with a sighted spotter, and
never rely on it instead of a cane or guide dog.

- The map is rebuilt every frame from what the camera sees right now. There is no
  memory of the surroundings (visual odometry or SLAM) yet, so obstacles that leave the
  field of view are forgotten.
- The camera can't see the ground right at the user's feet. Very low obstacles there,
  glass, mirrors and black surfaces can be missed (RealSense limits).
- In bright sunlight the D4xx depth is noisier. Outdoor use needs more testing.
- There is no semantic understanding yet: sidewalk, road and crosswalk all count as
  "floor". Do not use near traffic.
- There is no destination: it follows the open space ahead, like the demo.

## Next steps

1. **Use the gyroscope too.** Fusing it with the accelerometer (a complementary filter)
   would keep the up direction accurate during fast head turns and walking.
2. **Remember the surroundings.** Track head motion between frames (visual odometry) and
   fuse frames into a map that persists when the camera looks away.
3. **Recognise surfaces.** Run a TFLite segmentation model on the RealSense colour stream
   (sidewalk / road / crosswalk / stairs) and add it as a cost layer.
4. **Keep running with the screen off.** Move the navigation loop into a foreground
   service.
5. **Build a haptic headband.** An ESP32 with 3–5 vibration motors over BLE would give
   directional cues on the forehead, like the .lumen glasses.
