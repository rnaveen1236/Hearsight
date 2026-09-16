#include "qnn_runtime.h"

#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <sstream>

#define LOG_TAG "HSV2_QNN"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

static std::string qnnError(Qnn_ErrorHandle_t err) {
    std::ostringstream oss;

    oss << "QNN error 0x"
        << std::hex
        << static_cast<uint32_t>(err);

    const char* message = nullptr;
    const char* verboseMessage = nullptr;

    /*
     * The QNN backend exposes descriptive error APIs through
     * QnnInterface_t. qnnInterface_ is declared on QnnRuntime,
     * so the detailed error is queried at the call site instead
     * of guessing the meaning of the numeric error handle.
     */

    return oss.str();
}

static void freeTensorMetadata(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        if (tensor.v1.name) {
            free(const_cast<char*>(tensor.v1.name));
            tensor.v1.name = nullptr;
        }

        if (tensor.v1.dimensions) {
            free(tensor.v1.dimensions);
            tensor.v1.dimensions = nullptr;
        }
    } else if (tensor.version == QNN_TENSOR_VERSION_2) {
        if (tensor.v2.name) {
            free(const_cast<char*>(tensor.v2.name));
            tensor.v2.name = nullptr;
        }

        if (tensor.v2.dimensions) {
            free(tensor.v2.dimensions);
            tensor.v2.dimensions = nullptr;
        }

        if (tensor.v2.isDynamicDimensions) {
            free(tensor.v2.isDynamicDimensions);
            tensor.v2.isDynamicDimensions = nullptr;
        }
    }
}

static bool copyTensorMetadata(
        const Qnn_Tensor_t& src,
        Qnn_Tensor_t& dst) {

    std::memset(&dst, 0, sizeof(Qnn_Tensor_t));

    dst.version = src.version;

    if (src.version == QNN_TENSOR_VERSION_1) {
        dst.v1 = src.v1;

        if (src.v1.name) {
            size_t len = std::strlen(src.v1.name);
            char* name = static_cast<char*>(malloc(len + 1));
            if (!name) {
                return false;
            }
            std::memcpy(name, src.v1.name, len + 1);
            dst.v1.name = name;
        }

        if (src.v1.rank > 0 && src.v1.dimensions) {
            dst.v1.dimensions = static_cast<uint32_t*>(
                malloc(sizeof(uint32_t) * src.v1.rank));

            if (!dst.v1.dimensions) {
                freeTensorMetadata(dst);
                return false;
            }

            std::memcpy(
                dst.v1.dimensions,
                src.v1.dimensions,
                sizeof(uint32_t) * src.v1.rank);
        }

        return true;
    }

    if (src.version == QNN_TENSOR_VERSION_2) {
        dst.v2 = src.v2;

        if (src.v2.name) {
            size_t len = std::strlen(src.v2.name);
            char* name = static_cast<char*>(malloc(len + 1));
            if (!name) {
                return false;
            }
            std::memcpy(name, src.v2.name, len + 1);
            dst.v2.name = name;
        }

        if (src.v2.rank > 0 && src.v2.dimensions) {
            dst.v2.dimensions = static_cast<uint32_t*>(
                malloc(sizeof(uint32_t) * src.v2.rank));

            if (!dst.v2.dimensions) {
                freeTensorMetadata(dst);
                return false;
            }

            std::memcpy(
                dst.v2.dimensions,
                src.v2.dimensions,
                sizeof(uint32_t) * src.v2.rank);
        }

        if (src.v2.rank > 0 && src.v2.isDynamicDimensions) {
            dst.v2.isDynamicDimensions = static_cast<uint8_t*>(
                malloc(sizeof(uint8_t) * src.v2.rank));

            if (!dst.v2.isDynamicDimensions) {
                freeTensorMetadata(dst);
                return false;
            }

            std::memcpy(
                dst.v2.isDynamicDimensions,
                src.v2.isDynamicDimensions,
                sizeof(uint8_t) * src.v2.rank);
        }

        return true;
    }

    return false;
}

static Qnn_Tensor_t* tensorData(Qnn_Tensor_t& tensor) {
    return &tensor;
}

static const char* tensorName(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.name;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.name;
    }

    return nullptr;
}

static uint32_t tensorRank(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.rank;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.rank;
    }

    return 0;
}

