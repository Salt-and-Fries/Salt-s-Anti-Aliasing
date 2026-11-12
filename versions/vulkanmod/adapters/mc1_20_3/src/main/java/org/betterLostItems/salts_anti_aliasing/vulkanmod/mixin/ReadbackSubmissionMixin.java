package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.AcquiredImageWait;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import static org.lwjgl.vulkan.VK10.*;

/** This VulkanMod release predates flushCmds; submit before its independent readback queue. */
@Mixin(value = ImageUtil.class, remap = false)
abstract class ReadbackSubmissionMixin {
    @WrapMethod(method = "downloadTexture")
    private static void salts$flush(VulkanImage image, long destination, Operation<Void> original) {
        if (!Renderer.isRecording()) { original.call(image, destination); return; }
        var renderer = Renderer.getInstance();
        var access = (RendererAccessor) renderer;
        var framebuffer = access.salts$boundFramebuffer();
        var pass = renderer.getBoundRenderPass();
        var commands = Renderer.getCommandBuffer();
        renderer.endRenderPass();
        VulkanContext.check(vkEndCommandBuffer(commands), "End frame for texture readback");
        Synchronization.INSTANCE.waitFences();
        try (var stack = MemoryStack.stackPush()) {
            var info = VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(commands));
            var queue = DeviceManager.getGraphicsQueue().queue();
            VulkanContext.check(AcquiredImageWait.submit(info, fixed -> vkQueueSubmit(queue, fixed, 0)), "Submit frame for readback");
            VulkanContext.check(vkQueueWaitIdle(queue), "Wait for readback frame");
            VulkanContext.check(vkResetCommandBuffer(commands, 0), "Reset readback frame");
            VulkanContext.check(vkBeginCommandBuffer(commands, VkCommandBufferBeginInfo.calloc(stack).sType$Default()), "Resume readback frame");
            vkCmdSetDepthBias(commands, 0, 0, 0);
            vkCmdSetLineWidth(commands, 1);
        }
        try { original.call(image, destination); }
        finally {
            access.salts$boundPipelineHandle(0);
            if (framebuffer != null && pass != null) renderer.beginRendering(pass, framebuffer);
        }
    }
}
