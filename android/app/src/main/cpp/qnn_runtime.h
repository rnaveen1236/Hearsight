#ifndef QNN_RUNTIME_H
#define QNN_RUNTIME_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include <dlfcn.h>

#include "QnnInterface.h"
#include "QnnGraph.h"
#include "QnnTensor.h"
#include "QnnTypes.h"
#include "QnnSystemInterface.h"

namespace qnn_runtime_api {

enum ModelError_t {
    MODEL_NO_ERROR               = 0,
    MODEL_TENSOR_ERROR           = 1,
    MODEL_PARAMS_ERROR           = 2,
    MODEL_NODES_ERROR            = 3,
    MODEL_GRAPH_ERROR            = 4,
    MODEL_CONTEXT_ERROR          = 5,
    MODEL_GENERATION_ERROR       = 6,
    MODEL_SETUP_ERROR            = 7,
    MODEL_INVALID_ARGUMENT_ERROR = 8,
    MODEL_FILE_ERROR             = 9,
    MODEL_MEMORY_ALLOCATE_ERROR  = 10,
    MODEL_UNKNOWN_ERROR          = 0x7FFFFFFF
};

struct GraphInfo_t {
    Qnn_GraphHandle_t graph;
    char* graphName;
    Qnn_Tensor_t* inputTensors;
    uint32_t numInputTensors;
    Qnn_Tensor_t* outputTensors;
    uint32_t numOutputTensors;
};

using GraphInfoPtr_t = GraphInfo_t*;

struct GraphConfigInfo_t {
    char* graphName;
    const QnnGraph_Config_t** graphConfigs;
};

}  // namespace qnn_runtime_api


// Shape and quantization of a graph input/output.
// Dequantize with: real = (q + offset) * scale.
struct QnnTensorInfo {
    std::string name;
    std::vector<uint32_t> dims;
    float scale{1.0f};
    int32_t offset{0};
    size_t bytes{0};
};

class QnnRuntime {
public:
    QnnRuntime();
    ~QnnRuntime();

    bool initialize(
        const std::string& contextBinaryPath,
        const std::string& modelLibraryPath,
        const std::string& nativeLibDir
    );

    // Runs the graph. outputs[i] receives the raw (quantized)
    // bytes of output i, in the order of outputInfos().
    bool execute(
        const uint8_t* inputData,
        size_t inputSize,
        std::vector<std::vector<uint8_t>>& outputs
    );

    const QnnTensorInfo& inputInfo() const { return inputInfo_; }
    const std::vector<QnnTensorInfo>& outputInfos() const { return outputInfos_; }

    // Wall time of the last graphExecute() call.
    double lastExecuteMs() const { return lastExecuteMs_; }

    void shutdown();

    bool isInitialized() const;

    const std::string& getLastError() const;

private:
    bool loadQnnLibraries();
    bool loadModelLibrary();
    bool loadQnnInterface();
    bool loadQnnSystemInterface();

    bool initializeBackend();
    bool createContextFromBinary();
    bool retrieveGraph();

    bool setupInputTensor();
    bool setupOutputTensor();

    bool readContextBinary(std::vector<uint8_t>& binary);

    void cleanupModel();
    void cleanupContext();
    void cleanupBackend();
    void closeLibraries();

    bool setError(const std::string& error);

private:
    QNN_INTERFACE_VER_TYPE qnnInterface_{};
    QNN_SYSTEM_INTERFACE_VER_TYPE qnnSystemInterface_{};

    Qnn_LogHandle_t logHandle_{nullptr};
    Qnn_BackendHandle_t backendHandle_{nullptr};
    Qnn_DeviceHandle_t deviceHandle_{nullptr};
    Qnn_ContextHandle_t contextHandle_{nullptr};
    Qnn_GraphHandle_t graphHandle_{nullptr};
    std::string graphName_;
    Qnn_ProfileHandle_t profileHandle_{nullptr};

    void* qnnHtpLib_{nullptr};
    void* qnnHtpPrepareLib_{nullptr};
    void* qnnSystemLib_{nullptr};
    void* modelLib_{nullptr};

    using QnnInterfaceGetProvidersFn =
        Qnn_ErrorHandle_t (*)(
            const QnnInterface_t*** providers,
            uint32_t* numProviders
        );

    using QnnSystemInterfaceGetProvidersFn =
        Qnn_ErrorHandle_t (*)(
            const QnnSystemInterface_t*** providers,
            uint32_t* numProviders
        );

    QnnInterfaceGetProvidersFn qnnInterfaceGetProviders_{nullptr};
    QnnSystemInterfaceGetProvidersFn qnnSystemInterfaceGetProviders_{nullptr};

    using ComposeGraphsFn =
        qnn_runtime_api::ModelError_t (*)(
            Qnn_BackendHandle_t backendHandle,
            QNN_INTERFACE_VER_TYPE interface,
            Qnn_ContextHandle_t contextHandle,
            const qnn_runtime_api::GraphConfigInfo_t** graphsConfigInfo,
            uint32_t numGraphsConfigInfo,
            qnn_runtime_api::GraphInfoPtr_t** graphsInfo,
            uint32_t* numGraphsInfo,
            bool debug,
            QnnLog_Callback_t logCallback,
            QnnLog_Level_t maxLogLevel
        );

    using FreeGraphsInfoFn =
        qnn_runtime_api::ModelError_t (*)(
            qnn_runtime_api::GraphInfoPtr_t** graphsInfo,
            uint32_t numGraphsInfo
        );

    ComposeGraphsFn composeGraphs_{nullptr};
    FreeGraphsInfoFn freeGraphsInfo_{nullptr};

    qnn_runtime_api::GraphInfoPtr_t* graphsInfo_{nullptr};
    uint32_t graphsCount_{0};

    Qnn_Tensor_t inputTensor_{};
    std::vector<Qnn_Tensor_t> outputTensors_;

    QnnTensorInfo inputInfo_;
    std::vector<QnnTensorInfo> outputInfos_;
    double lastExecuteMs_{0.0};

    std::string contextBinaryPath_;
    std::string modelLibraryPath_;

    bool qnnLoaded_{false};
    bool modelLoaded_{false};
    bool backendInitialized_{false};
    bool contextInitialized_{false};
    bool graphInitialized_{false};

    std::string lastError_;
};

#endif  // QNN_RUNTIME_H
