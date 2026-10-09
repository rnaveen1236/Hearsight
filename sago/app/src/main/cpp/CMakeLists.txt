cmake_minimum_required(VERSION 3.22.1)
project(sago_native)

include(FetchContent)

# whisper.cpp is pulled at build time (pinned tag) so the repo stays small.
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.7.5
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper)

add_library(sago_whisper SHARED whisper_jni.cpp)
target_link_libraries(sago_whisper PRIVATE whisper android log)