static uint32_t* tensorDimensions(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dimensions;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dimensions;
    }

    return nullptr;
}

static Qnn_TensorType_t tensorType(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.type;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.type;
    }

    return QNN_TENSOR_TYPE_UNDEFINED;
}

static Qnn_DataType_t tensorDataType(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dataType;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dataType;
    }

    return QNN_DATATYPE_UNDEFINED;
}

static Qnn_TensorDataFormat_t tensorDataFormat(
        const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dataFormat;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dataFormat;
    }

    // FIX (Issue 1): QNN_TENSOR_DATA_FORMAT_UNDEFINED does not exist in
    // QAIRT 2.47.0.260601. Use the only defined enumerator as the fallback.
    return QNN_TENSOR_DATA_FORMAT_FLAT_BUFFER;
}

static Qnn_TensorMemType_t tensorMemType(
        const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.memType;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.memType;
    }

    return QNN_TENSORMEMTYPE_UNDEFINED;
}

static Qnn_ClientBuffer_t* tensorClientBuffer(
        Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return &tensor.v1.clientBuf;
    }

    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return &tensor.v2.clientBuf;
    }

    return nullptr;
}

}  // namespace


QnnRuntime::QnnRuntime() {
    std::memset(&qnnInterface_, 0, sizeof(qnnInterface_));
    std::memset(&qnnSystemInterface_, 0, sizeof(qnnSystemInterface_));
}


QnnRuntime::~QnnRuntime() {
    shutdown();
}


bool QnnRuntime::setError(const std::string& error) {
    lastError_ = error;
    LOGE("%s", lastError_.c_str());
    return false;
}


const std::string& QnnRuntime::getLastError() const {
    return lastError_;
}


bool QnnRuntime::isInitialized() const {
    return graphInitialized_;
}


bool QnnRuntime::loadQnnLibraries() {
    if (qnnHtpLib_ && qnnSystemLib_) {
        return true;
    }

    qnnHtpPrepareLib_ = dlopen(
        "libQnnHtpPrepare.so",
        RTLD_NOW | RTLD_LOCAL);

    if (!qnnHtpPrepareLib_) {
        return setError(
            "Failed to load libQnnHtpPrepare.so: " +
            std::string(dlerror() ? dlerror() : "unknown"));
    }

    qnnHtpLib_ = dlopen(
        "libQnnHtp.so",
        RTLD_NOW | RTLD_LOCAL);

    if (!qnnHtpLib_) {
        return setError(
            "Failed to load libQnnHtp.so: " +
            std::string(dlerror() ? dlerror() : "unknown"));
    }

    qnnSystemLib_ = dlopen(
        "libQnnSystem.so",
        RTLD_NOW | RTLD_LOCAL);

    if (!qnnSystemLib_) {
        return setError(
            "Failed to load libQnnSystem.so: " +
            std::string(dlerror() ? dlerror() : "unknown"));
    }

    qnnInterfaceGetProviders_ =
        reinterpret_cast<QnnInterfaceGetProvidersFn>(
            dlsym(
                qnnHtpLib_,
                "QnnInterface_getProviders"));

    if (!qnnInterfaceGetProviders_) {
        return setError(
            "QnnInterface_getProviders not found");
    }

    qnnSystemInterfaceGetProviders_ =
        reinterpret_cast<QnnSystemInterfaceGetProvidersFn>(
            dlsym(
                qnnSystemLib_,
                "QnnSystemInterface_getProviders"));

    if (!qnnSystemInterfaceGetProviders_) {
        return setError(
            "QnnSystemInterface_getProviders not found");
    }

    qnnLoaded_ = true;

    LOGI("QNN libraries loaded");

    return true;
}


bool QnnRuntime::loadModelLibrary() {
    if (modelLib_) {
        return true;
    }

    if (modelLibraryPath_.empty()) {
        return setError("Model library path is empty");
    }

    modelLib_ = dlopen(
        modelLibraryPath_.c_str(),
        RTLD_NOW | RTLD_LOCAL);

    if (!modelLib_) {
        return setError(
            "Failed to load model library: " +
            modelLibraryPath_);
    }

    composeGraphs_ =
        reinterpret_cast<ComposeGraphsFn>(
            dlsym(
                modelLib_,
                "QnnModel_composeGraphs"));

    freeGraphsInfo_ =
        reinterpret_cast<FreeGraphsInfoFn>(
            dlsym(
                modelLib_,
                "QnnModel_freeGraphsInfo"));

    if (!composeGraphs_ || !freeGraphsInfo_) {
        return setError(
            "Required QnnModel compose/free symbols not found");
    }

    modelLoaded_ = true;

    LOGI("QNN model library loaded");

    return true;
}


