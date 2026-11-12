package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import static org.lwjgl.vulkan.VK10.*;

/** LOAD also reads attachments; native transitions only made their writes visible. */
@Mixin(value = net.vulkanmod.vulkan.framebuffer.RenderPass.class, remap = false)
abstract class NativeRenderPassSynchronizationMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCmdBeginRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderPassBeginInfo;I)V"))
    private static void salts$makeAttachmentLoadsVisible(VkCommandBuffer commands, VkRenderPassBeginInfo info,
                                                       int contents, Operation<Void> original) {
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0, barrier, null, null);
        }
        original.call(commands, info, contents);
    }
}
