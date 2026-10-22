package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/** Resolves multisampled opaque depth for vanilla's single-sample transparency passes. */
final class VulkanDepthResolver {
    private static long lastPhysicalDevice;
    private static int resolveMode;

    private VulkanDepthResolver() {}

    static void resolve(VulkanDevice device, RenderTarget source, GpuTextureView destination) {
        VulkanGpuTexture sourceTexture = (VulkanGpuTexture) source.getDepthTexture();
        VulkanGpuTexture destinationTexture = (VulkanGpuTexture) destination.texture();
        VulkanGpuTextureView sourceView = (VulkanGpuTextureView) source.getDepthTextureView();
        VulkanGpuTextureView destinationView = (VulkanGpuTextureView) destination;
        if (VulkanMsaaState.sampleCount(sourceView) <= 1 || VulkanMsaaState.sampleCount(destination) != 1
                || sourceTexture.getFormat() != destinationTexture.getFormat()
                || sourceTexture.getWidth(0) != destinationTexture.getWidth(0)
                || sourceTexture.getHeight(0) != destinationTexture.getHeight(0)) {
            throw new IllegalArgumentException("MSAA depth resolve requires matching depth formats/sizes and N-to-1 samples");
        }
        var encoder = device.createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int mode = depthResolveMode(device, stack);
            var commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
            barrier(commandBuffer, stack, sourceTexture, destinationTexture);
            var attachment = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(sourceView.vkImageView()).imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                    .resolveMode(mode).resolveImageView(destinationView.vkImageView())
                    .resolveImageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
            var rendering = VkRenderingInfoKHR.calloc(stack).sType$Default().layerCount(1)
                    .pDepthAttachment(attachment);
            rendering.renderArea().offset().set(0, 0);
            rendering.renderArea().extent().set(source.width, source.height);
            // A load/store rendering scope resolves the existing samples without drawing or clearing.
            KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);
            KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            barrier(commandBuffer, stack, sourceTexture, destinationTexture);
            int endResult = VK12.vkEndCommandBuffer(commandBuffer);
            if (endResult != VK12.VK_SUCCESS) throw new IllegalStateException("Cannot end depth resolve command buffer: " + endResult);
            encoder.execute(commandBuffer);
        }
    }

    private static int depthResolveMode(VulkanDevice device, MemoryStack stack) {
        var physical = device.vkDevice().getPhysicalDevice();
        if (lastPhysicalDevice != physical.address()) {
            var depth = VkPhysicalDeviceDepthStencilResolveProperties.calloc(stack).sType$Default();
            var properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(depth.address());
            VK12.vkGetPhysicalDeviceProperties2(physical, properties);
            int supported = depth.supportedDepthResolveModes();
            // Reverse-Z's maximum is the nearest covered opaque sample. SAMPLE_ZERO is guaranteed.
            resolveMode = (supported & VK12.VK_RESOLVE_MODE_MAX_BIT) != 0
                    ? VK12.VK_RESOLVE_MODE_MAX_BIT : VK12.VK_RESOLVE_MODE_SAMPLE_ZERO_BIT;
            lastPhysicalDevice = physical.address();
            MetricsDiagnostics.info("msaa.depth", "depth_resolve_configured",
                    "supportedModes", supported, "selectedMode", resolveMode);
        }
        return resolveMode;
    }

    private static void barrier(VkCommandBuffer commandBuffer, MemoryStack stack,
                                VulkanGpuTexture source, VulkanGpuTexture destination) {
        var barriers = VkImageMemoryBarrier2.calloc(2, stack);
        for (int index = 0; index < 2; index++) {
            var barrier = barriers.get(index).sType$Default()
                    .srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR)
                    .srcAccessMask(KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR)
                    .dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR)
                    .dstAccessMask(KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                            | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR)
                    .oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL).newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                    .image(index == 0 ? source.vkImage() : destination.vkImage());
            barrier.subresourceRange().aspectMask(VK12.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        }
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }
}