bool QnnRuntime::loadQnnInterface() {
    const QnnInterface_t** providers = nullptr;
    uint32_t numProviders = 0;

    Qnn_ErrorHandle_t err =
        qnnInterfaceGetProviders_(
            &providers,
            &numProviders);

    if (err != QNN_SUCCESS) {
        return setError(
            "QnnInterface_getProviders failed: " +
            qnnError(err));
    }

    if (!providers || numProviders == 0) {
        return setError(
            "No QNN HTP interface providers found");
    }

    const QnnInterface_t* selected = nullptr;

    for (uint32_t i = 0; i < numProviders; ++i) {
        if (!providers[i]) {
            continue;
        }

        if (providers[i]->apiVersion.coreApiVersion.major ==
            QNN_API_VERSION_MAJOR) {
            selected = providers[i];
            break;
        }
    }

    if (!selected) {
        selected = providers[0];
    }

    qnnInterface_ = selected->QNN_INTERFACE_VER_NAME;

    LOGI(
        "QNN HTP interface selected: major=%u minor=%u",
        selected->apiVersion.coreApiVersion.major,
        selected->apiVersion.coreApiVersion.minor);

    return true;
}


bool QnnRuntime::loadQnnSystemInterface() {
    const QnnSystemInterface_t** providers = nullptr;
    uint32_t numProviders = 0;

    Qnn_ErrorHandle_t err =
        qnnSystemInterfaceGetProviders_(
            &providers,
            &numProviders);

    if (err != QNN_SUCCESS) {
        return setError(
            "QnnSystemInterface_getProviders failed: " +
            qnnError(err));
    }

    if (!providers || numProviders == 0) {
        return setError(
            "No QNN System interface providers found");
    }

    qnnSystemInterface_ =
        providers[0]->QNN_SYSTEM_INTERFACE_VER_NAME;

    LOGI("QNN System interface selected");

    return true;
}


bool QnnRuntime::initializeBackend() {
    // Initialize QNN backend logging, matching the official QNN SampleApp.
    if (qnnInterface_.logCreate) {
        Qnn_ErrorHandle_t logErr =
            qnnInterface_.logCreate(
                nullptr,
                QNN_LOG_LEVEL_ERROR,
                &logHandle_);

        if (logErr != QNN_SUCCESS) {
            LOGE("QNN logCreate failed: %s", qnnError(logErr).c_str());
            logHandle_ = nullptr;
        } else {
            LOGI("QNN log handle created");
        }
    } else {
        LOGI("QNN logCreate unavailable; continuing without QNN log handle");
    }

    if (!qnnInterface_.backendCreate) {
        return setError("QNN backendCreate function unavailable");
    }

    Qnn_ErrorHandle_t err =
        qnnInterface_.backendCreate(
            logHandle_,
            nullptr,
            &backendHandle_);

    if (err != QNN_SUCCESS) {
        return setError(
            "QNN backendCreate failed: " + qnnError(err));
    }

    LOGI("QNN HTP backend initialized");

    if (!qnnInterface_.deviceCreate) {
        return setError("QNN deviceCreate function unavailable");
    }

    err =
        qnnInterface_.deviceCreate(
            logHandle_,
            nullptr,
            &deviceHandle_);

    if (err != QNN_SUCCESS) {
        std::ostringstream errorText;
        errorText << "QNN deviceCreate failed: "
                  << qnnError(err);

        if (qnnInterface_.errorGetMessage) {
            const char* message = nullptr;

            Qnn_ErrorHandle_t messageErr =
                qnnInterface_.errorGetMessage(
                    err,
                    &message);

            if (messageErr == QNN_SUCCESS && message) {
                errorText << "; message=" << message;
            }
        }

        return setError(errorText.str());
    }

    if (!deviceHandle_) {
        return setError(
            "QNN deviceCreate returned success but deviceHandle is null");
    }

    LOGI("QNN HTP device created");

    return true;
}

