package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.lwjgl.vulkan.VK10.*;

/** Chunk arenas reuse byte ranges; order their batches with draws on the graphics queue. */
@Mixin(value = AreaUploadManager.class, remap = false)
abstract class AreaUploadSynchronizationMixin {
    @Shadow Queue queue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void salts$orderArenaUpdates(CallbackInfo info) { queue = DeviceManager.getGraphicsQueue(); }

    @WrapOperation(method = "uploadAsync", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/queue/Queue;beginCommands()Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;"))
    private CommandPool.CommandBuffer salts$beforeUploadBatch(Queue owner, Operation<CommandPool.CommandBuffer> original) {
        var commands = original.call(owner);
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
            "Lnet/vulkanmod/vulkan/queue/Queue;submitCommands(Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;)J"))
    private long salts$afterUploadBatch(Queue owner, CommandPool.CommandBuffer commands, Operation<Long> original) {
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
            vkCmdPipelineBarrier(commands.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, barrier, null, null);
        }
        return original.call(owner, commands);
    }
}
