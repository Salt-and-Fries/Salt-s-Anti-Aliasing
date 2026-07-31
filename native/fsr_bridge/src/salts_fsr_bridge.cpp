#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

#if defined(SALTS_FSR_WITH_FFX_SDK)
#include <vulkan/vulkan.h>
#include <ffx_api/ffx_api.h>
#include <ffx_api/ffx_upscale.h>
#include <ffx_api/ffx_framegeneration.h>
#include <ffx_api/vk/ffx_api_vk.h>
#endif

namespace {
std::mutex g_mutex;
bool g_initialized = false;
bool g_upscaling_supported = false;
bool g_frame_generation_ready = false;

#if defined(SALTS_FSR_WITH_FFX_SDK)
#if defined(_WIN32)
using ModuleHandle = HMODULE;
#else
using ModuleHandle = void*;
#endif

ModuleHandle g_ffx_module = nullptr;
ModuleHandle g_vulkan_module = nullptr;
PfnFfxCreateContext g_ffx_create_context = nullptr;
PfnFfxDestroyContext g_ffx_destroy_context = nullptr;
PfnFfxConfigure g_ffx_configure = nullptr;
PfnFfxQuery g_ffx_query = nullptr;
PfnFfxDispatch g_ffx_dispatch = nullptr;
PFN_vkGetInstanceProcAddr g_vk_get_instance_proc_addr = nullptr;
PFN_vkGetDeviceProcAddr g_vk_get_device_proc_addr = nullptr;
PFN_vkGetPhysicalDeviceSurfaceSupportKHR g_vk_get_physical_device_surface_support_khr = nullptr;

VkInstance g_vk_instance = VK_NULL_HANDLE;
VkPhysicalDevice g_vk_physical_device = VK_NULL_HANDLE;
VkDevice g_vk_device = VK_NULL_HANDLE;
VkQueue g_vk_graphics_queue = VK_NULL_HANDLE;
uint32_t g_vk_graphics_queue_family = 0;
VkQueue g_vk_compute_queue = VK_NULL_HANDLE;
uint32_t g_vk_compute_queue_family = 0;
VkQueue g_vk_transfer_queue = VK_NULL_HANDLE;
uint32_t g_vk_transfer_queue_family = 0;

ffxContext g_upscale_context = nullptr;
ffxContext g_frame_generation_context = nullptr;
ffxContext g_frame_generation_swapchain_context = nullptr;
ffxQueryDescSwapchainReplacementFunctionsVK g_swapchain_replacement_functions{};
VkSwapchainKHR g_frame_generation_swapchain = VK_NULL_HANDLE;
uint32_t g_frame_generation_display_width = 0;
uint32_t g_frame_generation_display_height = 0;
uint32_t g_frame_generation_backbuffer_format = FFX_API_SURFACE_FORMAT_UNKNOWN;
uint64_t g_frame_generation_frame_id = 0;
bool g_frame_generation_frame_id_initialized = false;
uint64_t g_fsr2_version_id = 0;
uint64_t g_fsr3_version_id = 0;
bool g_frame_generation_provider_available = false;
bool g_frame_generation_swapchain_provider_available = false;
int g_context_fsr_version = 0;
uint32_t g_context_max_render_width = 0;
uint32_t g_context_max_render_height = 0;
uint32_t g_context_max_output_width = 0;
uint32_t g_context_max_output_height = 0;

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

#if defined(_WIN32)
bool ends_with_dll(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), [](wchar_t ch) {
        return static_cast<wchar_t>(towlower(ch));
    });
    return value.ends_with(L".dll");
}

ModuleHandle load_library_path(const std::wstring& path) {
    return LoadLibraryW(path.c_str());
}

void* load_symbol(ModuleHandle module, const char* name) {
    return module == nullptr ? nullptr : reinterpret_cast<void*>(GetProcAddress(module, name));
}

void unload_library(ModuleHandle module) {
    if (module != nullptr) {
        FreeLibrary(module);
    }
}

bool load_ffx_runtime(const std::wstring& runtime_path) {
    if (g_ffx_module != nullptr) {
        return true;
    }

    std::vector<std::wstring> candidates;
    if (!runtime_path.empty()) {
        candidates.push_back(runtime_path);
        if (!ends_with_dll(runtime_path)) {
            wchar_t separator = runtime_path.ends_with(L"\\") || runtime_path.ends_with(L"/") ? L'\0' : L'\\';
            candidates.push_back(separator == L'\0'
                    ? runtime_path + L"amd_fidelityfx_vk.dll"
                    : runtime_path + separator + L"amd_fidelityfx_vk.dll");
        }
    }
    candidates.push_back(L"amd_fidelityfx_vk.dll");

    for (const std::wstring& candidate : candidates) {
        g_ffx_module = load_library_path(candidate);
        if (g_ffx_module != nullptr) {
            return true;
        }
    }
    return false;
}

