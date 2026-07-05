#include <jni.h>

#include <array>
#include <cstdint>
#include <mutex>
#include <string>

#if defined(_WIN32)
#include <windows.h>
#endif

#if defined(SALTS_DLSS_WITH_STREAMLINE)
#include <vulkan/vulkan.h>
#include <sl.h>
#include <sl_consts.h>
#include <sl_dlss.h>
#include <sl_helpers_vk.h>
#include <sl_matrix_helpers.h>
#endif

namespace {
std::mutex g_mutex;
bool g_initialized = false;
bool g_supported = false;

std::wstring to_wstring(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }

    const jsize length = env->GetStringLength(value);
    std::wstring result(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(value, chars);
    return result;
}

#if defined(SALTS_DLSS_WITH_STREAMLINE)
sl::DLSSMode to_dlss_mode(jint quality_preset) {
    switch (quality_preset) {
        case 0:
            return sl::DLSSMode::eMaxQuality;
        case 1:
            return sl::DLSSMode::eBalanced;
        case 2:
            return sl::DLSSMode::eMaxPerformance;
        case 3:
            return sl::DLSSMode::eUltraPerformance;
        case 4:
        default:
            return sl::DLSSMode::eBalanced;
    }
}

sl::float4x4 to_float4x4(JNIEnv* env, jfloatArray values) {
    std::array<float, 16> data{};
    env->GetFloatArrayRegion(values, 0, 16, data.data());
    sl::float4x4 matrix{};
    for (uint32_t row = 0; row < 4; row++) {
        matrix[row] = sl::float4(
                data[row * 4 + 0],
                data[row * 4 + 1],
                data[row * 4 + 2],
                data[row * 4 + 3]);
    }
    return matrix;
}

sl::Resource image_resource(jlong image, jlong view, uint32_t layout, uint32_t width, uint32_t height) {
    sl::Resource resource(
            sl::ResourceType::eTex2d,
            reinterpret_cast<void*>(static_cast<uintptr_t>(image)),
            nullptr,
            reinterpret_cast<void*>(static_cast<uintptr_t>(view)),
            layout);
    resource.width = width;
    resource.height = height;
    return resource;
}
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_initializeNative(
        JNIEnv* env,
        jclass,
        jstring plugin_path,
        jstring log_path,
        jint application_id,
        jlong vk_instance,
        jlong vk_physical_device,
        jlong vk_device,
        jlong vk_graphics_queue,
        jint graphics_queue_family) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_DLSS_WITH_STREAMLINE)
    (void) env;
    (void) plugin_path;
    (void) log_path;
    (void) application_id;
    (void) vk_instance;
    (void) vk_physical_device;
    (void) vk_device;
    (void) vk_graphics_queue;
    (void) graphics_queue_family;
    g_initialized = false;
    g_supported = false;
    return -100;
