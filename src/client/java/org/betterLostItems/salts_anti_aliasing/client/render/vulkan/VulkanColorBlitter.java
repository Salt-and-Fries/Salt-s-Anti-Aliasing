package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import org.betterLostItems.salts_anti_aliasing.mixin.client.GpuDeviceAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;

/**
 * Performs synchronized Vulkan color blits between Minecraft render targets.
 */
public final class VulkanColorBlitter {
    private VulkanColorBlitter() {
    }

    public static void blitColor(RenderTarget source, RenderTarget destination) {
        if (source == null || destination == null || source == destination) {
            return;
        }

        GpuTexture sourceTexture = source.getColorTexture();
        GpuTexture destinationTexture = destination.getColorTexture();
        if (sourceTexture == null || destinationTexture == null) {
            return;
        }
        if (!(sourceTexture instanceof VulkanGpuTexture sourceVulkanTexture)
                || !(destinationTexture instanceof VulkanGpuTexture destinationVulkanTexture)) {
            throw new IllegalStateException("Vulkan color blit requires Vulkan texture objects");
        }

        VulkanCommandEncoder encoder = vulkanDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
            transferBarrier(commandBuffer, stack, sourceVulkanTexture, destinationVulkanTexture);

            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.srcSubresource()
                    .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.dstSubresource()
                    .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.srcOffsets(0).set(0, 0, 0);
            region.srcOffsets(1).set(source.width, source.height, 1);
            region.dstOffsets(0).set(0, 0, 0);
            region.dstOffsets(1).set(destination.width, destination.height, 1);

            VK12.vkCmdBlitImage(
                    commandBuffer,
                    sourceVulkanTexture.vkImage(),
                    VK12.VK_IMAGE_LAYOUT_GENERAL,
                    destinationVulkanTexture.vkImage(),
                    VK12.VK_IMAGE_LAYOUT_GENERAL,
                    region,
                    VK12.VK_FILTER_LINEAR
            );
            completedBarrier(commandBuffer, stack, sourceVulkanTexture, destinationVulkanTexture);
            encoder.execute(commandBuffer);
        }
    }

    private static void transferBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VulkanGpuTexture sourceTexture,
            VulkanGpuTexture destinationTexture
    ) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
        configureImageBarrier(
                barriers.get(0),
                sourceTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(1),
                destinationTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private static void completedBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VulkanGpuTexture sourceTexture,
            VulkanGpuTexture destinationTexture
    ) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
        configureImageBarrier(
                barriers.get(0),
                sourceTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(1),
                destinationTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private static void configureImageBarrier(
            VkImageMemoryBarrier2 barrier,
            VulkanGpuTexture texture,
            long sourceStage,
            long sourceAccess,
            long destinationStage,
            long destinationAccess
    ) {
        barrier.sType$Default()
                .srcStageMask(sourceStage)
                .srcAccessMask(sourceAccess)
                .dstStageMask(destinationStage)
                .dstAccessMask(destinationAccess)
                .oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .image(texture.vkImage());
        barrier.subresourceRange()
                .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
    }

    private static void pipelineBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VkImageMemoryBarrier2.Buffer barriers
    ) {
        VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(barriers);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
    }

    private static VulkanDevice vulkanDevice() {
        GpuDevice device = RenderSystem.getDevice();
        GpuDeviceBackend backend = ((GpuDeviceAccessor) device).saltsAntiAliasing$backend();
        if (backend instanceof VulkanDevice vulkanDevice) {
            return vulkanDevice;
        }

        throw new IllegalStateException("Minecraft is not running on the Vulkan backend");
    }
}