bool load_vulkan_loader() {
    if (g_vk_get_device_proc_addr != nullptr) {
        return true;
    }

    g_vulkan_module = LoadLibraryW(L"vulkan-1.dll");
    if (g_vulkan_module == nullptr) {
        return false;
    }

    g_vk_get_device_proc_addr = reinterpret_cast<PFN_vkGetDeviceProcAddr>(
            GetProcAddress(g_vulkan_module, "vkGetDeviceProcAddr"));
    g_vk_get_instance_proc_addr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            GetProcAddress(g_vulkan_module, "vkGetInstanceProcAddr"));
    return g_vk_get_device_proc_addr != nullptr && g_vk_get_instance_proc_addr != nullptr;
}
#else
std::string to_utf8(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool ends_with_so(const std::string& value) {
    return value.ends_with(".so") || value.find(".so.") != std::string::npos;
}

ModuleHandle load_library_path(const std::string& path) {
    return dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
}

void* load_symbol(ModuleHandle module, const char* name) {
    return module == nullptr ? nullptr : dlsym(module, name);
}

void unload_library(ModuleHandle module) {
    if (module != nullptr) {
        dlclose(module);
    }
}

bool load_ffx_runtime(JNIEnv* env, jstring runtime_path_value) {
    if (g_ffx_module != nullptr) {
        return true;
    }

    const std::string runtime_path = to_utf8(env, runtime_path_value);
    std::vector<std::string> candidates;
    if (!runtime_path.empty()) {
        candidates.push_back(runtime_path);
        if (!ends_with_so(runtime_path)) {
            char separator = runtime_path.ends_with("/") ? '\0' : '/';
            candidates.push_back(separator == '\0'
                    ? runtime_path + "libamd_fidelityfx_vk.so"
                    : runtime_path + separator + "libamd_fidelityfx_vk.so");
        }
    }
    candidates.push_back("libamd_fidelityfx_vk.so");

    for (const std::string& candidate : candidates) {
        g_ffx_module = load_library_path(candidate);
        if (g_ffx_module != nullptr) {
            return true;
        }
    }
    return false;
}

bool load_vulkan_loader() {
    if (g_vk_get_device_proc_addr != nullptr) {
        return true;
    }

    g_vulkan_module = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (g_vulkan_module == nullptr) {
        g_vulkan_module = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (g_vulkan_module == nullptr) {
        return false;
    }

    g_vk_get_device_proc_addr = reinterpret_cast<PFN_vkGetDeviceProcAddr>(
            dlsym(g_vulkan_module, "vkGetDeviceProcAddr"));
    g_vk_get_instance_proc_addr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(g_vulkan_module, "vkGetInstanceProcAddr"));
    return g_vk_get_device_proc_addr != nullptr && g_vk_get_instance_proc_addr != nullptr;
}
#endif

bool load_ffx_symbols() {
    g_ffx_create_context = reinterpret_cast<PfnFfxCreateContext>(load_symbol(g_ffx_module, "ffxCreateContext"));
    g_ffx_destroy_context = reinterpret_cast<PfnFfxDestroyContext>(load_symbol(g_ffx_module, "ffxDestroyContext"));
    g_ffx_configure = reinterpret_cast<PfnFfxConfigure>(load_symbol(g_ffx_module, "ffxConfigure"));
    g_ffx_query = reinterpret_cast<PfnFfxQuery>(load_symbol(g_ffx_module, "ffxQuery"));
    g_ffx_dispatch = reinterpret_cast<PfnFfxDispatch>(load_symbol(g_ffx_module, "ffxDispatch"));
    return g_ffx_create_context != nullptr
            && g_ffx_destroy_context != nullptr
            && g_ffx_configure != nullptr
            && g_ffx_query != nullptr
            && g_ffx_dispatch != nullptr;
}

template <typename T>
T handle_from_jlong(jlong value) {
    return (T) static_cast<uintptr_t>(value);
}

bool has_vulkan_device_proc(const char* name) {
    return g_vk_get_device_proc_addr != nullptr
            && g_vk_device != VK_NULL_HANDLE
            && g_vk_get_device_proc_addr(g_vk_device, name) != nullptr;
}

bool physical_device_has_extension(const char* name) {
    if (g_vk_get_instance_proc_addr == nullptr
            || g_vk_instance == VK_NULL_HANDLE
            || g_vk_physical_device == VK_NULL_HANDLE) {
        return false;
    }

    auto enumerate_device_extension_properties = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
            g_vk_get_instance_proc_addr(g_vk_instance, "vkEnumerateDeviceExtensionProperties"));
    if (enumerate_device_extension_properties == nullptr) {
        return false;
    }

    uint32_t extension_count = 0;
    VkResult result = enumerate_device_extension_properties(g_vk_physical_device, nullptr, &extension_count, nullptr);
    if (result != VK_SUCCESS || extension_count == 0) {
        return false;
    }

    std::vector<VkExtensionProperties> extensions(extension_count);
    result = enumerate_device_extension_properties(g_vk_physical_device, nullptr, &extension_count, extensions.data());
    if (result != VK_SUCCESS) {
        return false;
    }

    return std::any_of(extensions.begin(), extensions.end(), [name](const VkExtensionProperties& extension) {
        return std::strcmp(extension.extensionName, name) == 0;
    });
}

bool has_required_fidelityfx_vulkan_entrypoints() {
    if (!physical_device_has_extension(VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME)) {
        return true;
    }

    return has_vulkan_device_proc("vkGetBufferMemoryRequirements2KHR");
}

bool load_vulkan_surface_symbols() {
    if (g_vk_get_physical_device_surface_support_khr != nullptr) {
        return true;
    }
    if (g_vk_get_instance_proc_addr == nullptr || g_vk_instance == VK_NULL_HANDLE) {
        return false;
    }
    g_vk_get_physical_device_surface_support_khr = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(
            g_vk_get_instance_proc_addr(g_vk_instance, "vkGetPhysicalDeviceSurfaceSupportKHR"));
    return g_vk_get_physical_device_surface_support_khr != nullptr;
}

uint32_t quality_mode(jint quality_preset) {
    switch (quality_preset) {
        case 0:
            return FFX_UPSCALE_QUALITY_MODE_NATIVEAA;
        case 1:
            return FFX_UPSCALE_QUALITY_MODE_QUALITY;
        case 2:
            return FFX_UPSCALE_QUALITY_MODE_BALANCED;
        case 3:
            return FFX_UPSCALE_QUALITY_MODE_PERFORMANCE;
        case 4:
        default:
            return FFX_UPSCALE_QUALITY_MODE_ULTRA_PERFORMANCE;
    }
}

bool query_upscale_versions() {
    uint64_t count = 0;
    ffxQueryDescGetVersions count_query{};
    count_query.header.type = FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
    count_query.createDescType = FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;
    count_query.device = g_vk_device;
    count_query.outputCount = &count;
    ffxReturnCode_t result = g_ffx_query(nullptr, &count_query.header);
    if (result != FFX_API_RETURN_OK || count == 0) {
        return false;
    }

    std::vector<uint64_t> ids(count);
    std::vector<const char*> names(count);
    ffxQueryDescGetVersions version_query{};
    version_query.header.type = FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
    version_query.createDescType = FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;
    version_query.device = g_vk_device;
    version_query.outputCount = &count;
    version_query.versionIds = ids.data();
    version_query.versionNames = names.data();
    result = g_ffx_query(nullptr, &version_query.header);
    if (result != FFX_API_RETURN_OK || count == 0) {
        return false;
    }

    g_fsr2_version_id = 0;
    g_fsr3_version_id = 0;
    for (uint64_t index = 0; index < count; index++) {
        const char* name = names[index];
        if (name != nullptr && name[0] == '2') {
            g_fsr2_version_id = ids[index];
        } else if (name != nullptr && name[0] == '3') {
            g_fsr3_version_id = ids[index];
        }
    }

    return g_fsr2_version_id != 0 && g_fsr3_version_id != 0;
}

bool query_provider_available(uint64_t create_desc_type) {
    uint64_t count = 0;
    ffxQueryDescGetVersions query{};
    query.header.type = FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
    query.createDescType = create_desc_type;
    query.device = g_vk_device;
    query.outputCount = &count;
    ffxReturnCode_t result = g_ffx_query(nullptr, &query.header);
    return result == FFX_API_RETURN_OK && count > 0;
}

