package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;
/** Each target owns a subset of these object types; constructors and static caches count too. */
@Mixin(value={net.vulkanmod.vulkan.Vulkan.class,net.vulkanmod.vulkan.Drawer.class,net.vulkanmod.vulkan.shader.Pipeline.class,
    net.vulkanmod.vulkan.texture.VulkanImage.class,net.vulkanmod.vulkan.TransferQueue.class},remap=false)
abstract class LegacyObjectLifetimesMixin {
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateCommandPool(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkCommandPoolCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createCommandPool(VkDevice device,VkCommandPoolCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("CommandPool",handle,0,()->vkDestroyCommandPool(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyCommandPool(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyCommandPool(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("CommandPool",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createPipelines(VkDevice device,long cache,VkGraphicsPipelineCreateInfo.Buffer infos,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,cache,infos,allocator,handles);
        if(result==VK_SUCCESS)for(int i=0;i<infos.remaining();i++){long handle=handles.get(i);NativeResourceLifetimes.created("Pipeline",handle,1,()->vkDestroyPipeline(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyPipeline(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyPipeline(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("Pipeline",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateDescriptorPool(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorPoolCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createDescriptorPool(VkDevice device,VkDescriptorPoolCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("DescriptorPool",handle,2,()->vkDestroyDescriptorPool(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyDescriptorPool(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyDescriptorPool(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("DescriptorPool",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateFramebuffer(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkFramebufferCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createFramebuffer(VkDevice device,VkFramebufferCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("Framebuffer",handle,3,()->vkDestroyFramebuffer(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyFramebuffer(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyFramebuffer(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("Framebuffer",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreatePipelineLayout(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkPipelineLayoutCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createPipelineLayout(VkDevice device,VkPipelineLayoutCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("PipelineLayout",handle,4,()->vkDestroyPipelineLayout(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyPipelineLayout(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyPipelineLayout(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("PipelineLayout",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateDescriptorSetLayout(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createDescriptorSetLayout(VkDevice device,VkDescriptorSetLayoutCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("DescriptorSetLayout",handle,5,()->vkDestroyDescriptorSetLayout(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyDescriptorSetLayout(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyDescriptorSetLayout(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("DescriptorSetLayout",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateShaderModule(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkShaderModuleCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createShaderModule(VkDevice device,VkShaderModuleCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("ShaderModule",handle,6,()->vkDestroyShaderModule(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyShaderModule(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyShaderModule(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("ShaderModule",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateRenderPass(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkRenderPassCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createRenderPass(VkDevice device,VkRenderPassCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("RenderPass",handle,7,()->vkDestroyRenderPass(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyRenderPass(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyRenderPass(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("RenderPass",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateImageView(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkImageViewCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createImageView(VkDevice device,VkImageViewCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("ImageView",handle,8,()->vkDestroyImageView(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyImageView(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyImageView(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("ImageView",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateSampler(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSamplerCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createSampler(VkDevice device,VkSamplerCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("Sampler",handle,9,()->vkDestroySampler(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroySampler(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroySampler(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("Sampler",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreatePipelineCache(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkPipelineCacheCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createPipelineCache(VkDevice device,VkPipelineCacheCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("PipelineCache",handle,10,()->vkDestroyPipelineCache(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyPipelineCache(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyPipelineCache(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("PipelineCache",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateSemaphore(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSemaphoreCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createSemaphore(VkDevice device,VkSemaphoreCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("Semaphore",handle,11,()->vkDestroySemaphore(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroySemaphore(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroySemaphore(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("Semaphore",handle);original.call(device,handle,allocator);
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateFence(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkFenceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createFence(VkDevice device,VkFenceCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("Fence",handle,12,()->vkDestroyFence(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyFence(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyFence(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("Fence",handle);original.call(device,handle,allocator);
    }
}
