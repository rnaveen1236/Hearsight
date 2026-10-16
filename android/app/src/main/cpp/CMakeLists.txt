cmake_minimum_required(VERSION 3.10.2)

project("hsv2_lite")

# ============================================================
# RealSense Android build configuration
# ============================================================

set(FORCE_RSUSB_BACKEND ON CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_GRAPHICAL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_WITH_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_WITH_TM2 OFF CACHE BOOL "" FORCE)

add_subdirectory(librealsense)

# ============================================================
# Compiler configuration
# ============================================================

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions")

include_directories(
        librealsense/include
)

# ============================================================
# QNN SDK
# ============================================================

set(QNN_SDK_ROOT
        "/home/naveen/Downloads/qairt/2.47.0.260601"
)

set(QNN_INCLUDE_DIR
        "${QNN_SDK_ROOT}/include"
)

set(QNN_ANDROID_LIB_DIR
        "${QNN_SDK_ROOT}/lib/aarch64-android"
)

include_directories(
        ${QNN_INCLUDE_DIR}
        ${QNN_INCLUDE_DIR}/QNN
        ${QNN_INCLUDE_DIR}/QNN/System
)

# ============================================================
# HSV2 Lite native library
# ============================================================

add_library(
        hsv2_lite
        SHARED

        native-lib.cpp
        qnn_runtime.cpp
        yolo_detector.cpp
        imu.cpp
        tracker.cpp
)

# ============================================================
# Android system libraries
# ============================================================

find_library(
        log-lib
        log
)

find_library(
        android-lib
        android
)

# ============================================================
# QNN runtime libraries
#
# These are supplied through android/app/src/main/jniLibs/
# and are loaded dynamically by qnn_runtime.cpp.
#
# We intentionally do NOT link the QNN shared libraries here.
# ============================================================

target_compile_options(
        hsv2_lite
        PRIVATE
        -fexceptions
        -frtti
)

# ============================================================
# Link dependencies
# ============================================================

target_link_libraries(
        hsv2_lite

        realsense2

        ${log-lib}
        ${android-lib}

        dl
)
