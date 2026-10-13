#!/usr/bin/env bash
# Builds Intel's librealsense Android wrapper (AAR) from source and copies it to
# app/libs/librealsense.aar, which the HearSight app needs for the RealSense camera.
#
# Requirements (install from Android Studio > SDK Manager > SDK Tools):
#   - Android SDK, NDK (side by side) and CMake
#   - JDK 17 (or 11 for older librealsense releases), git, curl, unzip
#   - ANDROID_HOME (or ANDROID_SDK_ROOT) pointing at the SDK
#
# Usage: scripts/build_librealsense_aar.sh [librealsense-git-tag]   (default v2.55.1)
set -euo pipefail

TAG="${1:-v2.55.1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/librealsense-${TAG}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [ -z "$SDK" ]; then
  echo "Set ANDROID_HOME to your Android SDK (Android Studio > Settings > Android SDK)." >&2
  exit 1
fi

if [ ! -d "$WORK" ]; then
  git clone --depth 1 --branch "$TAG" https://github.com/IntelRealSense/librealsense.git "$WORK"
fi

cd "$WORK/wrappers/android"
echo "sdk.dir=$SDK" > local.properties
echo "== librealsense Android wrapper build files =="
grep -rhE "com.android.tools.build:gradle|ndkVersion|compileSdk|distributionUrl" \
  build.gradle */build.gradle gradle/wrapper/gradle-wrapper.properties 2>/dev/null || true

# Intel's repository doesn't include gradle-wrapper.jar, so ./gradlew can't start.
# Download the Gradle version the wrapper asks for and run it directly instead.
if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  GRADLE=./gradlew
  chmod +x gradlew
else
  URL="$(grep '^distributionUrl' gradle/wrapper/gradle-wrapper.properties | cut -d= -f2- | sed 's#\\:#:#g')"
  DIST="$WORK/gradle-dist"
  if [ ! -d "$DIST" ]; then
    echo "Downloading $URL"
    mkdir -p "$DIST"
    curl -fsSL "$URL" -o "$DIST/gradle.zip"
    unzip -q "$DIST/gradle.zip" -d "$DIST"
  fi
  GRADLE="$(ls -d "$DIST"/gradle-*/bin/gradle | head -n 1)"
fi
echo "Using $GRADLE with JAVA_HOME=${JAVA_HOME:-system java}"
"$GRADLE" --no-daemon assembleRelease

AAR="$(find . -path '*build/outputs/aar/*release*.aar' | head -n 1)"
if [ -z "$AAR" ]; then
  echo "No AAR produced; check the Gradle output above." >&2
  exit 1
fi
mkdir -p "$ROOT/app/libs"
cp "$AAR" "$ROOT/app/libs/librealsense.aar"
echo "Copied $AAR -> app/libs/librealsense.aar. Rebuild the app to enable the camera."
