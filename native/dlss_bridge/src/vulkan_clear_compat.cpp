#include "vulkan_clear_compat.h"

#include <vulkan/vulkan.h>
#include <vulkan/vk_layer.h>
#include <windows.h>
#include <atomic>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace {
constexpr wchar_t layer_name[] = L"VK_LAYER_SALTS_dlss_clear_compat";
thread_local unsigned evaluation_depth = 0;
std::atomic<uint64_t> barriers{0};
std::atomic<uint64_t> devices_created{0};
std::mutex dispatch_mutex;

struct InstanceDispatch {
    VkInstance handle;
    PFN_vkGetInstanceProcAddr get_proc;
    PFN_GetPhysicalDeviceProcAddr get_physical_proc;
    PFN_vkDestroyInstance destroy;
};
struct DeviceDispatch {
    PFN_vkGetDeviceProcAddr get_proc;
    PFN_vkDestroyDevice destroy;
    PFN_vkCmdPipelineBarrier barrier;
};
std::unordered_map<void*, InstanceDispatch> instances;
std::unordered_map<void*, DeviceDispatch> devices;

template<class Handle> void* dispatch_key(Handle handle) {
    return handle ? *reinterpret_cast<void**>(handle) : nullptr;
}
InstanceDispatch instance_dispatch(VkInstance instance) {
    std::lock_guard lock(dispatch_mutex);
    auto entry = instances.find(dispatch_key(instance));
    return entry == instances.end() ? InstanceDispatch{} : entry->second;
}
DeviceDispatch device_dispatch(void* key) {
    std::lock_guard lock(dispatch_mutex);
    auto entry = devices.find(key);
    return entry == devices.end() ? DeviceDispatch{} : entry->second;
}
template<class Info> Info* link_info(const void* next, VkStructureType type) {
    for (auto* info = reinterpret_cast<Info*>(const_cast<void*>(next)); info;
         info = reinterpret_cast<Info*>(const_cast<void*>(info->pNext))) {
        if (info->sType == type && info->function == VK_LAYER_LINK_INFO) return info;
    }
    return nullptr;
}
std::wstring environment(const wchar_t* name) {
    DWORD count = GetEnvironmentVariableW(name, nullptr, 0);
    if (!count) return {};
    std::wstring value(count, L'\0');
    count = GetEnvironmentVariableW(name, value.data(), count);
    value.resize(count);
    return value;
}
}

extern "C" __declspec(dllexport) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
saltsLayerGetInstanceProcAddr(VkInstance instance, const char* name);
extern "C" __declspec(dllexport) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
saltsLayerGetDeviceProcAddr(VkDevice device, const char* name);

