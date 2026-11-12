package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.GraphicsQueue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import static org.lwjgl.vulkan.VK10.*;

/** Order reused chunk-arena regions on the queue that consumes their draw commands. */
@Mixin(value = AreaUploadManager.class, remap = false)
abstract class AreaUploadSynchronizationMixin {
    @WrapOperation(method = {"uploadAsync", "copy"}, at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/queue/TransferQueue;beginCommands()Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;"))
    private CommandPool.CommandBuffer salts$beginOrderedBatch(TransferQueue ignored, Operation<CommandPool.CommandBuffer> original) {
        var commands = GraphicsQueue.getInstance().beginCommands();
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(commands.getHandle(), VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, barrier, null, null);
        }
        return commands;
    }

    @WrapOperation(method = "submitUploads", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/queue/TransferQueue;submitCommands(Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;)J"))
    private long salts$submitOrderedBatch(TransferQueue ignored, CommandPool.CommandBuffer commands, Operation<Long> original) {
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
            vkCmdPipelineBarrier(commands.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, barrier, null, null);
        }
        return GraphicsQueue.getInstance().submitCommands(commands);
    }
}
