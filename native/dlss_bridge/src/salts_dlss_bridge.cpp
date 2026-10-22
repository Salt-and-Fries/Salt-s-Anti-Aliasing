#include <jni.h>

#include <array>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

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
using BeforePresent = VkResult (*)(VkQueue, const VkPresentInfoKHR*, bool&);
using AfterPresent = VkResult (*)();
BeforePresent g_beforePresent{};
AfterPresent g_afterPresent{};

sl::DLSSMode to_dlss_mode(jint quality_preset, jint output_height) {
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
            // Streamline has no Auto enum; select a predictable policy from output resolution.
            return output_height <= 1080 ? sl::DLSSMode::eMaxQuality
                    : output_height <= 1440 ? sl::DLSSMode::eBalanced : sl::DLSSMode::eMaxPerformance;
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

sl::Resource image_resource(jlong image, jlong view, uint32_t layout, uint32_t width, uint32_t height,
                            uint32_t format, uint32_t usage) {
    sl::Resource resource(
            sl::ResourceType::eTex2d,
            reinterpret_cast<void*>(static_cast<uintptr_t>(image)),
            nullptr,
            reinterpret_cast<void*>(static_cast<uintptr_t>(view)),
            layout);
    resource.width = width;
    resource.height = height;
    resource.nativeFormat = format;
    resource.mipLevels = 1;
    resource.arrayLayers = 1;
    resource.flags = 0;
    resource.usage = usage;
    return resource;
}
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_preInitializeNative(
        JNIEnv* env,
        jclass,
        jstring plugin_path,
        jstring log_path,
        jint application_id) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_DLSS_WITH_STREAMLINE)
    (void) env;
    (void) plugin_path;
    (void) log_path;
    (void) application_id;
    g_initialized = false;
    g_supported = false;
    return -100;