void destroy_upscale_context() {
    if (g_upscale_context != nullptr && g_ffx_destroy_context != nullptr) {
        g_ffx_destroy_context(&g_upscale_context, nullptr);
    }
    g_upscale_context = nullptr;
    g_context_fsr_version = 0;
    g_context_max_render_width = 0;
    g_context_max_render_height = 0;
    g_context_max_output_width = 0;
    g_context_max_output_height = 0;
}

void destroy_frame_generation_context() {
    if (g_frame_generation_context != nullptr && g_ffx_destroy_context != nullptr) {
        ffxConfigureDescFrameGeneration config{};
        config.header.type = FFX_API_CONFIGURE_DESC_TYPE_FRAMEGENERATION;
        config.swapChain = reinterpret_cast<void*>(g_frame_generation_swapchain);
        config.frameGenerationEnabled = false;
        g_ffx_configure(&g_frame_generation_context, &config.header);
        g_ffx_destroy_context(&g_frame_generation_context, nullptr);
    }
    g_frame_generation_context = nullptr;
    g_frame_generation_display_width = 0;
    g_frame_generation_display_height = 0;
    g_frame_generation_backbuffer_format = FFX_API_SURFACE_FORMAT_UNKNOWN;
    g_frame_generation_frame_id = 0;
    g_frame_generation_frame_id_initialized = false;
    g_frame_generation_ready = false;
}

void destroy_frame_generation_swapchain_context() {
    destroy_frame_generation_context();
    if (g_frame_generation_swapchain_context != nullptr && g_ffx_destroy_context != nullptr) {
        g_ffx_destroy_context(&g_frame_generation_swapchain_context, nullptr);
    }
    g_frame_generation_swapchain_context = nullptr;
    g_swapchain_replacement_functions = {};
    g_frame_generation_swapchain = VK_NULL_HANDLE;
}

ffxReturnCode_t frame_generation_dispatch_callback(ffxDispatchDescFrameGeneration* params, void* user_context) {
    if (params == nullptr || user_context == nullptr || g_ffx_dispatch == nullptr) {
        return FFX_API_RETURN_ERROR_PARAMETER;
    }
    return g_ffx_dispatch(reinterpret_cast<ffxContext*>(user_context), &params->header);
}

ffxReturnCode_t ensure_frame_generation_context(
        uint32_t display_width,
        uint32_t display_height,
        uint32_t backbuffer_format) {
    if (g_frame_generation_context != nullptr
            && g_frame_generation_display_width == display_width
            && g_frame_generation_display_height == display_height
            && g_frame_generation_backbuffer_format == backbuffer_format) {
        return FFX_API_RETURN_OK;
    }

    destroy_frame_generation_context();
    if (!g_frame_generation_provider_available
            || g_frame_generation_swapchain == VK_NULL_HANDLE
            || display_width == 0
            || display_height == 0
            || backbuffer_format == FFX_API_SURFACE_FORMAT_UNKNOWN) {
        return FFX_API_RETURN_NO_PROVIDER;
    }

    ffxCreateBackendVKDesc backend{};
    backend.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_VK;
    backend.vkDevice = g_vk_device;
    backend.vkPhysicalDevice = g_vk_physical_device;
    backend.vkDeviceProcAddr = g_vk_get_device_proc_addr;

    ffxCreateContextDescFrameGenerationHudless hudless{};
    hudless.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION_HUDLESS;
    hudless.hudlessBackBufferFormat = FFX_API_SURFACE_FORMAT_R8G8B8A8_UNORM;
    backend.header.pNext = &hudless.header;

    ffxCreateContextDescFrameGeneration create_desc{};
    create_desc.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION;
    create_desc.header.pNext = &backend.header;
    // Frame generation consumes the same finite reverse-Z depth as the FSR3 upscaler.
    create_desc.flags = FFX_FRAMEGENERATION_ENABLE_DEPTH_INVERTED;
    create_desc.displaySize = {display_width, display_height};
    create_desc.maxRenderSize = {display_width, display_height};
    create_desc.backBufferFormat = backbuffer_format;

    ffxReturnCode_t result = g_ffx_create_context(&g_frame_generation_context, &create_desc.header, nullptr);
    if (result != FFX_API_RETURN_OK) {
        destroy_frame_generation_context();
        return result;
    }

    g_frame_generation_display_width = display_width;
    g_frame_generation_display_height = display_height;
    g_frame_generation_backbuffer_format = backbuffer_format;
    g_frame_generation_ready = true;
    return FFX_API_RETURN_OK;
}

struct QueueCandidate {
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t family = 0;
};

VkQueueInfoFFXAPI queue_info(QueueCandidate candidate) {
    VkQueueInfoFFXAPI result{};
    result.queue = candidate.queue;
    result.familyIndex = candidate.family;
    result.submitFunc = nullptr;
    return result;
}

bool queue_supports_present(uint32_t family, VkSurfaceKHR surface) {
    if (g_vk_get_physical_device_surface_support_khr == nullptr || surface == VK_NULL_HANDLE) {
        return false;
    }

    VkBool32 supported = VK_FALSE;
    VkResult result = g_vk_get_physical_device_surface_support_khr(g_vk_physical_device, family, surface, &supported);
    return result == VK_SUCCESS && supported == VK_TRUE;
}

bool distinct_queue(VkQueue queue, VkQueue a, VkQueue b = VK_NULL_HANDLE) {
    return queue != VK_NULL_HANDLE && queue != a && queue != b;
}

bool select_frame_generation_queues(
        VkSurfaceKHR surface,
        VkQueueInfoFFXAPI& async_compute_queue,
        VkQueueInfoFFXAPI& present_queue,
        VkQueueInfoFFXAPI& image_acquire_queue) {
    std::vector<QueueCandidate> candidates = {
            {g_vk_graphics_queue, g_vk_graphics_queue_family},
            {g_vk_compute_queue, g_vk_compute_queue_family},
            {g_vk_transfer_queue, g_vk_transfer_queue_family}
    };

    QueueCandidate async_compute = candidates[0];
    for (QueueCandidate candidate : candidates) {
        if (distinct_queue(candidate.queue, g_vk_graphics_queue)) {
            async_compute = candidate;
            break;
        }
    }

    QueueCandidate present{};
    for (QueueCandidate candidate : candidates) {
        if (candidate.queue != VK_NULL_HANDLE && queue_supports_present(candidate.family, surface)) {
            present = candidate;
            break;
        }
    }

    QueueCandidate image_acquire{};
    for (QueueCandidate candidate : candidates) {
        if (distinct_queue(candidate.queue, present.queue)) {
            image_acquire = candidate;
            break;
        }
    }
    if (image_acquire.queue == VK_NULL_HANDLE) {
        image_acquire = async_compute.queue != VK_NULL_HANDLE ? async_compute : present;
    }

    if (present.queue == VK_NULL_HANDLE || image_acquire.queue == VK_NULL_HANDLE || async_compute.queue == VK_NULL_HANDLE) {
        return false;
    }

    async_compute_queue = queue_info(async_compute);
    present_queue = queue_info(present);
    image_acquire_queue = queue_info(image_acquire);
    return true;
}

