package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeTextureUploadSynchronization;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = net.vulkanmod.vulkan.texture.VulkanImage.class, remap = false)
abstract class NativeTextureUploadSynchronizationMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCmdCopyBufferToImage(Lorg/lwjgl/vulkan/VkCommandBuffer;JJILorg/lwjgl/vulkan/VkBufferImageCopy$Buffer;)V"))
    private static void salts$orderOverlappingCopies(VkCommandBuffer commands, long buffer, long image, int layout,
                                                    VkBufferImageCopy.Buffer regions, Operation<Void> original) {
        NativeTextureUploadSynchronization.beforeCopy(commands, image, layout, regions);
        original.call(commands, buffer, image, layout, regions);
    }
}
