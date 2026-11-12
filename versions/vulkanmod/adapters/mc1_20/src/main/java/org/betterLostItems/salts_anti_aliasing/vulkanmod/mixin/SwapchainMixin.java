package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SwapchainBridge;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

@Mixin(value = SwapChain.class, remap = false)
abstract class SwapchainMixin {
    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private int salts$create(VkDevice device, VkSwapchainCreateInfoKHR info, VkAllocationCallbacks allocator, LongBuffer result) {
        return SwapchainBridge.create(device, info, allocator, result);
    }
    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkGetSwapchainImagesKHR(Lorg/lwjgl/vulkan/VkDevice;JLjava/nio/IntBuffer;Ljava/nio/LongBuffer;)I"))
    private int salts$images(VkDevice device, long swapchain, IntBuffer count, LongBuffer images) {
        return SwapchainBridge.images(device, swapchain, count, images);
    }
    @Redirect(method = {"createSwapChain", "cleanUp"}, at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkDestroySwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private void salts$destroy(VkDevice device, long swapchain, VkAllocationCallbacks allocator) {
        SwapchainBridge.destroy(device, swapchain, allocator);
        org.betterLostItems.salts_anti_aliasing.vulkanmod.PresentationReadySemaphores.destroyed(device, swapchain);
    }
}
