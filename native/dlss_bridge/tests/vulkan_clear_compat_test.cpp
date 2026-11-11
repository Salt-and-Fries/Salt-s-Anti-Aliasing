#include "vulkan_clear_compat.h"
#include <vulkan/vulkan.h>
#include <vulkan/vk_layer.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>

extern "C" PFN_vkVoidFunction VKAPI_CALL saltsLayerGetInstanceProcAddr(VkInstance, const char*);
extern "C" PFN_vkVoidFunction VKAPI_CALL saltsLayerGetDeviceProcAddr(VkDevice, const char*);
extern "C" VkResult VKAPI_CALL saltsLayerNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface*);

namespace {
void require(bool condition, const char* message) {
    if (!condition) { std::fprintf(stderr, "%s\n", message); std::exit(1); }
}
int instance_table, device_table;
struct Handle { void* dispatch; } instance_handle{&instance_table}, physical_handle{&instance_table},
        device_handle{&device_table}, command_handle{&device_table};
VkInstance instance = reinterpret_cast<VkInstance>(&instance_handle);
VkPhysicalDevice physical = reinterpret_cast<VkPhysicalDevice>(&physical_handle);
VkDevice device = reinterpret_cast<VkDevice>(&device_handle);
VkCommandBuffer command = reinterpret_cast<VkCommandBuffer>(&command_handle);
thread_local std::vector<char> events;
thread_local VkPipelineStageFlags recorded_destination;
thread_local std::vector<VkImageMemoryBarrier> recorded_images;
VkImage test_image = reinterpret_cast<VkImage>(123);
VkClearColorValue test_color{};
VkImageSubresourceRange test_range{VK_IMAGE_ASPECT_COLOR_BIT, 2, 3, 4, 5};
unsigned destroyed_instances{}, destroyed_devices{};

VKAPI_ATTR VkResult VKAPI_CALL create_instance(const VkInstanceCreateInfo* info,
        const VkAllocationCallbacks*, VkInstance* output) {
    auto* chain = reinterpret_cast<const VkLayerInstanceCreateInfo*>(info->pNext);
    require(chain->u.pLayerInfo == nullptr, "Instance link was not advanced");
    *output = instance; return VK_SUCCESS;
}
VKAPI_ATTR VkResult VKAPI_CALL create_device(VkPhysicalDevice input, const VkDeviceCreateInfo* info,
        const VkAllocationCallbacks*, VkDevice* output) {
    require(input == physical, "Physical device was changed");
    auto* chain = reinterpret_cast<const VkLayerDeviceCreateInfo*>(info->pNext);
    require(chain->u.pLayerInfo == nullptr, "Device link was not advanced");
    *output = device; return VK_SUCCESS;
}
VKAPI_ATTR void VKAPI_CALL destroy_instance(VkInstance input, const VkAllocationCallbacks*) {
    require(input == instance, "Wrong instance destroyed"); ++destroyed_instances;
}
VKAPI_ATTR void VKAPI_CALL destroy_device(VkDevice input, const VkAllocationCallbacks*) {
    require(input == device, "Wrong device destroyed"); ++destroyed_devices;
}
VKAPI_ATTR void VKAPI_CALL clear(VkCommandBuffer input, VkImage image, VkImageLayout layout,
        const VkClearColorValue* color, uint32_t count, const VkImageSubresourceRange* ranges) {
    require(input == command && image == test_image && layout == VK_IMAGE_LAYOUT_GENERAL,
            "Clear handles or layout changed");
    require(color == &test_color && count == 1 && ranges == &test_range, "Clear data changed");
    events.push_back('C');
}
VKAPI_ATTR void VKAPI_CALL barrier(VkCommandBuffer input, VkPipelineStageFlags source,
        VkPipelineStageFlags destination, VkDependencyFlags flags, uint32_t memory_count,
        const VkMemoryBarrier* memory, uint32_t buffers, const VkBufferMemoryBarrier*,
        uint32_t images, const VkImageMemoryBarrier* image_barriers) {
    require(input == command && source == VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT && !flags,
            "Source stages or flags changed");
    require(!memory_count && !memory && !buffers && images == 2, "Unrelated barriers changed");
    recorded_destination=destination;
    recorded_images.assign(image_barriers,image_barriers+images);
}
VKAPI_ATTR VkResult VKAPI_CALL wait_idle(VkDevice) { return VK_SUCCESS; }
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL next_instance_proc(VkInstance input, const char* name) {
    if (!std::strcmp(name,"vkCreateInstance")) {
        require(!input,"vkCreateInstance lookup requires null instance");
        return reinterpret_cast<PFN_vkVoidFunction>(create_instance);
    }
    require(input == instance, "A physical device was incorrectly passed as a VkInstance");
    if (!std::strcmp(name,"vkCreateDevice")) return reinterpret_cast<PFN_vkVoidFunction>(create_device);
    if (!std::strcmp(name,"vkDestroyInstance")) return reinterpret_cast<PFN_vkVoidFunction>(destroy_instance);
    return nullptr;
}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL next_device_proc(VkDevice input, const char* name) {
    require(input == device, "Wrong device used for dispatch lookup");
    if (!std::strcmp(name,"vkDestroyDevice")) return reinterpret_cast<PFN_vkVoidFunction>(destroy_device);
    if (!std::strcmp(name,"vkCmdClearColorImage")) return reinterpret_cast<PFN_vkVoidFunction>(clear);
    if (!std::strcmp(name,"vkCmdPipelineBarrier")) return reinterpret_cast<PFN_vkVoidFunction>(barrier);
    if (!std::strcmp(name,"vkDeviceWaitIdle")) return reinterpret_cast<PFN_vkVoidFunction>(wait_idle);
    return nullptr;
}
}

