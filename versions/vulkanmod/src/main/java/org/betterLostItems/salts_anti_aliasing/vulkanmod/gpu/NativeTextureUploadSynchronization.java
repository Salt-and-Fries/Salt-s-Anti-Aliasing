package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;

/** Repeated uploads can overlap while the atlas stays in TRANSFER_DST layout. */
public final class NativeTextureUploadSynchronization {
    private NativeTextureUploadSynchronization() {}

    public static void beforeCopy(VkCommandBuffer commands, long image, int layout, VkBufferImageCopy.Buffer regions) {
        try (var stack = MemoryStack.stackPush()) {
            var barriers = VkImageMemoryBarrier.calloc(regions.remaining(), stack);
            for (int i = 0; i < regions.remaining(); i++) {
                var subresource = regions.get(regions.position() + i).imageSubresource();
                var barrier = barriers.get(i).sType$Default().image(image).oldLayout(layout).newLayout(layout)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.subresourceRange().aspectMask(subresource.aspectMask()).baseMipLevel(subresource.mipLevel())
                        .levelCount(1).baseArrayLayer(subresource.baseArrayLayer()).layerCount(subresource.layerCount());
            }
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barriers);
        }
    }
}
