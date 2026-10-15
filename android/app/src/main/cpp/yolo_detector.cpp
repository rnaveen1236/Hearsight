#include "yolo_detector.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace {

float iou(const Detection& a, const Detection& b) {
    float x1 = std::max(a.x, b.x);
    float y1 = std::max(a.y, b.y);
    float x2 = std::min(a.x + a.w, b.x + b.w);
    float y2 = std::min(a.y + a.h, b.y + b.h);
    float inter = std::max(0.0f, x2 - x1) * std::max(0.0f, y2 - y1);
    float uni = a.w * a.h + b.w * b.h - inter;
    return uni > 0.0f ? inter / uni : 0.0f;
}

}  // namespace


bool YoloDetector::configure(const QnnRuntime& runtime, std::string& error) {
    const QnnTensorInfo& in = runtime.inputInfo();

    if (in.dims.size() != 4 || in.dims[3] != 3) {
        error = "Expected NHWC [1,H,W,3] input";
        return false;
    }

    inputH_ = static_cast<int>(in.dims[1]);
    inputW_ = static_cast<int>(in.dims[2]);
    input_.assign(in.bytes, 0);

    const auto& outs = runtime.outputInfos();
    boxesIdx_ = scoresIdx_ = -1;

    for (size_t i = 0; i < outs.size(); ++i) {
        if (outs[i].name == "boxes") boxesIdx_ = static_cast<int>(i);
        if (outs[i].name == "scores") scoresIdx_ = static_cast<int>(i);
    }

    if (boxesIdx_ < 0 || scoresIdx_ < 0) {
        error = "Model must expose 'boxes' and 'scores' outputs";
        return false;
    }

    boxes_ = outs[boxesIdx_];
    scores_ = outs[scoresIdx_];

    if (boxes_.dims.size() != 3 || scores_.dims.size() != 3 ||
        boxes_.dims[1] != 4 || boxes_.dims[2] != scores_.dims[2]) {
        error = "Unexpected boxes/scores shapes";
        return false;
    }

    numClasses_ = static_cast<int>(scores_.dims[1]);
    numAnchors_ = static_cast<int>(scores_.dims[2]);
    return true;
}


void YoloDetector::preprocess(const uint8_t* rgba, int width, int height) {
    frameW_ = width;
    frameH_ = height;
    frameScale_ = std::min(
        static_cast<float>(inputW_) / width,
        static_cast<float>(inputH_) / height);

    int scaledW = std::min(inputW_, static_cast<int>(width * frameScale_));
    int scaledH = std::min(inputH_, static_cast<int>(height * frameScale_));

    // Letterbox grey (114), image in the top-left corner.
    std::memset(input_.data(), 114, input_.size());

    // Input scale is 1/255 with offset 0, so RGB bytes are already
    // the quantized values. Nearest-neighbour resize (a no-op copy
    // for the 640x480 stream).
    for (int y = 0; y < scaledH; ++y) {
        int sy = std::min(height - 1, static_cast<int>(y / frameScale_));
        const uint8_t* src = rgba + static_cast<size_t>(sy) * width * 4;
        uint8_t* dst = input_.data() + static_cast<size_t>(y) * inputW_ * 3;

        for (int x = 0; x < scaledW; ++x) {
            int sx = std::min(width - 1, static_cast<int>(x / frameScale_));
            const uint8_t* p = src + sx * 4;
            dst[x * 3 + 0] = p[0];
            dst[x * 3 + 1] = p[1];
            dst[x * 3 + 2] = p[2];
        }
    }
}


