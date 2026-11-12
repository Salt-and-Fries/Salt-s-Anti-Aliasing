package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.vulkan.SwapChain;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeAttachmentSynchronization;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The swapchain already uses KHR entrypoints, unlike its original Framebuffer base class. */
@Mixin(value = SwapChain.class, remap = false)
abstract class SwapchainAttachmentLoadMixin {
    @WrapOperation(method = "beginRendering", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V"))
    private void salts$loadStoredDepth(VkCommandBuffer commands, VkRenderingInfo info, Operation<Void> original) {
        NativeAttachmentSynchronization.beforeLoad(commands);
        original.call(commands, info);
    }
}