namespace {
VKAPI_ATTR VkResult VKAPI_CALL create_instance(const VkInstanceCreateInfo* info,
        const VkAllocationCallbacks* allocator, VkInstance* instance) {
    auto* link = link_info<VkLayerInstanceCreateInfo>(info->pNext, VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO);
    if (!link || !link->u.pLayerInfo) return VK_ERROR_INITIALIZATION_FAILED;
    auto get_proc = link->u.pLayerInfo->pfnNextGetInstanceProcAddr;
    auto get_physical = link->u.pLayerInfo->pfnNextGetPhysicalDeviceProcAddr;
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get_proc(VK_NULL_HANDLE, "vkCreateInstance"));
    if (!create) return VK_ERROR_INITIALIZATION_FAILED;
    link->u.pLayerInfo = link->u.pLayerInfo->pNext;
    VkResult result = create(info, allocator, instance);
    if (result == VK_SUCCESS) {
        std::lock_guard lock(dispatch_mutex);
        instances[dispatch_key(*instance)] = {*instance, get_proc, get_physical,
                reinterpret_cast<PFN_vkDestroyInstance>(get_proc(*instance, "vkDestroyInstance"))};
    }
    return result;
}
VKAPI_ATTR void VKAPI_CALL destroy_instance(VkInstance instance, const VkAllocationCallbacks* allocator) {
    if (!instance) return;
    auto dispatch = instance_dispatch(instance);
    { std::lock_guard lock(dispatch_mutex); instances.erase(dispatch_key(instance)); }
    if (dispatch.destroy) dispatch.destroy(instance, allocator);
}
VKAPI_ATTR VkResult VKAPI_CALL create_device(VkPhysicalDevice physical, const VkDeviceCreateInfo* info,
        const VkAllocationCallbacks* allocator, VkDevice* device) {
    auto* link = link_info<VkLayerDeviceCreateInfo>(info->pNext, VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO);
    if (!link || !link->u.pLayerInfo) return VK_ERROR_INITIALIZATION_FAILED;
    auto get_instance_proc = link->u.pLayerInfo->pfnNextGetInstanceProcAddr;
    auto get_device_proc = link->u.pLayerInfo->pfnNextGetDeviceProcAddr;
    // The physical device shares its instance's dispatch key. The next GIPA accepts
    // the instance here; never ask it for vkCreateDevice using a null instance.
    InstanceDispatch instance{};
    {
        std::lock_guard lock(dispatch_mutex);
        auto entry = instances.find(dispatch_key(physical));
        if (entry == instances.end()) return VK_ERROR_INITIALIZATION_FAILED;
        instance = entry->second;
    }
    auto create = reinterpret_cast<PFN_vkCreateDevice>(get_instance_proc(instance.handle, "vkCreateDevice"));
    if (!create) return VK_ERROR_INITIALIZATION_FAILED;
    link->u.pLayerInfo = link->u.pLayerInfo->pNext;
    VkResult result = create(physical, info, allocator, device);
    if (result == VK_SUCCESS) {
        DeviceDispatch dispatch{get_device_proc,
                reinterpret_cast<PFN_vkDestroyDevice>(get_device_proc(*device, "vkDestroyDevice")),
                reinterpret_cast<PFN_vkCmdPipelineBarrier>(get_device_proc(*device, "vkCmdPipelineBarrier"))};
        { std::lock_guard lock(dispatch_mutex); devices[dispatch_key(*device)] = dispatch; }
        ++devices_created;
    }
    return result;
}
VKAPI_ATTR void VKAPI_CALL destroy_device(VkDevice device, const VkAllocationCallbacks* allocator) {
    if (!device) return;
    auto dispatch = device_dispatch(dispatch_key(device));
    { std::lock_guard lock(dispatch_mutex); devices.erase(dispatch_key(device)); }
    if (dispatch.destroy) dispatch.destroy(device, allocator);
}
VKAPI_ATTR void VKAPI_CALL pipeline_barrier(VkCommandBuffer command, VkPipelineStageFlags source_stages,
        VkPipelineStageFlags destination_stages, VkDependencyFlags flags, uint32_t memory_count,
        const VkMemoryBarrier* memory, uint32_t buffer_count, const VkBufferMemoryBarrier* buffers,
        uint32_t image_count, const VkImageMemoryBarrier* images) {
    auto dispatch = device_dispatch(dispatch_key(command));
    if (!dispatch.barrier) return;
    std::vector<VkImageMemoryBarrier> corrected;
    if (evaluation_depth) {
        bool transfer_stage = (destination_stages & (VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)) != 0;
        for (uint32_t i = 0; i < image_count; ++i) {
            const auto& image = images[i];
            bool shader_access = (image.dstAccessMask & (VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)) != 0;
            bool transfer_write = (image.dstAccessMask & (VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_MEMORY_WRITE_BIT)) != 0;
            if (image.oldLayout != VK_IMAGE_LAYOUT_UNDEFINED || image.newLayout != VK_IMAGE_LAYOUT_GENERAL
                    || !shader_access || (transfer_stage && transfer_write)) continue;
            // The two supported runtimes initialize M/L private images for shader accesses,
            // then clear them. Repair that INITIAL transition's destination scope directly.
            // Do not add per-frame barriers to K/Auto's already synchronized histogram clears.
            if (corrected.empty()) corrected.assign(images, images + image_count);
            corrected[i].dstAccessMask |= VK_ACCESS_TRANSFER_WRITE_BIT;
            ++barriers;
        }
    }
    if (!corrected.empty()) destination_stages |= VK_PIPELINE_STAGE_TRANSFER_BIT;
    dispatch.barrier(command, source_stages, destination_stages, flags, memory_count, memory,
            buffer_count, buffers, image_count, corrected.empty() ? images : corrected.data());
}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL get_physical_proc(VkInstance instance, const char* name) {
    auto dispatch = instance_dispatch(instance);
    return dispatch.get_physical_proc ? dispatch.get_physical_proc(instance, name)
            : dispatch.get_proc ? dispatch.get_proc(instance, name) : nullptr;
}
PFN_vkVoidFunction intercept(const char* name) {
    if (!name) return nullptr;
    if (!std::strcmp(name, "vkGetInstanceProcAddr")) return reinterpret_cast<PFN_vkVoidFunction>(saltsLayerGetInstanceProcAddr);
    if (!std::strcmp(name, "vkGetDeviceProcAddr")) return reinterpret_cast<PFN_vkVoidFunction>(saltsLayerGetDeviceProcAddr);
    if (!std::strcmp(name, "vkCreateInstance")) return reinterpret_cast<PFN_vkVoidFunction>(create_instance);
    if (!std::strcmp(name, "vkDestroyInstance")) return reinterpret_cast<PFN_vkVoidFunction>(destroy_instance);
    if (!std::strcmp(name, "vkCreateDevice")) return reinterpret_cast<PFN_vkVoidFunction>(create_device);
    if (!std::strcmp(name, "vkDestroyDevice")) return reinterpret_cast<PFN_vkVoidFunction>(destroy_device);
    if (!std::strcmp(name, "vkCmdPipelineBarrier")) return reinterpret_cast<PFN_vkVoidFunction>(pipeline_barrier);
    return nullptr;
}
}