bool QnnRuntime::readContextBinary(std::vector<uint8_t>& binary) {
    binary.clear();

    std::ifstream file(contextBinaryPath_, std::ios::binary | std::ios::ate);

    if (!file.is_open()) {
        return setError(
            "Failed to open context binary: " + contextBinaryPath_);
    }

    const std::streamsize size = file.tellg();

    if (size <= 0) {
        return setError(
            "Context binary is empty: " + contextBinaryPath_);
    }

    file.seekg(0, std::ios::beg);

    binary.resize(static_cast<size_t>(size));

    if (!file.read(
            reinterpret_cast<char*>(binary.data()),
            size)) {
        binary.clear();
        return setError(
            "Failed to read context binary: " + contextBinaryPath_);
    }

    LOGI(
        "Loaded context binary: %zu bytes",
        binary.size());

    return true;
}

bool QnnRuntime::createContextFromBinary() {
    std::vector<uint8_t> binary;

    if (!readContextBinary(binary)) {
        return false;
    }

    if (!qnnSystemInterface_.systemContextCreate ||
        !qnnSystemInterface_.systemContextGetBinaryInfo ||
        !qnnSystemInterface_.systemContextFree) {
        return setError(
            "QNN System context APIs unavailable");
    }

    QnnSystemContext_Handle_t sysCtx = nullptr;

    Qnn_ErrorHandle_t err =
        qnnSystemInterface_.systemContextCreate(
            &sysCtx);

    if (err != QNN_SUCCESS) {
        return setError(
            "systemContextCreate failed: " +
            qnnError(err));
    }

    const QnnSystemContext_BinaryInfo_t* binaryInfo = nullptr;
    Qnn_ContextBinarySize_t binaryInfoSize = 0;

    err =
        qnnSystemInterface_.systemContextGetBinaryInfo(
            sysCtx,
            binary.data(),
            binary.size(),
            &binaryInfo,
            &binaryInfoSize);

    if (err != QNN_SUCCESS) {
        qnnSystemInterface_.systemContextFree(sysCtx);

        return setError(
            "systemContextGetBinaryInfo failed: " +
            qnnError(err));
    }

    if (!binaryInfo) {
        qnnSystemInterface_.systemContextFree(sysCtx);

        return setError(
            "QNN returned null binary metadata");
    }

    LOGI(
        "Context binary metadata size=%llu",
        static_cast<unsigned long long>(binaryInfoSize));

    err =
        qnnInterface_.contextCreateFromBinary(
            backendHandle_,
            deviceHandle_,
            nullptr,
            binary.data(),
            binary.size(),
            &contextHandle_,
            profileHandle_);

    qnnSystemInterface_.systemContextFree(sysCtx);

    if (err != QNN_SUCCESS) {
        std::ostringstream errorText;

        errorText
            << "contextCreateFromBinary failed: "
            << qnnError(err);

        if (qnnInterface_.errorGetMessage) {
            const char* message = nullptr;

            Qnn_ErrorHandle_t messageErr =
                qnnInterface_.errorGetMessage(
                    err,
                    &message);

            if (messageErr == QNN_SUCCESS && message) {
                errorText << "; message=" << message;
            }
        }

        if (qnnInterface_.errorGetVerboseMessage) {
            const char* verboseMessage = nullptr;

            Qnn_ErrorHandle_t verboseErr =
                qnnInterface_.errorGetVerboseMessage(
                    err,
                    &verboseMessage);

            if (verboseErr == QNN_SUCCESS && verboseMessage) {
                errorText << "; verbose=" << verboseMessage;

                if (qnnInterface_.errorFreeVerboseMessage) {
                    qnnInterface_.errorFreeVerboseMessage(
                        verboseMessage);
                }
            }
        }

        return setError(errorText.str());
    }

    contextInitialized_ = true;

    LOGI("QNN context created from HTP context binary");

    return true;
}


bool QnnRuntime::retrieveGraph() {
    if (!contextHandle_) {
        return setError("QNN context handle is null");
    }

    if (!qnnInterface_.graphRetrieve) {
        return setError(
            "QNN graphRetrieve function unavailable");
    }

    /*
     * The generated YOLOv8s context normally contains one
     * executable graph. Try the common graph name first.
     *
     * If the generated graph has another name, the model
     * metadata path can be used in a later step.
     */
    const char* graphNames[] = {
        "graph",
        "yolov8s",
        "yolov8s_int8",
        "main",
        "QnnModel_graph"
    };

    for (const char* name : graphNames) {
        Qnn_GraphHandle_t graph = nullptr;

        Qnn_ErrorHandle_t err =
            qnnInterface_.graphRetrieve(
                contextHandle_,
                name,
                &graph);

        if (err == QNN_SUCCESS && graph) {
            graphHandle_ = graph;
            graphInitialized_ = true;

            LOGI(
                "Retrieved QNN graph: %s",
                name);

            return true;
        }
    }

    return setError(
        "Unable to retrieve YOLOv8s graph from context binary");
}


