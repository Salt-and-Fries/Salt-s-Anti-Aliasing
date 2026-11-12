package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.vulkan.device.DeviceManager;
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
@Mixin(value = UploadManager.class, remap = false)
abstract class UploadManagerSynchronizationMixin {
    @Shadow Queue queue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void salts$orderArenaUpdates(CallbackInfo info) { queue = DeviceManager.getGraphicsQueue(); }

    @WrapOperation(method = {"recordUpload", "copyBuffer(Lnet/vulkanmod/vulkan/memory/Buffer;ILnet/vulkanmod/vulkan/memory/Buffer;II)V"}, at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/queue/Queue;beginCommands()Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;"))
    private CommandPool.CommandBuffer salts$beforeUploadBatch(Queue owner, Operation<CommandPool.CommandBuffer> original) {
        var commands = original.call(owner);
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
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
    // This release's arena-growth path only emitted a write-to-write dependency.
    @WrapOperation(method = "copyBuffer(Lnet/vulkanmod/vulkan/memory/Buffer;ILnet/vulkanmod/vulkan/memory/Buffer;II)V", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/queue/TransferQueue;uploadBufferCmd(Lorg/lwjgl/vulkan/VkCommandBuffer;JJJJJ)V"))
    private static void salts$readCompletedArenaWrites(VkCommandBuffer commands, long source, long sourceOffset,
            long destination, long destinationOffset, long bytes, Operation<Void> original) {
        try (var stack = MemoryStack.stackPush()) {
            var barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().buffer(source).offset(sourceOffset).size(bytes)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, barrier, null);
        }
        original.call(commands, source, sourceOffset, destination, destinationOffset, bytes);
    }

}
