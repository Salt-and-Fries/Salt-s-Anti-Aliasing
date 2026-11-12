package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import static org.lwjgl.vulkan.VK10.*;
/** Submit the actual acquired image before the native independent screenshot queue reads it. */
@Mixin(value = VulkanImage.class, remap = false)
abstract class ReadbackSubmissionMixin {
    @WrapMethod(method = "downloadTexture")
    private static void salts$read(int width, int height, int bytes, java.nio.ByteBuffer pixels, long image, Operation<Void> original) {
        if (!LegacyRenderer.isRecording()) { original.call(width, height, bytes, pixels, image); return; }
        var renderer = LegacyRenderer.getInstance();
        var framebuffer = renderer.salts$boundFramebuffer();
        renderer.endRenderPass();
        var commands = LegacyRenderer.getCommandBuffer();
        var source = org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderTargets.window().color();
        if (source.image != image) throw new IllegalArgumentException("Readback did not target the acquired swapchain image");
        int previous = source.layout();
        source.transition(commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        VulkanContext.check(vkEndCommandBuffer(commands), "End screenshot frame");
        Synchronization.waitFences();
        try (var stack = MemoryStack.stackPush()) {
            var submit = VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(commands));
            var queue = Vulkan.getGraphicsQueue();
            VulkanContext.check(AcquiredImageWait.submit(submit, info -> vkQueueSubmit(queue, info, 0)), "Submit screenshot frame");
            VulkanContext.check(vkQueueWaitIdle(queue), "Wait for screenshot frame");
            original.call(width, height, bytes, pixels, image);
            VulkanContext.check(vkResetCommandBuffer(commands, 0), "Reset screenshot frame");
            VulkanContext.check(vkBeginCommandBuffer(commands, VkCommandBufferBeginInfo.calloc(stack).sType$Default()), "Resume screenshot frame");
            vkCmdSetDepthBias(commands, 0, 0, 0); vkCmdSetLineWidth(commands, 1);
            source.transition(commands, previous);
        }
        finally { if (framebuffer != null) renderer.beginRendering(null, framebuffer); }
    }
}
