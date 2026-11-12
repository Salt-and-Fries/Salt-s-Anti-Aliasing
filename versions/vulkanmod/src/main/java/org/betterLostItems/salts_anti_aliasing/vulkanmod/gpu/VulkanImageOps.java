package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkBufferImageCopy;
import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Transfer operations outside a VulkanMod render pass; image ownership remains explicit. */
public final class VulkanImageOps {
    private VulkanImageOps() {}

    public static void upload(VulkanContext context, VkCommandBuffer commands, VulkanFrameResources frame,
                              VulkanImageResource image, ByteBuffer pixels) {
        var staging = new VulkanHostBuffer(context, pixels.remaining(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        try {
            staging.write(pixels);
            image.transition(commands, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var copy = VkBufferImageCopy.calloc(1, stack).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
                copy.imageSubresource().aspectMask(image.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
                copy.imageOffset().set(0, 0, 0); copy.imageExtent().set(image.width, image.height, 1);
                vkCmdCopyBufferToImage(commands, staging.buffer, image.image, image.layout(), copy);
            }
            frame.keepUntilFence(staging);
        } catch (RuntimeException e) { staging.close(); throw e; }
    }

    public static void blit(VkCommandBuffer commands, VulkanImageResource source, VulkanImageResource target, boolean flipY) {
        if (source.image == target.image || source.samples != 1 || target.samples != 1)
            throw new IllegalArgumentException("Blit requires distinct single-sample images");
        if ((source.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0 || (target.usage & VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0)
            throw new IllegalArgumentException("Blit images lack the required transfer usage flags");
        source.transition(commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        target.transition(commands, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var region = VkImageBlit.calloc(1, stack);
            int sourceAspect = (source.aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0 ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
            int targetAspect = (target.aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0 ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
            if (sourceAspect != targetAspect || sourceAspect == VK_IMAGE_ASPECT_DEPTH_BIT && source.format != target.format)
                throw new IllegalArgumentException("Depth blit requires matching formats/aspects");
            region.srcSubresource().aspectMask(sourceAspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.dstSubresource().aspectMask(targetAspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.srcOffsets(0).set(0, 0, 0); region.srcOffsets(1).set(source.width, source.height, 1);
            region.dstOffsets(0).set(0, flipY ? target.height : 0, 0);
            region.dstOffsets(1).set(target.width, flipY ? 0 : target.height, 1);
            vkCmdBlitImage(commands, source.image, source.layout(), target.image, target.layout(), region,
                    source.aspect == VK_IMAGE_ASPECT_COLOR_BIT ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
        }
    }
}
