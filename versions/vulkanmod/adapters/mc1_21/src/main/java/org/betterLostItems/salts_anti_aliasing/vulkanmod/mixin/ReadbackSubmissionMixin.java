package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.ImageUtil;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.vulkanmod.vulkan.texture.VulkanImage;

@Mixin(value = ImageUtil.class, remap = false)
abstract class ReadbackSubmissionMixin {
    // Submit before downloadTexture records any layout transition on its immediate queue.
    @WrapMethod(method = "downloadTexture")
    private static void salts$submitBeforeReadback(VulkanImage image, long destination, Operation<Void> operation) {
        var renderer = Renderer.getInstance();
        var framebuffer = renderer.getBoundFramebuffer();
        var pass = renderer.getBoundRenderPass();
        boolean recording = Renderer.isRecording();
        try (var ignored = org.betterLostItems.salts_anti_aliasing.vulkanmod.ReadbackScope.enter()) {
            if (recording) renderer.flushCmds();
            operation.call(image, destination);
        }
        finally {
            var access = (RendererAccessor) renderer;
            access.salts$boundPipeline(null); access.salts$boundPipelineHandle(0);
            // flushCmds starts a fresh command buffer. Vanilla can draw again after a
            // screenshot, so preserve the active pass instead of leaving its state null.
            if (recording && Renderer.isRecording() && framebuffer != null && pass != null)
                renderer.beginRenderPass(pass, framebuffer);
        }
    }
}
