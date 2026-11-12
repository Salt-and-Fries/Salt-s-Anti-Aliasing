package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeAttachmentSynchronization;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dynamic passes also LOAD depth stored by the preceding rendering instance. */
@Mixin(value = net.vulkanmod.vulkan.framebuffer.RenderPass.class, remap = false)
abstract class NativeDynamicRenderPassSynchronizationMixin {
    @WrapOperation(method = "beginDynamicRendering", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V"))
    private void salts$makeAttachmentLoadsVisible(VkCommandBuffer commands, VkRenderingInfo info,
                                                 Operation<Void> original) {
        NativeAttachmentSynchronization.beforeLoad(commands);
        original.call(commands, info);
    }
}
