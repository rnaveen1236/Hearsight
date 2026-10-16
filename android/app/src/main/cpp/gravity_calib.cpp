#include "gravity_calib.h"
#include "rigid.h"

#include <algorithm>
#include <cmath>

namespace {

constexpr float kRadToDeg = 57.2957795f;

float angleDeg(const float a[3], const float b[3]) {
    const float d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    return std::acos(std::max(-1.0f, std::min(1.0f, d))) * kRadToDeg;
}

}  // namespace

void GravityCalib::reset() {
    pairs_.clear();
    lastAdd_ = -1e9;
    sinceSolve_ = 0;
    ok_ = false;
    spreadDeg_ = residualDeg_ = 0;
}

void GravityCalib::add(const float phoneUp[3], const float camUp[3], double tSec) {
    if (tSec - lastAdd_ < minIntervalSec) return;
    lastAdd_ = tSec;
    Pair p;
    for (int i = 0; i < 3; ++i) { p.p[i] = phoneUp[i]; p.c[i] = camUp[i]; }
    pairs_.push_back(p);
    if (pairs_.size() > maxSamples) pairs_.erase(pairs_.begin());
    if (++sinceSolve_ >= 10 && pairs_.size() >= minSamples) {
        sinceSolve_ = 0;
        solve();
    }
}

void GravityCalib::solve() {
    // Orientation spread: largest angle between any sample and the mean.
    float mean[3] = {0, 0, 0};
    for (const Pair& p : pairs_) for (int i = 0; i < 3; ++i) mean[i] += p.p[i];
    const float ml = std::sqrt(mean[0] * mean[0] + mean[1] * mean[1] + mean[2] * mean[2]);
    if (ml < 1e-6f) return;
    for (float& v : mean) v /= ml;
    spreadDeg_ = 0;
    for (const Pair& p : pairs_) spreadDeg_ = std::max(spreadDeg_, angleDeg(p.p, mean));

    // Horn: B = sum c * p^T ; N (4x4) from B; R from N's top eigenvector.
    double S[3][3] = {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
    for (const Pair& p : pairs_)
        for (int i = 0; i < 3; ++i)
            for (int j = 0; j < 3; ++j) S[i][j] += static_cast<double>(p.p[i]) * p.c[j];
    float R[9];
    hornRotation(S, R);

    double se = 0;
    for (const Pair& p : pairs_) {
        float c[3];
        for (int i = 0; i < 3; ++i) c[i] = R[i * 3] * p.p[0] + R[i * 3 + 1] * p.p[1] + R[i * 3 + 2] * p.p[2];
        const float e = angleDeg(c, p.c);
        se += e * e;
    }
    residualDeg_ = static_cast<float>(std::sqrt(se / pairs_.size()));
    for (int i = 0; i < 9; ++i) R_[i] = R[i];
    ok_ = spreadDeg_ >= minSpreadDeg && residualDeg_ <= maxResidualDeg;
}

bool GravityCalib::toCamera(const float phone[3], float cam[3]) const {
    if (!ok_) return false;
    for (int i = 0; i < 3; ++i)
        cam[i] = R_[i * 3] * phone[0] + R_[i * 3 + 1] * phone[1] + R_[i * 3 + 2] * phone[2];
    return true;
}