uint64_t version_id_for(int fsr_version) {
    return fsr_version == 2 ? g_fsr2_version_id : g_fsr3_version_id;
}

ffxReturnCode_t ensure_upscale_context(
        int fsr_version,
        uint32_t render_width,
        uint32_t render_height,
        uint32_t output_width,
        uint32_t output_height) {
    if (g_upscale_context != nullptr
            && g_context_fsr_version == fsr_version
            && render_width == g_context_max_render_width
            && render_height == g_context_max_render_height
            && output_width == g_context_max_output_width
            && output_height == g_context_max_output_height) {
        return FFX_API_RETURN_OK;
    }

    destroy_upscale_context();

    uint64_t version_id = version_id_for(fsr_version);
    if (version_id == 0) {
        return FFX_API_RETURN_NO_PROVIDER;
    }

    ffxCreateBackendVKDesc backend{};
    backend.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_VK;
    backend.vkDevice = g_vk_device;
    backend.vkPhysicalDevice = g_vk_physical_device;
    backend.vkDeviceProcAddr = g_vk_get_device_proc_addr;

    ffxOverrideVersion version_override{};
    version_override.header.type = FFX_API_DESC_TYPE_OVERRIDE_VERSION;
    version_override.versionId = version_id;
    backend.header.pNext = &version_override.header;

    ffxCreateContextDescUpscale create_desc{};
    create_desc.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;
    create_desc.header.pNext = &backend.header;
    // Java converts Minecraft's display-encoded scene to linear LDR before dispatch.
    create_desc.flags = FFX_UPSCALE_ENABLE_DEPTH_INVERTED | FFX_UPSCALE_ENABLE_AUTO_EXPOSURE;
    create_desc.maxRenderSize = {render_width, render_height};
    create_desc.maxUpscaleSize = {output_width, output_height};

    ffxReturnCode_t result = g_ffx_create_context(&g_upscale_context, &create_desc.header, nullptr);
    if (result != FFX_API_RETURN_OK) {
        destroy_upscale_context();
        return result;
    }

    g_context_fsr_version = fsr_version;
    g_context_max_render_width = render_width;
    g_context_max_render_height = render_height;
    g_context_max_output_width = output_width;
    g_context_max_output_height = output_height;
    return FFX_API_RETURN_OK;
}

VkResult create_frame_generation_swapchain(
        VkDevice device,
        const VkSwapchainCreateInfoKHR* create_info,
        const VkAllocationCallbacks* allocator,
        VkSwapchainKHR* out_swapchain) {
    if (!g_initialized
            || !g_frame_generation_provider_available
            || !g_frame_generation_swapchain_provider_available
            || create_info == nullptr
            || out_swapchain == nullptr
            || device != g_vk_device) {
        return VK_ERROR_FEATURE_NOT_PRESENT;
    }

    uint32_t backbuffer_format = ffxApiGetSurfaceFormatVK(create_info->imageFormat);
    if (backbuffer_format == FFX_API_SURFACE_FORMAT_UNKNOWN) {
        return VK_ERROR_FORMAT_NOT_SUPPORTED;
    }

    if (g_frame_generation_swapchain_context == nullptr) {
        VkQueueInfoFFXAPI async_compute_queue{};
        VkQueueInfoFFXAPI present_queue{};
        VkQueueInfoFFXAPI image_acquire_queue{};
        if (!select_frame_generation_queues(
                    create_info->surface,
                    async_compute_queue,
                    present_queue,
                    image_acquire_queue)) {
            return VK_ERROR_FEATURE_NOT_PRESENT;
        }

        VkSwapchainKHR swapchain = create_info->oldSwapchain;
        ffxCreateContextDescFrameGenerationSwapChainVK create_desc{};
        create_desc.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FGSWAPCHAIN_VK;
        create_desc.physicalDevice = g_vk_physical_device;
        create_desc.device = g_vk_device;
        create_desc.swapchain = &swapchain;
        create_desc.allocator = const_cast<VkAllocationCallbacks*>(allocator);
        create_desc.createInfo = *create_info;
        create_desc.gameQueue.queue = g_vk_graphics_queue;
        create_desc.gameQueue.familyIndex = g_vk_graphics_queue_family;
        create_desc.gameQueue.submitFunc = nullptr;
        create_desc.asyncComputeQueue = async_compute_queue;
        create_desc.presentQueue = present_queue;
        create_desc.imageAcquireQueue = image_acquire_queue;

        ffxReturnCode_t result = g_ffx_create_context(&g_frame_generation_swapchain_context, &create_desc.header, nullptr);
        if (result != FFX_API_RETURN_OK) {
            destroy_frame_generation_swapchain_context();
            return VK_ERROR_FEATURE_NOT_PRESENT;
        }

        g_frame_generation_swapchain = swapchain;
        *out_swapchain = swapchain;

        ffxQueryDescSwapchainReplacementFunctionsVK replacement_query{};
        replacement_query.header.type = FFX_API_QUERY_DESC_TYPE_FGSWAPCHAIN_FUNCTIONS_VK;
        result = g_ffx_query(&g_frame_generation_swapchain_context, &replacement_query.header);
        if (result != FFX_API_RETURN_OK
                || replacement_query.pOutCreateSwapchainFFXAPI == nullptr
                || replacement_query.pOutDestroySwapchainFFXAPI == nullptr
                || replacement_query.pOutGetSwapchainImagesKHR == nullptr
                || replacement_query.pOutAcquireNextImageKHR == nullptr
                || replacement_query.pOutQueuePresentKHR == nullptr) {
            destroy_frame_generation_swapchain_context();
            return VK_ERROR_FEATURE_NOT_PRESENT;
        }
        g_swapchain_replacement_functions = replacement_query;
    } else {
        if (g_swapchain_replacement_functions.pOutCreateSwapchainFFXAPI == nullptr) {
            return VK_ERROR_FEATURE_NOT_PRESENT;
        }
        VkResult result = g_swapchain_replacement_functions.pOutCreateSwapchainFFXAPI(
                device,
                create_info,
                allocator,
                out_swapchain,
                g_frame_generation_swapchain_context);
        if (result != VK_SUCCESS) {
            return result;
        }
        g_frame_generation_swapchain = *out_swapchain;
    }

    ffxReturnCode_t framegen_result = ensure_frame_generation_context(
            std::max(1u, create_info->imageExtent.width),
            std::max(1u, create_info->imageExtent.height),
            backbuffer_format);
    if (framegen_result != FFX_API_RETURN_OK) {
        destroy_frame_generation_swapchain_context();
        return VK_ERROR_FEATURE_NOT_PRESENT;
    }

    return VK_SUCCESS;
}

