package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;

/** Dependencies omitted by the original dynamic-rendering attachment helpers. */
public final class NativeAttachmentSynchronization {
    private NativeAttachmentSynchronization() {}

    public static void beforeLoad(VkCommandBuffer commands) {
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0, barrier, null, null);
        }
    }

    public static int prepareImageTransitions(int sourceStages, VkImageMemoryBarrier.Buffer images) {
        if (images == null) return sourceStages;
        for (int i = images.position(); i < images.limit(); i++) {
            var image = images.get(i);
            if (image.oldLayout() != VK_IMAGE_LAYOUT_UNDEFINED) {
                image.srcAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
                sourceStages = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }
            // The frame-generation proxy can end in shader-read layout where the
            // original helper assumed presentation. Cover its actual consumers too.
            image.dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
        }
        return sourceStages;
    }
}
