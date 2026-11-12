package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.vulkan.SwapChain;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeAttachmentSynchronization;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {SwapChain.class, VulkanImage.class}, remap = false)
abstract class NativeImageSynchronizationMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V"))
    private static void salts$preserveAttachmentAccess(VkCommandBuffer commands, int source, int destination, int flags,
            VkMemoryBarrier.Buffer memory, VkBufferMemoryBarrier.Buffer buffers, VkImageMemoryBarrier.Buffer images,
            Operation<Void> original) {
        original.call(commands, NativeAttachmentSynchronization.prepareImageTransitions(source, images),
                org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, flags, memory, buffers, images);
    }
}