bool owns_frame_generation_swapchain(VkSwapchainKHR swapchain) {
    return g_frame_generation_swapchain_context != nullptr
            && swapchain != VK_NULL_HANDLE
            && swapchain == g_frame_generation_swapchain;
}

FfxApiResource resource(
        jlong image,
        uint32_t format,
        uint32_t width,
        uint32_t height,
        uint32_t usage,
        uint32_t state) {
    FfxApiResource result{};
    if (image == 0 || width == 0 || height == 0) {
        return result;
    }

    result.resource = reinterpret_cast<void*>(static_cast<uintptr_t>(image));
    result.description.type = FFX_API_RESOURCE_TYPE_TEXTURE2D;
    result.description.format = format;
    result.description.width = width;
    result.description.height = height;
    result.description.depth = 1;
    result.description.mipCount = 1;
    result.description.flags = FFX_API_RESOURCE_FLAGS_NONE;
    result.description.usage = usage;
    result.state = state;
    return result;
}

FfxApiResource color_input(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R16G16B16A16_FLOAT,
            width,
            height,
            FFX_API_RESOURCE_USAGE_READ_ONLY,
            FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ);
}

FfxApiResource output_color(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R16G16B16A16_FLOAT,
            width,
            height,
            FFX_API_RESOURCE_USAGE_UAV | FFX_API_RESOURCE_USAGE_RENDERTARGET,
            FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
}

FfxApiResource hudless_color(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R8G8B8A8_UNORM,
            width,
            height,
            FFX_API_RESOURCE_USAGE_READ_ONLY,
            FFX_API_RESOURCE_STATE_COMPUTE_READ);
}

FfxApiResource depth_input(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R32_FLOAT,
            width,
            height,
            FFX_API_RESOURCE_USAGE_DEPTHTARGET,
            FFX_API_RESOURCE_STATE_COMPUTE_READ);
}

FfxApiResource motion_input(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R16G16_FLOAT,
            width,
            height,
            FFX_API_RESOURCE_USAGE_READ_ONLY,
            FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ);
}

FfxApiResource mask_input(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R8_UNORM,
            width,
            height,
            FFX_API_RESOURCE_USAGE_READ_ONLY,
            FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ);
}

FfxApiResource mask_output(jlong image, uint32_t width, uint32_t height) {
    return resource(
            image,
            FFX_API_SURFACE_FORMAT_R8_UNORM,
            width,
            height,
            FFX_API_RESOURCE_USAGE_UAV,
            // FidelityFX restores imported resources to this state when reactive-mask
            // generation finishes. Returning the mask to a readable state inserts the
            // required UAV-write -> SRV-read barrier before the following upscale dispatch.
            FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ);
}

ffxReturnCode_t generate_reactive_mask(
        void* command_list,
        jlong opaque_color_image,
        jlong input_color_image,
        jlong reactive_mask_image,
        uint32_t render_width,
        uint32_t render_height) {
    if (opaque_color_image == 0 || input_color_image == 0 || reactive_mask_image == 0) {
        return FFX_API_RETURN_OK;
    }

    ffxDispatchDescUpscaleGenerateReactiveMask desc{};
    desc.header.type = FFX_API_DISPATCH_DESC_TYPE_UPSCALE_GENERATEREACTIVEMASK;
    desc.commandList = command_list;
    desc.colorOpaqueOnly = color_input(opaque_color_image, render_width, render_height);
    desc.colorPreUpscale = color_input(input_color_image, render_width, render_height);
    desc.outReactive = mask_output(reactive_mask_image, render_width, render_height);
    desc.renderSize = {render_width, render_height};
    desc.scale = 1.0f;
    desc.cutoffThreshold = 0.2f;
    desc.binaryValue = 0.9f;
    desc.flags = FFX_UPSCALE_AUTOREACTIVEFLAGS_APPLY_THRESHOLD
            | FFX_UPSCALE_AUTOREACTIVEFLAGS_USE_COMPONENTS_MAX;
    return g_ffx_dispatch(&g_upscale_context, &desc.header);
}
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_initializeNative(
        JNIEnv* env,
        jclass,
        jstring runtime_path,
        jstring log_path,
        jlong vk_instance,
        jlong vk_physical_device,
        jlong vk_device,
        jlong vk_graphics_queue,
        jint graphics_queue_family,
        jlong vk_compute_queue,
        jint compute_queue_family,
        jlong vk_transfer_queue,
        jint transfer_queue_family) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) env;
    (void) runtime_path;
    (void) log_path;
    (void) vk_instance;
    (void) vk_physical_device;
    (void) vk_device;
    (void) vk_graphics_queue;
    (void) graphics_queue_family;
    (void) vk_compute_queue;
    (void) compute_queue_family;
    (void) vk_transfer_queue;
    (void) transfer_queue_family;
    g_initialized = false;
    g_upscaling_supported = false;
    g_frame_generation_ready = false;
    return -100;
#else
    (void) log_path;
    if (vk_instance == 0
            || vk_physical_device == 0
            || vk_device == 0
            || vk_graphics_queue == 0
            || graphics_queue_family < 0
            || vk_compute_queue == 0
            || compute_queue_family < 0
            || vk_transfer_queue == 0
            || transfer_queue_family < 0) {
        return -1;
    }

#if defined(_WIN32)
    if (!load_ffx_runtime(to_wstring(env, runtime_path))) {
        return -2;
    }
#else
    if (!load_ffx_runtime(env, runtime_path)) {
        return -2;
    }
