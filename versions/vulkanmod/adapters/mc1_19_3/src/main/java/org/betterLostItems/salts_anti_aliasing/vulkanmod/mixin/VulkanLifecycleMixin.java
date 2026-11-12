package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Vulkan.class, remap = false)
abstract class VulkanLifecycleMixin {
    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void salts$close(CallbackInfo ci) { org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(Vulkan.getDevice()); SaltsVulkanModClient.close(); }
    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkDestroyDevice(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private static void salts$sdk(CallbackInfo ci) {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes.close();
        long swapchain=Vulkan.getSwapChain();
        if(swapchain!=0){
            SwapchainBridge.destroy(Vulkan.getDevice(),swapchain,null);
            PresentationReadySemaphores.destroyed(Vulkan.getDevice(),swapchain);
        }
        NativeVulkanSetup.shutdown();
        org.lwjgl.util.vma.Vma.vmaDestroyAllocator(Vulkan.getAllocator());
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="cleanUp",at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyInstance(Lorg/lwjgl/vulkan/VkInstance;Lorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private static void salts$instanceObjects(org.lwjgl.vulkan.VkInstance instance,org.lwjgl.vulkan.VkAllocationCallbacks allocator,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){
        long messenger=VulkanWindowAccessor.salts$debugMessenger();
        if(messenger!=0)org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance,messenger,null);
        long surface=VulkanWindowAccessor.salts$surface();
        if(surface!=0)org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(instance,surface,null);
        original.call(instance,allocator);
    }
}