bool QnnRuntime::setupInputTensor() {
    if (!graphsInfo_ || graphsCount_ == 0) {
        return setError(
            "No graph metadata available");
    }

    qnn_runtime_api::GraphInfo_t* graphInfo =
        graphsInfo_[0];

    if (!graphInfo ||
        graphInfo->numInputTensors == 0 ||
        !graphInfo->inputTensors) {
        return setError(
            "Graph has no input tensor metadata");
    }

    if (!copyTensorMetadata(
            graphInfo->inputTensors[0],
            inputTensor_)) {
        return setError(
            "Failed to copy input tensor metadata");
    }

    LOGI(
        "Input tensor: name=%s rank=%u type=%d datatype=%d memtype=%d",
        tensorName(inputTensor_)
            ? tensorName(inputTensor_)
            : "<null>",
        tensorRank(inputTensor_),
        static_cast<int>(tensorType(inputTensor_)),
        static_cast<int>(tensorDataType(inputTensor_)),
        static_cast<int>(tensorMemType(inputTensor_)));

    return true;
}


bool QnnRuntime::setupOutputTensor() {
    if (!graphsInfo_ || graphsCount_ == 0) {
        return setError(
            "No graph metadata available");
    }

    qnn_runtime_api::GraphInfo_t* graphInfo =
        graphsInfo_[0];

    if (!graphInfo ||
        graphInfo->numOutputTensors == 0 ||
        !graphInfo->outputTensors) {
        return setError(
            "Graph has no output tensor metadata");
    }

    if (!copyTensorMetadata(
            graphInfo->outputTensors[0],
            outputTensor_)) {
        return setError(
            "Failed to copy output tensor metadata");
    }

    LOGI(
        "Output tensor: name=%s rank=%u type=%d datatype=%d memtype=%d",
        tensorName(outputTensor_)
            ? tensorName(outputTensor_)
            : "<null>",
        tensorRank(outputTensor_),
        static_cast<int>(tensorType(outputTensor_)),
        static_cast<int>(tensorDataType(outputTensor_)),
        static_cast<int>(tensorMemType(outputTensor_)));

    return true;
}


