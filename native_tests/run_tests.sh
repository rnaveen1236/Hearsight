#!/bin/bash
# Host-side (PC) tests for the native safety layer and object tracker.
# Synthetic ray-cast depth scenes / simulated people; no phone needed.
set -e
cd "$(dirname "$0")"
SRC=../android/app/src/main/cpp
OUT=${TMPDIR:-/tmp}/hsv2_native_tests
mkdir -p "$OUT"
for t in test_safety test_walk test_walkstep; do
  g++ -std=c++17 -O2 -Wall -I"$SRC" "$t.cpp" "$SRC/safety.cpp" -o "$OUT/$t"
  echo "== $t"; "$OUT/$t"
done
# The tracker includes QNN headers (via yolo_detector.h).
QNN=${QNN_SDK_ROOT:-/home/naveen/Downloads/qairt/2.47.0.260601}/include
g++ -std=c++17 -O2 -Wall -I"$SRC" -I"$QNN" -I"$QNN/QNN" -I"$QNN/QNN/System" test_tracker.cpp "$SRC/tracker.cpp" -o "$OUT/test_tracker"
echo "== test_tracker"; "$OUT/test_tracker"
