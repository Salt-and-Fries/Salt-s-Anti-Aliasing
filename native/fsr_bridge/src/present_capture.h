// Opt-in diagnostic capture at the FidelityFX presentation callback. No code on the
// normal presentation path allocates readbacks unless SALTS_FSR_CAPTURE_DIR is set before launch.
// The copy matches AMD's default callback when no separate UI resource is registered.

struct PresentCaptureSlot {
    VkBuffer buffer{};
    VkDeviceMemory memory{};
    uint32_t width{}, height{}, format{};
    uint64_t frame_id{}, time_ns{};
    bool generated{};
    bool reset{};
    std::string role;
};
std::mutex g_capture_mutex;
std::vector<PresentCaptureSlot> g_capture_slots;
std::map<uint64_t,bool> g_capture_dispatch_resets;
FfxApiResource g_capture_hudless_input{};
std::vector<PresentCaptureSlot> g_capture_inputs;
uint64_t g_capture_callback_count = 0;
bool g_capture_finished = false;

bool present_capture_enabled() {
    const char* directory = std::getenv("SALTS_FSR_CAPTURE_DIR");
    return directory != nullptr && directory[0] != '\0';
}
template<class T> T capture_proc(const char* name) {
    return reinterpret_cast<T>(g_vk_get_device_proc_addr(g_vk_device,name));
}
VkImageLayout capture_layout(uint32_t state) {
    if(state==FFX_API_RESOURCE_STATE_PRESENT)return VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    if(state==FFX_API_RESOURCE_STATE_COPY_SRC)return VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    if(state==FFX_API_RESOURCE_STATE_COPY_DEST)return VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    if(state==FFX_API_RESOURCE_STATE_RENDER_TARGET)return VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    if(state==FFX_API_RESOURCE_STATE_COMPUTE_READ || state==FFX_API_RESOURCE_STATE_PIXEL_READ
            || state==FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ)return VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    return VK_IMAGE_LAYOUT_GENERAL;
}
void capture_barrier(VkCommandBuffer command,VkImage image,VkImageLayout from,VkImageLayout to,
                     VkAccessFlags source,VkAccessFlags destination) {
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.oldLayout=from;barrier.newLayout=to;
    barrier.srcAccessMask=source;barrier.dstAccessMask=destination;
    barrier.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;barrier.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    barrier.image=image;barrier.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    capture_proc<PFN_vkCmdPipelineBarrier>("vkCmdPipelineBarrier")(command,
            VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,0,0,nullptr,0,nullptr,1,&barrier);
}
bool create_present_capture(PresentCaptureSlot& slot) {
    VkDeviceSize bytes=VkDeviceSize(slot.width)*slot.height*4;
    if(bytes==0 || bytes>64*1024*1024)return false;
    VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    info.size=bytes;info.usage=VK_BUFFER_USAGE_TRANSFER_DST_BIT;info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if(capture_proc<PFN_vkCreateBuffer>("vkCreateBuffer")(g_vk_device,&info,nullptr,&slot.buffer)!=VK_SUCCESS)return false;
    VkMemoryRequirements requirements{};
    capture_proc<PFN_vkGetBufferMemoryRequirements>("vkGetBufferMemoryRequirements")(g_vk_device,slot.buffer,&requirements);
    VkPhysicalDeviceMemoryProperties properties{};
    reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties>(g_vk_get_instance_proc_addr(
            g_vk_instance,"vkGetPhysicalDeviceMemoryProperties"))(g_vk_physical_device,&properties);
    uint32_t type=properties.memoryTypeCount;
    for(uint32_t i=0;i<properties.memoryTypeCount;++i) {
        auto required=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        if((requirements.memoryTypeBits&(1u<<i)) && (properties.memoryTypes[i].propertyFlags&required)==required){type=i;break;}
    }
    VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocation.allocationSize=requirements.size;allocation.memoryTypeIndex=type;
    if(type==properties.memoryTypeCount || capture_proc<PFN_vkAllocateMemory>("vkAllocateMemory")(
            g_vk_device,&allocation,nullptr,&slot.memory)!=VK_SUCCESS) {
        capture_proc<PFN_vkDestroyBuffer>("vkDestroyBuffer")(g_vk_device,slot.buffer,nullptr);return false;
    }
    if(capture_proc<PFN_vkBindBufferMemory>("vkBindBufferMemory")(g_vk_device,slot.buffer,slot.memory,0)!=VK_SUCCESS) {
        capture_proc<PFN_vkDestroyBuffer>("vkDestroyBuffer")(g_vk_device,slot.buffer,nullptr);
        capture_proc<PFN_vkFreeMemory>("vkFreeMemory")(g_vk_device,slot.memory,nullptr);return false;
    }
    return true;
}
void capture_generation_inputs(ffxDispatchDescFrameGeneration* params) {
    if(g_capture_finished || g_capture_callback_count<180 || !g_capture_inputs.empty())return;
    FfxApiResource inputs[2]={params->presentColor,g_capture_hudless_input};
    for(int i=0;i<2;++i) {
        auto& input=inputs[i];if(!input.resource)continue;
        PresentCaptureSlot slot{};slot.width=input.description.width;slot.height=input.description.height;
        slot.format=input.description.format;slot.frame_id=params->frameID;
        slot.role=i==0?"input-present":"input-hudless";
        if(!create_present_capture(slot))continue;
        VkCommandBuffer command=reinterpret_cast<VkCommandBuffer>(params->commandList);
        VkImage image=reinterpret_cast<VkImage>(input.resource);auto layout=capture_layout(input.state);
        capture_barrier(command,image,layout,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_ACCESS_MEMORY_WRITE_BIT|VK_ACCESS_MEMORY_READ_BIT,VK_ACCESS_TRANSFER_READ_BIT);
        VkBufferImageCopy copy{};copy.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        copy.imageExtent={slot.width,slot.height,1};
        capture_proc<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(command,image,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,slot.buffer,1,&copy);
        capture_barrier(command,image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,layout,
                VK_ACCESS_TRANSFER_READ_BIT,VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT);
        g_capture_inputs.push_back(slot);
    }
}
ffxReturnCode_t diagnostic_present_callback(ffxCallbackDescFrameGenerationPresent* params,void*) {
    if(!params || params->currentUI.resource)return FFX_API_RETURN_ERROR_PARAMETER;
    std::lock_guard<std::mutex> lock(g_capture_mutex);
    VkCommandBuffer command=reinterpret_cast<VkCommandBuffer>(params->commandList);
    VkImage source=reinterpret_cast<VkImage>(params->currentBackBuffer.resource);
    VkImage destination=reinterpret_cast<VkImage>(params->outputSwapChainBuffer.resource);
    auto source_layout=capture_layout(params->currentBackBuffer.state);
    auto destination_layout=capture_layout(params->outputSwapChainBuffer.state);
    capture_barrier(command,source,source_layout,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT,VK_ACCESS_TRANSFER_READ_BIT);
    capture_barrier(command,destination,destination_layout,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_ACCESS_MEMORY_READ_BIT,VK_ACCESS_TRANSFER_WRITE_BIT);
    VkImageCopy copy{};
    copy.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};copy.dstSubresource=copy.srcSubresource;
    copy.extent={params->currentBackBuffer.description.width,params->currentBackBuffer.description.height,1};
    capture_proc<PFN_vkCmdCopyImage>("vkCmdCopyImage")(command,source,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            destination,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&copy);
    ++g_capture_callback_count;
    if(!g_capture_finished && g_capture_callback_count>180 && g_capture_slots.size()<48
            && (g_capture_slots.size()+1)*VkDeviceSize(copy.extent.width)*copy.extent.height*4<=256*1024*1024) {
        PresentCaptureSlot slot{};slot.width=copy.extent.width;slot.height=copy.extent.height;
        slot.format=params->currentBackBuffer.description.format;slot.frame_id=params->frameID;
        slot.generated=params->isGeneratedFrame;
        slot.reset=g_capture_dispatch_resets[params->frameID];
        slot.time_ns=std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        if(create_present_capture(slot)) {
            VkBufferImageCopy readback{};readback.imageSubresource=copy.srcSubresource;readback.imageExtent=copy.extent;
            capture_proc<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(command,source,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,slot.buffer,1,&readback);
            g_capture_slots.push_back(slot);
        }
    }
    capture_barrier(command,source,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,source_layout,
            VK_ACCESS_TRANSFER_READ_BIT,VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT);
    capture_barrier(command,destination,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,destination_layout,
            VK_ACCESS_TRANSFER_WRITE_BIT,VK_ACCESS_MEMORY_READ_BIT);
    return FFX_API_RETURN_OK;
}
// Caller waits for the SDK's presentation work and device completion before mapping.
void flush_present_capture() {
    if(!present_capture_enabled() || g_vk_device==VK_NULL_HANDLE)return;
    if(g_capture_slots.empty() && g_capture_inputs.empty())return;
    if(g_capture_finished && g_capture_slots.empty())return;
    capture_proc<PFN_vkDeviceWaitIdle>("vkDeviceWaitIdle")(g_vk_device);
    std::lock_guard<std::mutex> lock(g_capture_mutex);
    std::filesystem::path directory=std::filesystem::u8path(std::getenv("SALTS_FSR_CAPTURE_DIR"));
    std::filesystem::create_directories(directory);
    std::ofstream metadata(directory/"present-frames.json");metadata<<"[\n";
    g_capture_slots.insert(g_capture_slots.end(),g_capture_inputs.begin(),g_capture_inputs.end());
    g_capture_inputs.clear();
    for(size_t i=0;i<g_capture_slots.size();++i) {
        auto& slot=g_capture_slots[i];void* pixels=nullptr;
        if(capture_proc<PFN_vkMapMemory>("vkMapMemory")(g_vk_device,slot.memory,0,VK_WHOLE_SIZE,0,&pixels)==VK_SUCCESS) {
            std::string filename=(slot.role.empty()?"present":slot.role)+"-"+std::to_string(i)+(slot.generated?"-generated.ppm":"-rendered.ppm");
            std::ofstream image(directory/filename,std::ios::binary);image<<"P6\n"<<slot.width<<" "<<slot.height<<"\n255\n";
            bool bgra=slot.format==FFX_API_SURFACE_FORMAT_B8G8R8A8_UNORM || slot.format==FFX_API_SURFACE_FORMAT_B8G8R8A8_SRGB;
            auto data=static_cast<const uint8_t*>(pixels);
            for(size_t pixel=0;pixel<size_t(slot.width)*slot.height;++pixel) {
                char rgb[3]={char(data[pixel*4+(bgra?2:0)]),char(data[pixel*4+1]),char(data[pixel*4+(bgra?0:2)])};
                image.write(rgb,3);
            }
            capture_proc<PFN_vkUnmapMemory>("vkUnmapMemory")(g_vk_device,slot.memory);
            metadata<<(i?",\n":"")<<"{\"file\":\""<<filename<<"\",\"frameId\":"<<slot.frame_id
                    <<",\"generated\":"<<(slot.generated?"true":"false")<<",\"format\":"<<slot.format<<",\"role\":\""<<slot.role<<"\",\"dispatchReset\":"<<(slot.reset?"true":"false")<<",\"callbackTimeNs\":"<<slot.time_ns<<"}";
        }
        capture_proc<PFN_vkDestroyBuffer>("vkDestroyBuffer")(g_vk_device,slot.buffer,nullptr);
        capture_proc<PFN_vkFreeMemory>("vkFreeMemory")(g_vk_device,slot.memory,nullptr);
    }
    metadata<<"\n]\n";g_capture_slots.clear();g_capture_finished=true;
}