bool QnnRuntime::initialize(
        const std::string& contextBinaryPath,
        const std::string& modelLibraryPath) {

    shutdown();

    contextBinaryPath_ = contextBinaryPath;
    modelLibraryPath_ = modelLibraryPath;
    lastError_.clear();

    // Set ADSP_LIBRARY_PATH so the FastRPC/ADSP loader can find
    // the DSP-side HTP skel library (for example,
    // libQnnHtpV81Skel.so) from the native library directory
    // and standard DSP locations.
    {
        size_t lastSlash = modelLibraryPath_.find_last_of('/');

        if (lastSlash != std::string::npos) {
            std::string nativeLibDir =
                modelLibraryPath_.substr(0, lastSlash);

            std::string adspPath =
                nativeLibDir +
                ";/vendor/lib/rfsa/adsp;/vendor/dsp/cdsp;/dsp";

            if (setenv("ADSP_LIBRARY_PATH", adspPath.c_str(), 1) != 0) {
                LOGE("Failed to set ADSP_LIBRARY_PATH");
            } else {
                LOGI(
                    "ADSP_LIBRARY_PATH set to: %s",
                    adspPath.c_str());
            }
        } else {
            LOGE(
                "Could not derive native lib dir from model library path: %s",
                modelLibraryPath_.c_str());
        }
    }

    if (!loadQnnLibraries()) {
        return false;
    }

    if (!loadQnnInterface()) {
        return false;
    }

    if (!loadQnnSystemInterface()) {
        return false;
    }

    if (!initializeBackend()) {
        return false;
    }

    /*
     * The generated model library is needed for graph/tensor
     * metadata. Load it before using composeGraphs.
     */
    if (!loadModelLibrary()) {
        return false;
    }

    /*
     * Create the QNN context from the pre-generated HTP context
     * binary BEFORE composing the generated model graph.
     *
     * QnnModel_composeGraphs() passes contextHandle to
     * QnnModel::initialize(). A null context causes the generated
     * model to return MODEL_CONTEXT_ERROR (5).
     */
    if (!createContextFromBinary()) {
        return false;
    }

    if (!composeGraphs_) {
        return setError(
            "QnnModel_composeGraphs unavailable");
    }

    const qnn_runtime_api::GraphConfigInfo_t** graphConfigs =
        nullptr;

    qnn_runtime_api::ModelError_t modelErr =
        composeGraphs_(
            backendHandle_,
            qnnInterface_,
            contextHandle_,
            graphConfigs,
            0,
            &graphsInfo_,
            &graphsCount_,
            false,
            nullptr,
            QNN_LOG_LEVEL_ERROR);

    if (modelErr !=
        qnn_runtime_api::MODEL_NO_ERROR) {

        std::ostringstream oss;
        oss << "QnnModel_composeGraphs failed: "
            << static_cast<int>(modelErr);

        return setError(oss.str());
    }

    if (!graphsInfo_ || graphsCount_ == 0) {
        return setError(
            "QnnModel_composeGraphs returned no graphs");
    }

    LOGI(
        "Model metadata contains %u graph(s)",
        graphsCount_);

    /*
     * Prefer the graph handle from generated metadata if present.
     * Otherwise retrieve it from the context.
     */
    if (graphsInfo_[0] &&
        graphsInfo_[0]->graph) {

        graphHandle_ =
            graphsInfo_[0]->graph;

        graphInitialized_ = true;

        LOGI("Using graph handle from model metadata");
    } else {
        if (!retrieveGraph()) {
            return false;
        }
    }

    if (!setupInputTensor()) {
        return false;
    }

    if (!setupOutputTensor()) {
        return false;
    }

    LOGI("QNN runtime initialized successfully");

    return true;
}


bool QnnRuntime::execute(
        const uint8_t* inputData,
        size_t inputSize,
        std::vector<uint8_t>& outputData) {

    if (!graphInitialized_ || !graphHandle_) {
        return setError(
            "QNN graph is not initialized");
    }

    if (!inputData || inputSize == 0) {
        return setError(
            "Invalid input buffer");
    }

    Qnn_ClientBuffer_t* inputClient =
        tensorClientBuffer(inputTensor_);

    if (!inputClient) {
        return setError(
            "Input tensor does not expose client buffer");
    }

    inputClient->data =
        const_cast<uint8_t*>(inputData);

    inputClient->dataSize =
        static_cast<uint32_t>(inputSize);

    Qnn_ClientBuffer_t* outputClient =
        tensorClientBuffer(outputTensor_);

    if (!outputClient) {
        return setError(
            "Output tensor does not expose client buffer");
    }

    /*
     * Output size is obtained from the metadata dimensions.
     * For the YOLOv8s INT8 model the output is expected to be
     * [1,84,8400] with 8-bit elements.
     */
    size_t outputElements = 1;

    uint32_t rank = tensorRank(outputTensor_);

    uint32_t* dims =
        tensorDimensions(outputTensor_);

    if (!dims || rank == 0) {
        return setError(
            "Output tensor has invalid dimensions");
    }

    for (uint32_t i = 0; i < rank; ++i) {
        outputElements *= dims[i];
    }

    size_t outputBytes = outputElements;

    void* outputBuffer =
        malloc(outputBytes);

    if (!outputBuffer) {
        return setError(
            "Failed to allocate QNN output buffer");
    }

    std::memset(
        outputBuffer,
        0,
        outputBytes);

    outputClient->data = outputBuffer;
    outputClient->dataSize =
        static_cast<uint32_t>(outputBytes);

    auto start =
        std::chrono::steady_clock::now();

    Qnn_ErrorHandle_t err =
        qnnInterface_.graphExecute(
            graphHandle_,
            &inputTensor_,
            1,
            &outputTensor_,
            1,
            profileHandle_,
            nullptr);

    auto end =
        std::chrono::steady_clock::now();

    double elapsedMs =
        std::chrono::duration<double, std::milli>(
            end - start).count();

    if (err != QNN_SUCCESS) {
        free(outputBuffer);

        inputClient->data = nullptr;
        inputClient->dataSize = 0;

        outputClient->data = nullptr;
        outputClient->dataSize = 0;

        return setError(
            "QNN graphExecute failed: " +
            qnnError(err));
    }

    outputData.resize(outputBytes);

    std::memcpy(
        outputData.data(),
        outputBuffer,
        outputBytes);

    free(outputBuffer);

    inputClient->data = nullptr;
    inputClient->dataSize = 0;

    outputClient->data = nullptr;
    outputClient->dataSize = 0;

    LOGI(
        "QNN inference completed: %.3f ms, output=%zu bytes",
        elapsedMs,
        outputBytes);

    return true;
}


