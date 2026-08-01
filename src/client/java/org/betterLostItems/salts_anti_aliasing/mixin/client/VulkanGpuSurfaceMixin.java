package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FrameGenerationSwapchainImagePolicy;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Routes Minecraft's Vulkan swapchain calls through the FidelityFX replacement functions when
 * the native FSR3 frame-generation swapchain owns the handle.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanGpuSurface")
public abstract class VulkanGpuSurfaceMixin {
    @ModifyArg(
            method = "blitFromTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkImageMemoryBarrier2$Buffer;newLayout(I)Lorg/lwjgl/vulkan/VkImageMemoryBarrier2$Buffer;",
                    ordinal = 1
            )
    )
    private int saltsAntiAliasing$useFrameGenerationPresentLayout(int regularLayout) {
        return FrameGenerationSwapchainImagePolicy.finalLayout(
                FsrRuntime.instance().isFrameGenerationSwapchainOwned(),
                regularLayout
        );
    }

    @ModifyArg(
            method = "blitFromTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkImageMemoryBarrier2$Buffer;dstAccessMask(J)Lorg/lwjgl/vulkan/VkImageMemoryBarrier2$Buffer;",
                    ordinal = 1
            )
    )
    private long saltsAntiAliasing$useFrameGenerationPresentAccess(long regularAccessMask) {
        return FrameGenerationSwapchainImagePolicy.finalAccessMask(
                FsrRuntime.instance().isFrameGenerationSwapchainOwned(),
                regularAccessMask
        );
    }

    @Redirect(
            method = "configure",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"
            )
    )
    private int saltsAntiAliasing$createFrameGenerationSwapchain(
            VkDevice device,
            VkSwapchainCreateInfoKHR createInfo,
            VkAllocationCallbacks allocator,
            LongBuffer outSwapchain
    ) {
        return FsrRuntime.instance().createFrameGenerationSwapchain(device, createInfo, allocator, outSwapchain);
    }

    @Redirect(
            method = "destroySwapchain",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkDestroySwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"
            )
    )
    private void saltsAntiAliasing$destroyFrameGenerationSwapchain(
            VkDevice device,
            long swapchain,
            VkAllocationCallbacks allocator
    ) {
        FsrRuntime.instance().destroyFrameGenerationSwapchain(device, swapchain, allocator);
    }

    @Redirect(
            method = "configure",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkGetSwapchainImagesKHR(Lorg/lwjgl/vulkan/VkDevice;JLjava/nio/IntBuffer;Ljava/nio/LongBuffer;)I"
            )
    )
    private int saltsAntiAliasing$getFrameGenerationSwapchainImages(
            VkDevice device,
            long swapchain,
            IntBuffer imageCount,
            LongBuffer images
    ) {
        return FsrRuntime.instance().getFrameGenerationSwapchainImages(device, swapchain, imageCount, images);
    }

    @Redirect(
            method = "acquireNextTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"
            )
    )
    private int saltsAntiAliasing$acquireNextFrameGenerationImage(
            VkDevice device,
            long swapchain,
            long timeout,
            long semaphore,
            long fence,
            IntBuffer imageIndex
    ) {
        return FsrRuntime.instance().acquireNextFrameGenerationImage(
                device,
                swapchain,
                timeout,
                semaphore,
                fence,
                imageIndex
        );
    }

    @Redirect(
            method = "present",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"
            )
    )
    private int saltsAntiAliasing$presentFrameGenerationSwapchain(VkQueue queue, VkPresentInfoKHR presentInfo) {
        return FsrRuntime.instance().presentFrameGenerationSwapchain(queue, presentInfo);
    }
}