#else
    if (g_initialized) return 0;

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
    // The SDK documents ProjectID initialization when no NVIDIA-issued application ID exists.
    // When NVIDIA supplies one, prefer it instead of still taking the ProjectID path.
    preferences.engineVersion = application_id > 0 ? nullptr : "Minecraft-RenderPearl-26.3";
    preferences.projectId = application_id > 0 ? nullptr : "15777de6-c4ac-4992-988c-12af5c9c6058";
    preferences.flags = sl::PreferenceFlags::eUseManualHooking | sl::PreferenceFlags::eDisableCLStateTracking
            | sl::PreferenceFlags::eUseFrameBasedResourceTagging;
    preferences.renderAPI = sl::RenderAPI::eVulkan;
    preferences.featuresToLoad = features;
    preferences.numFeaturesToLoad = 1;

    sl::Result result = slInit(preferences);
    if (result != sl::Result::eOk) {
        g_initialized = false;
        g_supported = false;
        return static_cast<jint>(result);
    }

    g_initialized = true;
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_abiVersionNative(JNIEnv*, jclass) {
    return 2;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_requirementsNative(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::vector<std::string> values;
#if defined(SALTS_DLSS_WITH_STREAMLINE)
    if (g_initialized) {
        sl::FeatureRequirements requirements{};
        if (slGetFeatureRequirements(sl::kFeatureDLSS, requirements) == sl::Result::eOk) {
            auto append = [&values](const char* prefix, uint32_t count, const char** names) {
                for (uint32_t i = 0; i < count; ++i) values.emplace_back(std::string(prefix) + names[i]);
            };
            append("instance:", requirements.vkNumInstanceExtensions, requirements.vkInstanceExtensions);
            append("device:", requirements.vkNumDeviceExtensions, requirements.vkDeviceExtensions);
            append("12:", requirements.vkNumFeatures12, requirements.vkFeatures12);
            append("13:", requirements.vkNumFeatures13, requirements.vkFeatures13);
            values.emplace_back("graphicsQueues:" + std::to_string(requirements.vkNumGraphicsQueuesRequired));
            values.emplace_back("computeQueues:" + std::to_string(requirements.vkNumComputeQueuesRequired));
        }
    }
#endif
    auto array = env->NewObjectArray(static_cast<jsize>(values.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < values.size(); ++i) {
        auto value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(array, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return array;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_initializeDeviceNative(
        JNIEnv*, jclass, jlong vk_instance, jlong vk_physical_device, jlong vk_device,
        jlong vk_graphics_queue, jint graphics_queue_family) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized || vk_instance == 0 || vk_physical_device == 0 || vk_device == 0 || vk_graphics_queue == 0) return -1;
#if !defined(SALTS_DLSS_WITH_STREAMLINE)
    return -100;
#else
    sl::VulkanInfo vulkanInfo{};
    vulkanInfo.instance = reinterpret_cast<VkInstance>(static_cast<uintptr_t>(vk_instance));
    vulkanInfo.physicalDevice = reinterpret_cast<VkPhysicalDevice>(static_cast<uintptr_t>(vk_physical_device));
    vulkanInfo.device = reinterpret_cast<VkDevice>(static_cast<uintptr_t>(vk_device));
    vulkanInfo.graphicsQueueFamily = static_cast<uint32_t>(graphics_queue_family);
    vulkanInfo.graphicsQueueIndex = 0;
    vulkanInfo.computeQueueFamily = static_cast<uint32_t>(graphics_queue_family);
    vulkanInfo.computeQueueIndex = 0;

    sl::Result result = slSetVulkanInfo(vulkanInfo);
    if (result != sl::Result::eOk) {
        slShutdown();
        g_initialized = false;
        g_supported = false;
        return static_cast<jint>(result);
    }

    sl::AdapterInfo adapterInfo{};
    adapterInfo.vkPhysicalDevice = vulkanInfo.physicalDevice;
    result = slIsFeatureSupported(sl::kFeatureDLSS, adapterInfo);
    if (result == sl::Result::eOk) {
        result = slGetFeatureFunction(sl::kFeatureCommon, "slHookVkPresent", reinterpret_cast<void*&>(g_beforePresent));
        if (result == sl::Result::eOk) {
            result = slGetFeatureFunction(sl::kFeatureCommon, "slHookVkAfterPresent", reinterpret_cast<void*&>(g_afterPresent));
        }
    }
    g_initialized = true;
    g_supported = result == sl::Result::eOk;
    return g_supported ? 0 : static_cast<jint>(result);
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_presentNative(
        JNIEnv*, jclass, jlong queue, jlong present_info, jboolean after) {
    std::lock_guard<std::mutex> lock(g_mutex);
#if defined(SALTS_DLSS_WITH_STREAMLINE)
    if (g_initialized && g_supported) {
        if (after) return static_cast<jint>(g_afterPresent());
        bool skip = false;
        return static_cast<jint>(g_beforePresent(reinterpret_cast<VkQueue>(static_cast<uintptr_t>(queue)),
                reinterpret_cast<const VkPresentInfoKHR*>(static_cast<uintptr_t>(present_info)), skip));
    }
#endif
    return 0;
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
    options.mode = to_dlss_mode(quality_preset, output_height);
    options.outputWidth = static_cast<uint32_t>(output_width);
    options.outputHeight = static_cast<uint32_t>(output_height);
    options.useAutoExposure = sl::Boolean::eTrue;
    options.colorBuffersHDR = sl::Boolean::eFalse;

    sl::DLSSOptimalSettings settings{};
    sl::Result result = slDLSSGetOptimalSettings(options, settings);
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }
    result = slDLSSSetOptions(sl::ViewportHandle{0}, options);
    if (result != sl::Result::eOk) return static_cast<jint>(result);

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
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_dlss_DlssNativeBridge_evaluateNativeV2(
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
        jfloatArray previous_view_projection,
        jfloatArray projection_matrix,
        jfloatArray camera_parameters,
        jintArray resource_formats,
        jintArray resource_usages,
        jlong rejection_image,
        jlong rejection_view) {
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
    (void) projection_matrix;
    (void) camera_parameters;
    (void) resource_formats;
    (void) resource_usages;
    (void) rejection_image;
    (void) rejection_view;
    return -100;
#else
    sl::float4x4 current = to_float4x4(env, current_view_projection);
    sl::float4x4 previous = to_float4x4(env, previous_view_projection);
    sl::float4x4 currentInverse{};
    sl::float4x4 clipToPrev{};
    sl::float4x4 prevClipToClip{};
    sl::float4x4 projection = to_float4x4(env, projection_matrix);
    sl::float4x4 projectionInverse{};
    matrixFullInvert(projectionInverse, projection);
    std::array<float, 16> camera{};
    std::array<jint, 5> formats{}, usages{};
    env->GetFloatArrayRegion(camera_parameters, 0, 16, camera.data());
    env->GetIntArrayRegion(resource_formats, 0, 5, formats.data());
    env->GetIntArrayRegion(resource_usages, 0, 5, usages.data());
    if (env->ExceptionCheck()) return -2;
    matrixFullInvert(currentInverse, current);
    matrixMul(clipToPrev, currentInverse, previous);
    matrixFullInvert(prevClipToClip, clipToPrev);

    sl::FrameToken* token{};
    // SDK-owned indices remain monotonic when Minecraft resets its temporal history or changes modes.
    sl::Result result = slGetNewFrameToken(token);
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    sl::ViewportHandle viewport{0};
    sl::Extent renderExtent{0, 0, static_cast<uint32_t>(render_width), static_cast<uint32_t>(render_height)};
    sl::Extent outputExtent{0, 0, static_cast<uint32_t>(output_width), static_cast<uint32_t>(output_height)};

    sl::Resource colorIn = image_resource(input_color_image, input_color_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height, formats[0], usages[0]);
    sl::Resource colorOut = image_resource(output_color_image, output_color_view, VK_IMAGE_LAYOUT_GENERAL, output_width, output_height, formats[1], usages[1]);
    sl::Resource depth = image_resource(depth_image, depth_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height, formats[2], usages[2]);
    sl::Resource motion = image_resource(motion_vector_image, motion_vector_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height, formats[3], usages[3]);
    sl::Resource rejection = image_resource(rejection_image, rejection_view, VK_IMAGE_LAYOUT_GENERAL, render_width, render_height, formats[4], usages[4]);

    sl::ResourceTag tags[] = {
            {&colorIn, sl::kBufferTypeScalingInputColor, sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&colorOut, sl::kBufferTypeScalingOutputColor, sl::ResourceLifecycle::eValidUntilEvaluate, &outputExtent},
            {&depth, sl::kBufferTypeDepth, sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&motion, sl::kBufferTypeMotionVectors, sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&rejection, sl::kBufferTypeBiasCurrentColorHint, sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent}
    };
    result = slSetTagForFrame(*token, viewport, tags, 5, reinterpret_cast<sl::CommandBuffer*>(static_cast<uintptr_t>(command_buffer)));
    if (result != sl::Result::eOk) {
        return static_cast<jint>(result);
    }

    sl::Constants constants{};
    constants.mvecScale = {motion_vector_scale_x, motion_vector_scale_y};
    constants.jitterOffset = {jitter_x, jitter_y};
    constants.reset = reset_history ? sl::Boolean::eTrue : sl::Boolean::eFalse;
    constants.depthInverted = sl::Boolean::eTrue;
    constants.cameraMotionIncluded = sl::Boolean::eTrue;
    constants.motionVectors3D = sl::Boolean::eFalse;
    constants.cameraViewToClip = projection;
    constants.clipToCameraView = projectionInverse;
    constants.clipToPrevClip = clipToPrev;
    constants.prevClipToClip = prevClipToClip;
    constants.cameraNear = camera[0];
    constants.cameraFar = camera[1];
    constants.cameraFOV = camera[2];
    constants.cameraAspectRatio = camera[3];
    constants.cameraPos = {camera[4], camera[5], camera[6]};
    constants.cameraUp = {camera[7], camera[8], camera[9]};
    constants.cameraRight = {camera[10], camera[11], camera[12]};
    constants.cameraFwd = {camera[13], camera[14], camera[15]};

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