#endif
    if (!load_ffx_symbols()) {
        return -3;
    }
    if (!load_vulkan_loader()) {
        return -4;
    }

    g_vk_instance = handle_from_jlong<VkInstance>(vk_instance);
    g_vk_physical_device = handle_from_jlong<VkPhysicalDevice>(vk_physical_device);
    g_vk_device = handle_from_jlong<VkDevice>(vk_device);
    g_vk_graphics_queue = handle_from_jlong<VkQueue>(vk_graphics_queue);
    g_vk_graphics_queue_family = static_cast<uint32_t>(graphics_queue_family);
    g_vk_compute_queue = handle_from_jlong<VkQueue>(vk_compute_queue);
    g_vk_compute_queue_family = static_cast<uint32_t>(compute_queue_family);
    g_vk_transfer_queue = handle_from_jlong<VkQueue>(vk_transfer_queue);
    g_vk_transfer_queue_family = static_cast<uint32_t>(transfer_queue_family);

    if (!has_required_fidelityfx_vulkan_entrypoints()) {
        return -7;
    }

    if (!load_vulkan_surface_symbols()) {
        return -6;
    }

    if (!query_upscale_versions()) {
        g_initialized = false;
        g_upscaling_supported = false;
        g_frame_generation_ready = false;
        return -5;
    }

    g_initialized = true;
    g_upscaling_supported = true;
    g_frame_generation_provider_available =
            query_provider_available(FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION);
    g_frame_generation_swapchain_provider_available =
            query_provider_available(FFX_API_CREATE_CONTEXT_DESC_TYPE_FGSWAPCHAIN_VK);
    g_frame_generation_ready = false;
    return 0;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_isUpscalingSupportedNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_initialized && g_upscaling_supported ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_isFrameGenerationSupportedNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_initialized
            && g_frame_generation_provider_available
            && g_frame_generation_swapchain_provider_available
            ? JNI_TRUE
            : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_isFrameGenerationSwapchainActiveNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_initialized && g_frame_generation_ready ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_createFrameGenerationSwapchainNative(
        JNIEnv*,
        jclass,
        jlong vk_device,
        jlong create_info_address,
        jlong allocator_address,
        jlong out_swapchain_address) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) vk_device;
    (void) create_info_address;
    (void) allocator_address;
    (void) out_swapchain_address;
    return std::numeric_limits<jint>::min();
#else
    if (!g_initialized
            || create_info_address == 0
            || out_swapchain_address == 0
            || vk_device == 0) {
        return std::numeric_limits<jint>::min();
    }

    VkResult result = create_frame_generation_swapchain(
            handle_from_jlong<VkDevice>(vk_device),
            reinterpret_cast<const VkSwapchainCreateInfoKHR*>(static_cast<uintptr_t>(create_info_address)),
            reinterpret_cast<const VkAllocationCallbacks*>(static_cast<uintptr_t>(allocator_address)),
            reinterpret_cast<VkSwapchainKHR*>(static_cast<uintptr_t>(out_swapchain_address)));
    return result == VK_ERROR_FEATURE_NOT_PRESENT ? std::numeric_limits<jint>::min() : static_cast<jint>(result);
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_destroyFrameGenerationSwapchainNative(
        JNIEnv*,
        jclass,
        jlong vk_device,
        jlong swapchain,
        jlong allocator_address) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) vk_device;
    (void) swapchain;
    (void) allocator_address;
    return JNI_FALSE;
#else
    VkSwapchainKHR vk_swapchain = handle_from_jlong<VkSwapchainKHR>(swapchain);
    if (!owns_frame_generation_swapchain(vk_swapchain)
            || g_swapchain_replacement_functions.pOutDestroySwapchainFFXAPI == nullptr) {
        return JNI_FALSE;
    }

    destroy_frame_generation_context();
    g_swapchain_replacement_functions.pOutDestroySwapchainFFXAPI(
            handle_from_jlong<VkDevice>(vk_device),
            vk_swapchain,
            reinterpret_cast<const VkAllocationCallbacks*>(static_cast<uintptr_t>(allocator_address)),
            g_frame_generation_swapchain_context);
    g_frame_generation_swapchain = VK_NULL_HANDLE;
    return JNI_TRUE;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_getFrameGenerationSwapchainImagesNative(
        JNIEnv*,
        jclass,
        jlong vk_device,
        jlong swapchain,
        jlong image_count_address,
        jlong images_address) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) vk_device;
    (void) swapchain;
    (void) image_count_address;
    (void) images_address;
    return std::numeric_limits<jint>::min();
#else
    VkSwapchainKHR vk_swapchain = handle_from_jlong<VkSwapchainKHR>(swapchain);
    if (!owns_frame_generation_swapchain(vk_swapchain)
            || image_count_address == 0
            || g_swapchain_replacement_functions.pOutGetSwapchainImagesKHR == nullptr) {
        return std::numeric_limits<jint>::min();
    }

    return static_cast<jint>(g_swapchain_replacement_functions.pOutGetSwapchainImagesKHR(
            handle_from_jlong<VkDevice>(vk_device),
            vk_swapchain,
            reinterpret_cast<uint32_t*>(static_cast<uintptr_t>(image_count_address)),
            reinterpret_cast<VkImage*>(static_cast<uintptr_t>(images_address))));
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_acquireNextFrameGenerationImageNative(
        JNIEnv*,
        jclass,
        jlong vk_device,
        jlong swapchain,
        jlong timeout,
        jlong semaphore,
        jlong fence,
        jlong image_index_address) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) vk_device;
    (void) swapchain;
    (void) timeout;
    (void) semaphore;
    (void) fence;
    (void) image_index_address;
    return std::numeric_limits<jint>::min();
#else
    VkSwapchainKHR vk_swapchain = handle_from_jlong<VkSwapchainKHR>(swapchain);
    if (!owns_frame_generation_swapchain(vk_swapchain)
            || image_index_address == 0
            || g_swapchain_replacement_functions.pOutAcquireNextImageKHR == nullptr) {
        return std::numeric_limits<jint>::min();
    }

    return static_cast<jint>(g_swapchain_replacement_functions.pOutAcquireNextImageKHR(
            handle_from_jlong<VkDevice>(vk_device),
            vk_swapchain,
            static_cast<uint64_t>(timeout),
            handle_from_jlong<VkSemaphore>(semaphore),
            handle_from_jlong<VkFence>(fence),
            reinterpret_cast<uint32_t*>(static_cast<uintptr_t>(image_index_address))));
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_presentFrameGenerationSwapchainNative(
        JNIEnv*,
        jclass,
        jlong vk_queue,
        jlong present_info_address) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) vk_queue;
    (void) present_info_address;
    return std::numeric_limits<jint>::min();
#else
    if (present_info_address == 0 || g_swapchain_replacement_functions.pOutQueuePresentKHR == nullptr) {
        return std::numeric_limits<jint>::min();
    }

    const VkPresentInfoKHR* present_info =
            reinterpret_cast<const VkPresentInfoKHR*>(static_cast<uintptr_t>(present_info_address));
    if (present_info->swapchainCount != 1
            || present_info->pSwapchains == nullptr
            || !owns_frame_generation_swapchain(present_info->pSwapchains[0])) {
        return std::numeric_limits<jint>::min();
    }

    return static_cast<jint>(g_swapchain_replacement_functions.pOutQueuePresentKHR(
            handle_from_jlong<VkQueue>(vk_queue),
            present_info));
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_queryOptimalSettingsNative(
        JNIEnv* env,
        jclass,
        jint quality_preset,
        jint output_width,
        jint output_height,
        jintArray out_render_size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized || !g_upscaling_supported || output_width <= 0 || output_height <= 0) {
        return -1;
    }

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) env;
    (void) quality_preset;
    (void) out_render_size;
    return -100;