bool YoloDetector::detect(QnnRuntime& runtime, std::vector<Detection>& out) {
    out.clear();

    if (!runtime.execute(input_.data(), input_.size(), outputs_)) {
        return false;
    }

    const uint8_t* boxes = outputs_[boxesIdx_].data();
    const uint8_t* scores = outputs_[scoresIdx_].data();

    // Compare in the quantized domain: q >= conf/scale - offset.
    int qThresh = static_cast<int>(
        std::ceil(confThreshold / scores_.scale - scores_.offset));
    qThresh = std::max(qThresh, 1);

    auto deq = [](uint8_t q, const QnnTensorInfo& t) {
        return (static_cast<int>(q) + t.offset) * t.scale;
    };

    std::vector<Detection> candidates;

    for (int a = 0; a < numAnchors_; ++a) {
        int bestClass = -1;
        int bestQ = qThresh - 1;

        for (int c = 0; c < numClasses_; ++c) {
            int q = scores[static_cast<size_t>(c) * numAnchors_ + a];
            if (q > bestQ) {
                bestQ = q;
                bestClass = c;
            }
        }

        if (bestClass < 0) continue;

        float cx = deq(boxes[0 * numAnchors_ + a], boxes_);
        float cy = deq(boxes[1 * numAnchors_ + a], boxes_);
        float bw = deq(boxes[2 * numAnchors_ + a], boxes_);
        float bh = deq(boxes[3 * numAnchors_ + a], boxes_);

        // Model pixels -> camera pixels (top-left letterbox).
        Detection d;
        d.classId = bestClass;
        d.confidence = deq(static_cast<uint8_t>(bestQ), scores_);
        d.x = (cx - bw * 0.5f) / frameScale_;
        d.y = (cy - bh * 0.5f) / frameScale_;
        d.w = bw / frameScale_;
        d.h = bh / frameScale_;

        // Clip to the frame.
        float x2 = std::min(static_cast<float>(frameW_), d.x + d.w);
        float y2 = std::min(static_cast<float>(frameH_), d.y + d.h);
        d.x = std::max(0.0f, d.x);
        d.y = std::max(0.0f, d.y);
        d.w = x2 - d.x;
        d.h = y2 - d.y;

        if (d.w > 1.0f && d.h > 1.0f) candidates.push_back(d);
    }

    std::sort(candidates.begin(), candidates.end(),
              [](const Detection& l, const Detection& r) {
                  return l.confidence > r.confidence;
              });

    // Per-class greedy NMS.
    std::vector<bool> removed(candidates.size(), false);

    for (size_t i = 0; i < candidates.size() && out.size() < maxDetections; ++i) {
        if (removed[i]) continue;
        out.push_back(candidates[i]);

        for (size_t j = i + 1; j < candidates.size(); ++j) {
            if (!removed[j] &&
                candidates[j].classId == candidates[i].classId &&
                iou(candidates[i], candidates[j]) > iouThreshold) {
                removed[j] = true;
            }
        }
    }

    return true;
}


float boxDistance(const DepthSampler& depthAt, const Detection& det) {
    // Sample the central 50% of the box on a 7x7 grid.
    std::vector<float> samples;
    samples.reserve(49);

    for (int iy = 0; iy < 7; ++iy) {
        for (int ix = 0; ix < 7; ++ix) {
            float x = det.x + det.w * (0.25f + 0.5f * ix / 6.0f);
            float y = det.y + det.h * (0.25f + 0.5f * iy / 6.0f);

            float z = depthAt(x, y);
            if (z > 0.0f) samples.push_back(z);
        }
    }

    if (samples.empty()) return 0.0f;

    auto mid = samples.begin() + samples.size() / 2;
    std::nth_element(samples.begin(), mid, samples.end());
    return *mid;
}


void drawDetections(uint8_t* rgba, int width, int height, int stride,
                    const std::vector<Detection>& dets) {
    const int thickness = 2;

    for (const Detection& d : dets) {
        int x0 = std::max(0, static_cast<int>(d.x));
        int y0 = std::max(0, static_cast<int>(d.y));
        int x1 = std::min(width - 1, static_cast<int>(d.x + d.w));
        int y1 = std::min(height - 1, static_cast<int>(d.y + d.h));

        auto put = [&](int x, int y) {
            uint8_t* p = rgba + (static_cast<size_t>(y) * stride + x) * 4;
            p[0] = 0; p[1] = 255; p[2] = 0; p[3] = 255;
        };

        for (int t = 0; t < thickness; ++t) {
            for (int x = x0; x <= x1; ++x) {
                if (y0 + t <= y1) put(x, y0 + t);
                if (y1 - t >= y0) put(x, y1 - t);
            }
            for (int y = y0; y <= y1; ++y) {
                if (x0 + t <= x1) put(x0 + t, y);
                if (x1 - t >= x0) put(x1 - t, y);
            }
        }
    }
}
