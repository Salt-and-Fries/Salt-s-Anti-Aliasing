package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Renderer;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.IntBuffer;

@Mixin(value = Renderer.class, remap = false)
abstract class PresentationMixin {
    @Redirect(method = "beginFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"))
    private int salts$acquire(VkDevice device, long swapchain, long timeout, long semaphore, long fence, IntBuffer index) {
        int result = FsrRuntime.instance().acquireNextFrameGenerationImage(device, swapchain, timeout, semaphore, fence, index);
        org.betterLostItems.salts_anti_aliasing.vulkanmod.AcquiredImageWait.acquired(result, semaphore);
        if (result == org.lwjgl.vulkan.VK10.VK_SUCCESS || result == KHRSwapchain.VK_SUBOPTIMAL_KHR)
            org.betterLostItems.salts_anti_aliasing.vulkanmod.PresentationReadySemaphores.acquired(device, swapchain, index.get(0));
        return result;
    }
    @Redirect(method = "submitFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"))
    private int salts$present(VkQueue queue, VkPresentInfoKHR info) {
        DlssRuntime.instance().present(queue.address(), info.address(), false);
        try { return FsrRuntime.instance().presentFrameGenerationSwapchain(queue, info); }
        finally { DlssRuntime.instance().present(queue.address(), info.address(), true); }
    }
}
