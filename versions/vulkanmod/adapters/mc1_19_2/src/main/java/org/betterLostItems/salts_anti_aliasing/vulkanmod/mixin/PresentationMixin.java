package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.IntBuffer;
@Mixin(value = Drawer.class, remap = false)
abstract class PresentationMixin {
    @Shadow private java.util.ArrayList<Long> imageAvailableSemaphores;
    @Shadow private java.util.ArrayList<Long> inFlightFences;
    @Unique private static int salts$acquired;
    /** Acquire and rebuild before native per-frame memory and chunk-upload bookkeeping. */
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "initiateRenderPass")
    private void salts$acquireEarly(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var width = stack.ints(0); var height = stack.ints(0);
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(Vulkan.window, width, height);
            if (width.get(0) == 0 || height.get(0) == 0) { original.call(); return; }
            if (Drawer.shouldRecreate) salts$recreate();
            var index = stack.ints(0);
            for (int attempt = 0; ; attempt++) {
                org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.check(
                        VK10.vkWaitForFences(Vulkan.getDevice(), inFlightFences.get(Drawer.getCurrentFrame()), true, -1L), "Wait before image acquisition");
                long semaphore = imageAvailableSemaphores.get(Drawer.getCurrentFrame());
                var chain = Vulkan.getSwapChain();
                salts$acquired = FsrRuntime.instance().acquireNextFrameGenerationImage(Vulkan.getDevice(), chain.getId(), -1L, semaphore, 0, index);
                if (salts$acquired == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR && attempt < 2) { salts$recreate(); continue; }
                if (salts$acquired != VK10.VK_SUCCESS && salts$acquired != KHRSwapchain.VK_SUBOPTIMAL_KHR)
                    throw new IllegalStateException("Could not acquire VulkanMod image: " + salts$acquired);
                LegacyRenderer.imageIndex(index.get(0));
                AcquiredImageWait.acquired(salts$acquired, semaphore);
                PresentationReadySemaphores.acquired(Vulkan.getDevice(), chain.getId(), index.get(0));
                break;
            }
        }
        original.call();
    }
    @Unique private void salts$recreate() {
        Drawer.shouldRecreate = false;
        ((DrawerAccessor) this).salts$recreateSwapChain();
        // Minecraft reset the previous ring slot before the swapchain rebuilt it.
        Drawer.getInstance().resetBuffers();
    }
    @Redirect(method = "drawFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"))
    private int salts$cached(VkDevice device, long chain, long timeout, long semaphore, long fence, IntBuffer index) {
        index.put(0, LegacyRenderer.imageIndex()); return salts$acquired == KHRSwapchain.VK_SUBOPTIMAL_KHR ? VK10.VK_SUCCESS : salts$acquired;
    }
    @Redirect(method = "drawFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"))
    private int salts$present(VkQueue queue, VkPresentInfoKHR info) {
        DlssRuntime.instance().present(queue.address(), info.address(), false);
        try { return FsrRuntime.instance().presentFrameGenerationSwapchain(queue, info); }
        finally { DlssRuntime.instance().present(queue.address(), info.address(), true); }
    }
}