#else
    if (application_id <= 0 || vk_instance == 0 || vk_physical_device == 0 || vk_device == 0 || vk_graphics_queue == 0) {
        return -1;
    }

    const std::wstring pluginPath = to_wstring(env, plugin_path);
    const std::wstring logPath = to_wstring(env, log_path);
    const wchar_t* pluginPaths[] = {pluginPath.c_str()};
    const sl::Feature features[] = {sl::kFeatureDLSS};

    sl::Preferences preferences{};
    preferences.showConsole = false;
    preferences.logLevel = sl::LogLevel::eDefault;
    preferences.pathsToPlugins = pluginPath.empty() ? nullptr : pluginPaths;
    preferences.numPathsToPlugins = pluginPath.empty() ? 0 : 1;
    preferences.pathToLogsAndData = logPath.empty() ? nullptr : logPath.c_str();
    preferences.applicationId = static_cast<uint32_t>(application_id);
    preferences.engine = sl::EngineType::eCustom;
    preferences.renderAPI = sl::RenderAPI::eVulkan;
    preferences.featuresToLoad = features;
    preferences.numFeaturesToLoad = 1;

    sl::Result result = slInit(preferences);
    if (result != sl::Result::eOk) {
        g_initialized = false;
        g_supported = false;
        return static_cast<jint>(result);
    }

    sl::VulkanInfo vulkanInfo{};
    vulkanInfo.instance = reinterpret_cast<VkInstance>(static_cast<uintptr_t>(vk_instance));
    vulkanInfo.physicalDevice = reinterpret_cast<VkPhysicalDevice>(static_cast<uintptr_t>(vk_physical_device));
    vulkanInfo.device = reinterpret_cast<VkDevice>(static_cast<uintptr_t>(vk_device));
    vulkanInfo.graphicsQueueFamily = static_cast<uint32_t>(graphics_queue_family);
    vulkanInfo.graphicsQueueIndex = 0;
    vulkanInfo.computeQueueFamily = static_cast<uint32_t>(graphics_queue_family);
    vulkanInfo.computeQueueIndex = 0;

    result = slSetVulkanInfo(vulkanInfo);
    if (result != sl::Result::eOk) {
        slShutdown();
        g_initialized = false;
        g_supported = false;
        return static_cast<jint>(result);
    }

    sl::AdapterInfo adapterInfo{};
    result = slIsFeatureSupported(sl::kFeatureDLSS, adapterInfo);
    g_initialized = true;
    g_supported = result == sl::Result::eOk;
    return g_supported ? 0 : static_cast<jint>(result);
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_isSupportedNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_initialized && g_supported ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_queryOptimalSettingsNative(
        JNIEnv* env,
        jclass,
        jint quality_preset,
        jint output_width,
        jint output_height,
        jintArray out_settings,
        jfloatArray out_sharpness) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized || !g_supported || output_width <= 0 || output_height <= 0) {
        return -1;
    }

#if !defined(SALTS_DLSS_WITH_STREAMLINE)
    (void) quality_preset;
    return -100;
#else
    sl::DLSSOptions options{};
    options.mode = to_dlss_mode(quality_preset);
    options.outputWidth = static_cast<uint32_t>(output_width);
    options.outputHeight = static_cast<uint32_t>(output_height);
    options.useAutoExposure = sl::Boolean::eTrue;

    sl::DLSSOptimalSettings settings{};
    sl::Result result = slDLSSGetOptimalSettings(options, settings);
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    std::array<jint, 6> values = {
            static_cast<jint>(settings.optimalRenderWidth),
            static_cast<jint>(settings.optimalRenderHeight),
            static_cast<jint>(settings.renderWidthMin),
            static_cast<jint>(settings.renderHeightMin),
            static_cast<jint>(settings.renderWidthMax),
            static_cast<jint>(settings.renderHeightMax)
    };
    jfloat sharpness = settings.optimalSharpness;
    env->SetIntArrayRegion(out_settings, 0, static_cast<jsize>(values.size()), values.data());
    env->SetFloatArrayRegion(out_sharpness, 0, 1, &sharpness);
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_evaluateNative(
        JNIEnv* env,
        jclass,
        jlong command_buffer,
        jlong input_color_image,
        jlong input_color_view,
        jlong output_color_image,
        jlong output_color_view,
        jlong depth_image,
        jlong depth_view,
        jlong motion_vector_image,
        jlong motion_vector_view,
        jint render_width,
        jint render_height,
        jint output_width,
        jint output_height,
        jfloat jitter_x,
        jfloat jitter_y,
        jboolean reset_history,
        jlong frame_index,
        jfloat motion_vector_scale_x,
        jfloat motion_vector_scale_y,
        jfloatArray current_view_projection,
        jfloatArray previous_view_projection) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized || !g_supported) {
        return -1;
    }

