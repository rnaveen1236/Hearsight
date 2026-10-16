#include "gravity_calib.h"

#include <algorithm>
#include <cmath>

namespace {

constexpr float kRadToDeg = 57.2957795f;

// Eigen-decomposition of a symmetric 4x4 matrix (cyclic Jacobi); returns the
// eigenvector of the largest eigenvalue.
void largestEigenvector4(double A[4][4], double out[4]) {
    double V[4][4] = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    for (int sweep = 0; sweep < 50; ++sweep) {
        double off = 0;
        for (int p = 0; p < 4; ++p)
            for (int q = p + 1; q < 4; ++q) off += A[p][q] * A[p][q];
        if (off < 1e-18) break;
        for (int p = 0; p < 4; ++p) {
            for (int q = p + 1; q < 4; ++q) {
                if (std::fabs(A[p][q]) < 1e-15) continue;
                const double theta = (A[q][q] - A[p][p]) / (2 * A[p][q]);
                const double t = (theta >= 0 ? 1.0 : -1.0) /
                                 (std::fabs(theta) + std::sqrt(theta * theta + 1));
                const double c = 1 / std::sqrt(t * t + 1), s = t * c;
                for (int k = 0; k < 4; ++k) {
                    const double akp = A[k][p], akq = A[k][q];
                    A[k][p] = c * akp - s * akq;
                    A[k][q] = s * akp + c * akq;
                }
                for (int k = 0; k < 4; ++k) {
                    const double apk = A[p][k], aqk = A[q][k];
                    A[p][k] = c * apk - s * aqk;
                    A[q][k] = s * apk + c * aqk;
                }
                for (int k = 0; k < 4; ++k) {
                    const double vkp = V[k][p], vkq = V[k][q];
                    V[k][p] = c * vkp - s * vkq;
                    V[k][q] = s * vkp + c * vkq;
                }
            }
        }
    }
    int m = 0;
    for (int i = 1; i < 4; ++i)
        if (A[i][i] > A[m][m]) m = i;
    for (int k = 0; k < 4; ++k) out[k] = V[k][m];
}

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
    const double Sxx = S[0][0], Sxy = S[0][1], Sxz = S[0][2];
    const double Syx = S[1][0], Syy = S[1][1], Syz = S[1][2];
    const double Szx = S[2][0], Szy = S[2][1], Szz = S[2][2];
    double N[4][4] = {
        {Sxx + Syy + Szz, Syz - Szy, Szx - Sxz, Sxy - Syx},
        {Syz - Szy, Sxx - Syy - Szz, Sxy + Syx, Szx + Sxz},
        {Szx - Sxz, Sxy + Syx, -Sxx + Syy - Szz, Syz + Szy},
        {Sxy - Syx, Szx + Sxz, Syz + Szy, -Sxx - Syy + Szz}};
    double q[4];
    largestEigenvector4(N, q);
    const double w = q[0], x = q[1], y = q[2], z = q[3];
    const float R[9] = {
        static_cast<float>(w * w + x * x - y * y - z * z), static_cast<float>(2 * (x * y - w * z)),
        static_cast<float>(2 * (x * z + w * y)),
        static_cast<float>(2 * (x * y + w * z)), static_cast<float>(w * w - x * x + y * y - z * z),
        static_cast<float>(2 * (y * z - w * x)),
        static_cast<float>(2 * (x * z - w * y)), static_cast<float>(2 * (y * z + w * x)),
        static_cast<float>(w * w - x * x - y * y + z * z)};

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