#else
    uint32_t render_width = 0;
    uint32_t render_height = 0;
    ffxQueryDescUpscaleGetRenderResolutionFromQualityMode query{};
    query.header.type = FFX_API_QUERY_DESC_TYPE_UPSCALE_GETRENDERRESOLUTIONFROMQUALITYMODE;
    query.displayWidth = static_cast<uint32_t>(output_width);
    query.displayHeight = static_cast<uint32_t>(output_height);
    query.qualityMode = quality_mode(quality_preset);
    query.pOutRenderWidth = &render_width;
    query.pOutRenderHeight = &render_height;
    ffxReturnCode_t result = g_ffx_query(nullptr, &query.header);
    if (result != FFX_API_RETURN_OK) {
        return static_cast<jint>(result);
    }

    int32_t jitter_phase_count = 0;
    ffxQueryDescUpscaleGetJitterPhaseCount jitter_query{};
    jitter_query.header.type = FFX_API_QUERY_DESC_TYPE_UPSCALE_GETJITTERPHASECOUNT;
    jitter_query.renderWidth = render_width;
    jitter_query.displayWidth = static_cast<uint32_t>(output_width);
    jitter_query.pOutPhaseCount = &jitter_phase_count;
    result = g_ffx_query(nullptr, &jitter_query.header);
    if (result != FFX_API_RETURN_OK) {
        return static_cast<jint>(result);
    }

    jint values[3] = {
            static_cast<jint>(std::max(1u, render_width)),
            static_cast<jint>(std::max(1u, render_height)),
            static_cast<jint>(std::max(1, jitter_phase_count))
    };
    env->SetIntArrayRegion(out_render_size, 0, 3, values);
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_evaluateNative(
        JNIEnv*,
        jclass,
        jlong command_buffer,
        jint fsr_version,
        jboolean frame_generation,
        jlong input_color_image,
        jlong input_color_view,
        jlong output_color_image,
        jlong output_color_view,
        jlong depth_image,
        jlong depth_view,
        jlong motion_vector_image,
        jlong motion_vector_view,
        jlong opaque_color_image,
        jlong opaque_color_view,
        jlong reactive_mask_image,
        jlong reactive_mask_view,
        jlong transparency_mask_image,
        jlong transparency_mask_view,
        jlong hudless_color_image,
        jlong hudless_color_view,
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
        jfloat sharpness,
        jfloat frame_time_delta_ms,
        jfloat camera_near,
        jfloat camera_far,
        jfloat camera_fov_y,
        jfloat view_space_to_meters,
        jfloat camera_position_x,
        jfloat camera_position_y,
        jfloat camera_position_z,
        jfloat camera_up_x,
        jfloat camera_up_y,
        jfloat camera_up_z,
        jfloat camera_right_x,
        jfloat camera_right_y,
        jfloat camera_right_z,
        jfloat camera_forward_x,
        jfloat camera_forward_y,
        jfloat camera_forward_z) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized || !g_upscaling_supported) {
        return -1;
    }

#if !defined(SALTS_FSR_WITH_FFX_SDK)
    (void) command_buffer;
    (void) fsr_version;
    (void) frame_generation;
    (void) input_color_image;
    (void) input_color_view;
    (void) output_color_image;
    (void) output_color_view;
    (void) depth_image;
    (void) depth_view;
    (void) motion_vector_image;
    (void) motion_vector_view;
    (void) opaque_color_image;
    (void) opaque_color_view;
    (void) reactive_mask_image;
    (void) reactive_mask_view;
    (void) transparency_mask_image;
    (void) transparency_mask_view;
    (void) hudless_color_image;
    (void) hudless_color_view;
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
    (void) sharpness;
    (void) frame_time_delta_ms;
    (void) camera_near;
    (void) camera_far;
    (void) camera_fov_y;
    (void) view_space_to_meters;
    (void) camera_position_x;
    (void) camera_position_y;
    (void) camera_position_z;
    (void) camera_up_x;
    (void) camera_up_y;
    (void) camera_up_z;
    (void) camera_right_x;
    (void) camera_right_y;
    (void) camera_right_z;
    (void) camera_forward_x;
    (void) camera_forward_y;
    (void) camera_forward_z;
    return -100;
