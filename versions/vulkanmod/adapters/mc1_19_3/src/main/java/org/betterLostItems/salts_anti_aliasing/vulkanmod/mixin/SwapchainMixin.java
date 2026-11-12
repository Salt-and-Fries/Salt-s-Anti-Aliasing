package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SwapchainBridge;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

@Mixin(value = Vulkan.class, remap = false)
abstract class SwapchainMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="recreateSwapChain",at=@At("RETURN"))
    private static void salts$newAttachments(org.spongepowered.asm.mixin.injection.callback.CallbackInfo info){
        // The SDK can reuse its proxy handle while replacing every image and its extent.
        org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderTargets.invalidate();
    }

    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private static int salts$create(VkDevice device, VkSwapchainCreateInfoKHR info, VkAllocationCallbacks allocator, LongBuffer result) {
        return SwapchainBridge.create(device, info, allocator, result);
    }
    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkGetSwapchainImagesKHR(Lorg/lwjgl/vulkan/VkDevice;JLjava/nio/IntBuffer;Ljava/nio/LongBuffer;)I"))
    private static int salts$images(VkDevice device, long swapchain, IntBuffer count, LongBuffer images) {
        return SwapchainBridge.images(device, swapchain, count, images);
    }
    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkDestroySwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private static void salts$destroy(VkDevice device, long swapchain, VkAllocationCallbacks allocator) {
        SwapchainBridge.destroy(device, swapchain, allocator);
        org.betterLostItems.salts_anti_aliasing.vulkanmod.PresentationReadySemaphores.destroyed(device, swapchain);
    }
    @org.spongepowered.asm.mixin.injection.ModifyArg(method="createDepthResources",at=@At(value="INVOKE",target="Lnet/vulkanmod/vulkan/memory/MemoryManager;createImage(IIIIIIILjava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;)V"),index=5)
    private static int salts$depthUsage(int usage) { return usage | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT; }
}