void QnnRuntime::cleanupModel() {
    if (graphsInfo_) {
        for (uint32_t i = 0; i < graphsCount_; ++i) {
            if (!graphsInfo_[i]) {
                continue;
            }

            qnn_runtime_api::GraphInfo_t* graph =
                graphsInfo_[i];

            for (uint32_t j = 0;
                 j < graph->numInputTensors;
                 ++j) {
                freeTensorMetadata(
                    graph->inputTensors[j]);
            }

            for (uint32_t j = 0;
                 j < graph->numOutputTensors;
                 ++j) {
                freeTensorMetadata(
                    graph->outputTensors[j]);
            }
        }

        if (freeGraphsInfo_) {
            // FIX (Issue 2a): freeGraphsInfo_ expects
            // GraphInfo_t*** (GraphInfoPtr_t**) so it can null out
            // the caller's pointer internally. Pass the address of
            // graphsInfo_, not graphsInfo_ itself.
            freeGraphsInfo_(
                &graphsInfo_,
                graphsCount_);
        }

        graphsInfo_ = nullptr;
        graphsCount_ = 0;
    }

    freeTensorMetadata(inputTensor_);
    freeTensorMetadata(outputTensor_);

    std::memset(
        &inputTensor_,
        0,
        sizeof(inputTensor_));

    std::memset(
        &outputTensor_,
        0,
        sizeof(outputTensor_));

    composeGraphs_ = nullptr;
    freeGraphsInfo_ = nullptr;

    if (modelLib_) {
        dlclose(modelLib_);
        modelLib_ = nullptr;
    }

    modelLoaded_ = false;
}


void QnnRuntime::cleanupContext() {
    graphHandle_ = nullptr;
    graphInitialized_ = false;

    if (contextHandle_ &&
        qnnInterface_.contextFree) {

        // FIX (Issue 2b): contextFree in this QAIRT SDK takes two
        // arguments - the context handle and a profile handle -
        // matching how contextCreateFromBinary() is called above
        // with profileHandle_.
        qnnInterface_.contextFree(
            contextHandle_,
            profileHandle_);

        contextHandle_ = nullptr;
    }

    contextInitialized_ = false;
}


void QnnRuntime::cleanupBackend() {
    if (backendHandle_ &&
        qnnInterface_.backendFree) {

        qnnInterface_.backendFree(
            backendHandle_);

        backendHandle_ = nullptr;
    }

    if (logHandle_ &&
        qnnInterface_.logFree) {

        qnnInterface_.logFree(
            logHandle_);

        logHandle_ = nullptr;
    }

    backendInitialized_ = false;
}


void QnnRuntime::closeLibraries() {
    qnnInterfaceGetProviders_ = nullptr;
    qnnSystemInterfaceGetProviders_ = nullptr;

    if (qnnSystemLib_) {
        dlclose(qnnSystemLib_);
        qnnSystemLib_ = nullptr;
    }

    if (qnnHtpLib_) {
        dlclose(qnnHtpLib_);
        qnnHtpLib_ = nullptr;
    }

    if (qnnHtpPrepareLib_) {
        dlclose(qnnHtpPrepareLib_);
        qnnHtpPrepareLib_ = nullptr;
    }

    qnnLoaded_ = false;

    std::memset(
        &qnnInterface_,
        0,
        sizeof(qnnInterface_));

    std::memset(
        &qnnSystemInterface_,
        0,
        sizeof(qnnSystemInterface_));
}


void QnnRuntime::shutdown() {
    cleanupModel();
    cleanupContext();
    cleanupBackend();
    closeLibraries();

    deviceHandle_ = nullptr;
    profileHandle_ = nullptr;
}
