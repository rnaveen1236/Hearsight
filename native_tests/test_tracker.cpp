// Host test: synthetic people at 30 fps with stereo-like depth noise.
#include "tracker.h"
#include <cstdio>
#include <random>
const float fx = 600, cx = 320;
Detection makeDet(float X, float Z, std::mt19937& rng) {
    std::normal_distribution<float> n(0, 0.01f * Z * Z);   // depth noise grows with Z^2
    Detection d; d.classId = 0; d.confidence = 0.8f;
    float u = cx + X * fx / Z; float h = 1.7f * fx / Z, w = 0.5f * fx / Z;
    d.x = u - w / 2; d.y = 240 - h / 2; d.w = w; d.h = h; d.distanceM = Z + n(rng);
    return d;
}
void run(const char* name, float X0, float Z0, float vx, float vz, float secs) {
    ObjectTracker tr; tr.setIntrinsics(fx, cx); std::mt19937 rng(1);
    std::vector<TrackInfo> info; float firstStop = -1;
    for (int f = 0; f <= secs * 30; ++f) {
        float t = f / 30.0f, X = X0 + vx * t, Z = Z0 + vz * t;
        std::vector<Detection> d{makeDet(X, Z, rng)}; tr.update(d, t, info);
        if (tr.alert() == MotionAlert::Stop && firstStop < 0) firstStop = t;
        if (f % 30 == 0 || f == (int)(secs * 30)) {
            const TrackInfo& i = info[0];
            float trueTtc = vz < 0 ? Z / -vz : -1;
            printf("%-9s t=%.1fs Z=%.2f | id %d est v=(%.2f,%.2f) m/s ttc %.2f (true %.2f) alert %d\n", name, t, Z,
                   i.id, i.vx, i.vz, i.ttc, trueTtc, (int)tr.alert());
        }
    }
    printf("%-9s first STOP at t=%.2fs\n\n", name, firstStop);
}
int main() {
    run("approach", 0.1f, 6.0f, 0.0f, -1.3f, 3.5f);   // walking at the user, 1.3 m/s
    run("crossing", -3.0f, 3.0f, 1.3f, 0.0f, 4.0f);   // crossing left->right 3 m ahead
    run("standing", 0.3f, 2.5f, 0.0f, 0.0f, 3.0f);    // standing still
}