int main() {
    VkNegotiateLayerInterface negotiation{};
    negotiation.loaderLayerInterfaceVersion = 2;
    require(saltsLayerNegotiateLoaderLayerInterfaceVersion(&negotiation) == VK_SUCCESS &&
            negotiation.pfnGetInstanceProcAddr && negotiation.pfnGetDeviceProcAddr &&
            negotiation.pfnGetPhysicalDeviceProcAddr, "Loader interface negotiation failed");
    VkLayerInstanceLink instance_link{nullptr,next_instance_proc,nullptr};
    VkLayerInstanceCreateInfo instance_chain{};
    instance_chain.sType=VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO;
    instance_chain.function=VK_LAYER_LINK_INFO; instance_chain.u.pLayerInfo=&instance_link;
    VkInstanceCreateInfo instance_info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};instance_info.pNext=&instance_chain;
    auto createInstance=reinterpret_cast<PFN_vkCreateInstance>(saltsLayerGetInstanceProcAddr(nullptr,"vkCreateInstance"));
    VkInstance created{}; require(createInstance(&instance_info,nullptr,&created)==VK_SUCCESS,"Instance creation failed");
    VkLayerDeviceLink device_link{nullptr,next_instance_proc,next_device_proc};
    VkLayerDeviceCreateInfo device_chain{};
    device_chain.sType=VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO;
    device_chain.function=VK_LAYER_LINK_INFO;device_chain.u.pLayerInfo=&device_link;
    VkDeviceCreateInfo device_info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};device_info.pNext=&device_chain;
    auto createDevice=reinterpret_cast<PFN_vkCreateDevice>(saltsLayerGetInstanceProcAddr(created,"vkCreateDevice"));
    VkDevice created_device{};require(createDevice(physical,&device_info,nullptr,&created_device)==VK_SUCCESS,"Device creation failed");
    require(salts::dlss_clear::device_count()==1,"Device was not registered");
    require(saltsLayerGetDeviceProcAddr(device,"vkDeviceWaitIdle")==reinterpret_cast<PFN_vkVoidFunction>(wait_idle),
            "An unrelated device command was not forwarded");
    require(saltsLayerGetDeviceProcAddr(device,"vkCmdClearColorImage")==reinterpret_cast<PFN_vkVoidFunction>(clear),
            "Clears should be forwarded directly, including K/Auto's steady-state clears");
    VkImageMemoryBarrier image_barriers[2]{};
    for(auto& image:image_barriers) {
        image.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        image.srcAccessMask=0;image.dstAccessMask=VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT;
        image.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED;image.newLayout=VK_IMAGE_LAYOUT_GENERAL;
        image.srcQueueFamilyIndex=image.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        image.image=test_image;image.subresourceRange=test_range;
    }
    image_barriers[1].oldLayout=VK_IMAGE_LAYOUT_GENERAL;
    auto pipelineBarrier=reinterpret_cast<PFN_vkCmdPipelineBarrier>(saltsLayerGetDeviceProcAddr(device,"vkCmdPipelineBarrier"));
    auto invoke=[&](bool corrected, VkPipelineStageFlags stages=VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT) {
        pipelineBarrier(command,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,stages,0,0,nullptr,0,nullptr,2,image_barriers);
        require(recorded_destination==(stages|(corrected?VK_PIPELINE_STAGE_TRANSFER_BIT:0)),"Incorrect destination stage repair");
        for(unsigned i=0;i<2;++i) {
            const auto& original=image_barriers[i];const auto& recorded=recorded_images[i];
            require(recorded.dstAccessMask==(original.dstAccessMask|((corrected&&i==0)?VK_ACCESS_TRANSFER_WRITE_BIT:0)),
                    "Incorrect destination access repair");
            require(recorded.image==original.image&&recorded.oldLayout==original.oldLayout&&recorded.newLayout==original.newLayout&&
                    recorded.srcAccessMask==original.srcAccessMask&&recorded.srcQueueFamilyIndex==original.srcQueueFamilyIndex&&
                    recorded.dstQueueFamilyIndex==original.dstQueueFamilyIndex&&
                    !std::memcmp(&recorded.subresourceRange,&original.subresourceRange,sizeof(test_range)),
                    "Image identity, source scope, layouts, ownership or subresources changed");
        }
    };
    invoke(false);
    {
        salts::dlss_clear::EvaluationScope scope;
        invoke(true);
        {salts::dlss_clear::EvaluationScope nested;invoke(true);} invoke(true);
        std::thread other([&]{invoke(false);});other.join();
        image_barriers[0].dstAccessMask|=VK_ACCESS_TRANSFER_WRITE_BIT;
        invoke(false,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT);
        image_barriers[0].dstAccessMask&=~VK_ACCESS_TRANSFER_WRITE_BIT;
        require(!(image_barriers[0].dstAccessMask&VK_ACCESS_TRANSFER_WRITE_BIT),"Caller-owned barrier array was mutated");
    }
    invoke(false);
    require(salts::dlss_clear::barrier_count()==3,"Unexpected number of corrected transitions");
    reinterpret_cast<PFN_vkDestroyDevice>(saltsLayerGetDeviceProcAddr(device,"vkDestroyDevice"))(device,nullptr);
    reinterpret_cast<PFN_vkDestroyInstance>(saltsLayerGetInstanceProcAddr(instance,"vkDestroyInstance"))(instance,nullptr);
    require(destroyed_devices==1&&destroyed_instances==1,"Native destruction was not forwarded");
    std::puts("Clear compatibility dispatch, dependency, scope, thread isolation and lifetime checks passed");
}