#else
    (void) input_color_view;
    (void) output_color_view;
    (void) depth_view;
    (void) motion_vector_view;
    (void) opaque_color_view;
    (void) reactive_mask_view;
    (void) transparency_mask_view;
    (void) hudless_color_view;
    (void) frame_index;
    if (frame_generation == JNI_TRUE && !g_frame_generation_ready) {
        return -20;
    }
    if (command_buffer == 0
            || input_color_image == 0
            || output_color_image == 0
            || depth_image == 0
            || motion_vector_image == 0
            || render_width <= 0
            || render_height <= 0
            || output_width <= 0
            || output_height <= 0) {
        return -2;
    }

    uint32_t render_w = static_cast<uint32_t>(render_width);
    uint32_t render_h = static_cast<uint32_t>(render_height);
    uint32_t output_w = static_cast<uint32_t>(output_width);
    uint32_t output_h = static_cast<uint32_t>(output_height);
    int requested_fsr_version = fsr_version == 2 ? 2 : 3;
    ffxReturnCode_t result = ensure_upscale_context(requested_fsr_version, render_w, render_h, output_w, output_h);
    if (result != FFX_API_RETURN_OK) {
        return static_cast<jint>(result);
    }

    void* command_list = reinterpret_cast<void*>(static_cast<uintptr_t>(command_buffer));
    result = generate_reactive_mask(command_list, opaque_color_image, input_color_image, reactive_mask_image, render_w, render_h);
    if (result != FFX_API_RETURN_OK) {
        return static_cast<jint>(result);
    }

    ffxDispatchDescUpscale desc{};
    desc.header.type = FFX_API_DISPATCH_DESC_TYPE_UPSCALE;
    desc.commandList = command_list;
    desc.color = color_input(input_color_image, render_w, render_h);
    desc.depth = depth_input(depth_image, render_w, render_h);
    desc.motionVectors = motion_input(motion_vector_image, render_w, render_h);
    desc.reactive = mask_input(reactive_mask_image, render_w, render_h);
    desc.transparencyAndComposition = mask_input(transparency_mask_image, render_w, render_h);
    desc.output = output_color(output_color_image, output_w, output_h);
    desc.jitterOffset = {jitter_x, jitter_y};
    desc.motionVectorScale = {motion_vector_scale_x, motion_vector_scale_y};
    desc.renderSize = {render_w, render_h};
    desc.upscaleSize = {output_w, output_h};
    desc.enableSharpening = sharpness > 0.0f;
    desc.sharpness = std::clamp(static_cast<float>(sharpness), 0.0f, 1.0f);
    desc.frameTimeDelta = frame_time_delta_ms > 0.0f ? frame_time_delta_ms : 16.6667f;
    desc.preExposure = 1.0f;
    desc.reset = reset_history == JNI_TRUE;
    // FidelityFX expects the projection-order plane values when reverse-Z is enabled.
    desc.cameraNear = camera_far;
    desc.cameraFar = camera_near;
    desc.cameraFovAngleVertical = camera_fov_y;
    desc.viewSpaceToMetersFactor = view_space_to_meters <= 0.0f ? 1.0f : view_space_to_meters;
    desc.flags = 0;

    result = g_ffx_dispatch(&g_upscale_context, &desc.header);
    if (result != FFX_API_RETURN_OK) {
        return static_cast<jint>(result);
    }

    if (frame_generation == JNI_TRUE) {
        if (!g_frame_generation_ready
                || g_frame_generation_context == nullptr
                || g_frame_generation_swapchain_context == nullptr
                || g_frame_generation_swapchain == VK_NULL_HANDLE) {
            return -20;
        }

        // A deliberate ID gap is the SDK 1.1.4 reset signal; unused_reset is not consumed.
        uint64_t frame_generation_id = g_frame_generation_frame_id_initialized
                ? g_frame_generation_frame_id + (reset_history == JNI_TRUE ? 2u : 1u)
                : 0u;

        ffxConfigureDescFrameGeneration config{};
        config.header.type = FFX_API_CONFIGURE_DESC_TYPE_FRAMEGENERATION;
        config.swapChain = reinterpret_cast<void*>(g_frame_generation_swapchain);
        config.frameGenerationCallback = frame_generation_dispatch_callback;
        config.frameGenerationCallbackUserContext = &g_frame_generation_context;
        config.frameGenerationEnabled = true;
        config.allowAsyncWorkloads = false;
        config.HUDLessColor = {};
        if (hudless_color_image != 0) {
            config.HUDLessColor = hudless_color(hudless_color_image, output_w, output_h);
        }
        config.flags = 0;
        config.onlyPresentGenerated = false;
        config.generationRect = {
                0,
                0,
                static_cast<int32_t>(output_w),
                static_cast<int32_t>(output_h)
        };
        config.frameID = frame_generation_id;

        result = g_ffx_configure(&g_frame_generation_context, &config.header);
        if (result != FFX_API_RETURN_OK) {
            return static_cast<jint>(result);
        }

        ffxDispatchDescFrameGenerationPrepareCameraInfo camera_info{};
        camera_info.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION_PREPARE_CAMERAINFO;
        camera_info.cameraPosition[0] = camera_position_x;
        camera_info.cameraPosition[1] = camera_position_y;
        camera_info.cameraPosition[2] = camera_position_z;
        camera_info.cameraUp[0] = camera_up_x;
        camera_info.cameraUp[1] = camera_up_y;
        camera_info.cameraUp[2] = camera_up_z;
        camera_info.cameraRight[0] = camera_right_x;
        camera_info.cameraRight[1] = camera_right_y;
        camera_info.cameraRight[2] = camera_right_z;
        camera_info.cameraForward[0] = camera_forward_x;
        camera_info.cameraForward[1] = camera_forward_y;
        camera_info.cameraForward[2] = camera_forward_z;

        ffxDispatchDescFrameGenerationPrepare prepare{};
        prepare.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION_PREPARE;
        prepare.header.pNext = &camera_info.header;
        prepare.frameID = frame_generation_id;
        prepare.flags = 0;
        prepare.commandList = command_list;
        prepare.renderSize = {render_w, render_h};
        prepare.jitterOffset = {jitter_x, jitter_y};
        prepare.motionVectorScale = {motion_vector_scale_x, motion_vector_scale_y};
        prepare.frameTimeDelta = desc.frameTimeDelta;
        prepare.unused_reset = reset_history == JNI_TRUE;
        prepare.cameraNear = camera_far;
        prepare.cameraFar = camera_near;
        prepare.cameraFovAngleVertical = camera_fov_y;
        prepare.viewSpaceToMetersFactor = view_space_to_meters <= 0.0f ? 1.0f : view_space_to_meters;
        prepare.depth = depth_input(depth_image, render_w, render_h);
        prepare.motionVectors = motion_input(motion_vector_image, render_w, render_h);

        result = g_ffx_dispatch(&g_frame_generation_context, &prepare.header);
        if (result != FFX_API_RETURN_OK) {
            return static_cast<jint>(result);
        }

        ffxConfigureDescFrameGenerationSwapChainRegisterUiResourceVK ui_config{};
        ui_config.header.type = FFX_API_CONFIGURE_DESC_TYPE_FGSWAPCHAIN_REGISTERUIRESOURCE_VK;
        ui_config.uiResource = {};
        ui_config.flags = 0;
        result = g_ffx_configure(&g_frame_generation_swapchain_context, &ui_config.header);
        if (result != FFX_API_RETURN_OK) {
            return static_cast<jint>(result);
        }

        g_frame_generation_frame_id = frame_generation_id;
        g_frame_generation_frame_id_initialized = true;
    }

    return 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_fsr_FsrNativeBridge_shutdownNative(
        JNIEnv*,
        jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);

#if defined(SALTS_FSR_WITH_FFX_SDK)
    destroy_frame_generation_swapchain_context();
    destroy_upscale_context();
    unload_library(g_ffx_module);
    unload_library(g_vulkan_module);
    g_ffx_module = nullptr;
    g_vulkan_module = nullptr;
    g_ffx_create_context = nullptr;
    g_ffx_destroy_context = nullptr;
    g_ffx_configure = nullptr;
    g_ffx_query = nullptr;
    g_ffx_dispatch = nullptr;
    g_vk_get_instance_proc_addr = nullptr;
    g_vk_get_device_proc_addr = nullptr;
    g_vk_get_physical_device_surface_support_khr = nullptr;
    g_vk_instance = VK_NULL_HANDLE;
    g_vk_physical_device = VK_NULL_HANDLE;
    g_vk_device = VK_NULL_HANDLE;
    g_vk_graphics_queue = VK_NULL_HANDLE;
    g_vk_graphics_queue_family = 0;
    g_vk_compute_queue = VK_NULL_HANDLE;
    g_vk_compute_queue_family = 0;
    g_vk_transfer_queue = VK_NULL_HANDLE;
    g_vk_transfer_queue_family = 0;
    g_fsr2_version_id = 0;
    g_fsr3_version_id = 0;
    g_frame_generation_provider_available = false;
    g_frame_generation_swapchain_provider_available = false;
#endif

    g_initialized = false;
    g_upscaling_supported = false;
    g_frame_generation_ready = false;
}