extern "C" __declspec(dllexport) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
saltsLayerGetInstanceProcAddr(VkInstance instance, const char* name) {
    if (auto function = intercept(name)) return function;
    auto dispatch = instance_dispatch(instance);
    return dispatch.get_proc ? dispatch.get_proc(instance, name) : nullptr;
}
extern "C" __declspec(dllexport) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
saltsLayerGetDeviceProcAddr(VkDevice device, const char* name) {
    if (!name) return nullptr;
    if (!std::strcmp(name, "vkGetDeviceProcAddr")) return reinterpret_cast<PFN_vkVoidFunction>(saltsLayerGetDeviceProcAddr);
    if (!std::strcmp(name, "vkDestroyDevice")) return reinterpret_cast<PFN_vkVoidFunction>(destroy_device);
    if (!std::strcmp(name, "vkCmdPipelineBarrier")) return reinterpret_cast<PFN_vkVoidFunction>(pipeline_barrier);
    auto dispatch = device_dispatch(dispatch_key(device));
    return dispatch.get_proc ? dispatch.get_proc(device, name) : nullptr;
}
extern "C" __declspec(dllexport) VKAPI_ATTR VkResult VKAPI_CALL
saltsLayerNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface* version) {
    if (!version || version->loaderLayerInterfaceVersion < 2) return VK_ERROR_INITIALIZATION_FAILED;
    version->loaderLayerInterfaceVersion = 2;
    version->pfnGetInstanceProcAddr = saltsLayerGetInstanceProcAddr;
    version->pfnGetDeviceProcAddr = saltsLayerGetDeviceProcAddr;
    version->pfnGetPhysicalDeviceProcAddr = get_physical_proc;
    return VK_SUCCESS;
}

namespace salts::dlss_clear {
EvaluationScope::EvaluationScope() { ++evaluation_depth; }
EvaluationScope::~EvaluationScope() { --evaluation_depth; }
uint64_t barrier_count() { return barriers.load(); }
uint64_t device_count() { return devices_created.load(); }
bool enable_layer(const std::wstring& directory) {
    // These changes affect this game process only. Preserve existing layer search paths
    // and validation/overlay layers, placing us before validation so it sees the repair.
    const wchar_t* path_name = environment(L"VK_LAYER_PATH").empty() ? L"VK_ADD_LAYER_PATH" : L"VK_LAYER_PATH";
    auto path = environment(path_name);
    path = directory + (path.empty() ? L"" : L";" + path);
    auto layers = environment(L"VK_INSTANCE_LAYERS");
    if (layers.find(layer_name) == std::wstring::npos)
        layers = std::wstring(layer_name) + (layers.empty() ? L"" : L";" + layers);
    return SetEnvironmentVariableW(path_name, path.c_str()) &&
            SetEnvironmentVariableW(L"VK_INSTANCE_LAYERS", layers.c_str());
}
}