#if !defined(SALTS_DLSS_WITH_STREAMLINE)
    (void) env;
    (void) command_buffer;
    (void) input_color_image;
    (void) input_color_view;
    (void) output_color_image;
    (void) output_color_view;
    (void) depth_image;
    (void) depth_view;
    (void) motion_vector_image;
    (void) motion_vector_view;
    (void) render_width;
    (void) render_height;
    (void) output_width;
    (void) output_height;
    (void) jitter_x;
    (void) jitter_y;
    (void) reset_history;
    (void) frame_index;
    (void) motion_vector_scale_x;
    (void) motion_vector_scale_y;
    (void) current_view_projection;
    (void) previous_view_projection;
    return -100;
#else
    sl::float4x4 current = to_float4x4(env, current_view_projection);
    sl::float4x4 previous = to_float4x4(env, previous_view_projection);
    sl::float4x4 currentInverse{};
    sl::float4x4 clipToPrev{};
    sl::float4x4 prevClipToClip{};
    matrixFullInvert(currentInverse, current);
    matrixMul(clipToPrev, currentInverse, previous);
    matrixFullInvert(prevClipToClip, clipToPrev);

    sl::FrameToken* token{};
    const uint32_t frameIndex = static_cast<uint32_t>(frame_index);
    sl::Result result = slGetNewFrameToken(token, &frameIndex);
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    sl::ViewportHandle viewport{0};
    sl::Extent renderExtent{0, 0, static_cast<uint32_t>(render_width), static_cast<uint32_t>(render_height)};
    sl::Extent outputExtent{0, 0, static_cast<uint32_t>(output_width), static_cast<uint32_t>(output_height)};

    sl::Resource colorIn = image_resource(input_color_image, input_color_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height);
    sl::Resource colorOut = image_resource(output_color_image, output_color_view, VK_IMAGE_LAYOUT_GENERAL, output_width, output_height);
    sl::Resource depth = image_resource(depth_image, depth_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height);
    sl::Resource motion = image_resource(motion_vector_image, motion_vector_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height);

    sl::ResourceTag tags[] = {
            {&colorIn, sl::kBufferTypeScalingInputColor, sl::ResourceLifecycle::eOnlyValidNow, &renderExtent},
            {&colorOut, sl::kBufferTypeScalingOutputColor, sl::ResourceLifecycle::eOnlyValidNow, &outputExtent},
            {&depth, sl::kBufferTypeDepth, sl::ResourceLifecycle::eValidUntilPresent, &renderExtent},
            {&motion, sl::kBufferTypeMotionVectors, sl::ResourceLifecycle::eOnlyValidNow, &renderExtent}
    };
    result = slSetTagForFrame(*token, viewport, tags, 4, reinterpret_cast<sl::CommandBuffer*>(static_cast<uintptr_t>(command_buffer)));
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    sl::Constants constants{};
    constants.mvecScale = {motion_vector_scale_x, motion_vector_scale_y};
    constants.jitterOffset = {jitter_x, jitter_y};
    constants.reset = reset_history ? sl::Boolean::eTrue : sl::Boolean::eFalse;
    constants.depthInverted = sl::Boolean::eFalse;
    constants.cameraMotionIncluded = sl::Boolean::eTrue;
    constants.motionVectors3D = sl::Boolean::eFalse;
    constants.cameraViewToClip = current;
    constants.clipToCameraView = currentInverse;
    constants.clipToPrevClip = clipToPrev;
    constants.prevClipToClip = prevClipToClip;

    result = slSetConstants(constants, *token, viewport);
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    sl::BaseStructure* inputs[] = {&viewport};
    const sl::BaseStructure* constInputs[] = {inputs[0]};
    result = slEvaluateFeature(
            sl::kFeatureDLSS,
            *token,
            constInputs,
            1,
            reinterpret_cast<sl::CommandBuffer*>(static_cast<uintptr_t>(command_buffer)));
    return result != sl::Result::eOk ? static_cast<jint>(result) : 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_shutdownNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
#if defined(SALTS_DLSS_WITH_STREAMLINE)
    if (g_initialized) {
        slFreeResources(sl::kFeatureDLSS, sl::ViewportHandle{0});
        slShutdown();
    }
#endif
    g_initialized = false;
    g_supported = false;
}
