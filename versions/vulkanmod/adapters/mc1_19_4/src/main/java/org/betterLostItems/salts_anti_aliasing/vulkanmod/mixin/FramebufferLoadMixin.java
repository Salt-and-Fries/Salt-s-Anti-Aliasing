package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.*;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Resumed passes must retain world/HUD pixels and depth after post processing. */
@Mixin(value = {Framebuffer.class, SwapChain.class}, remap = false)
abstract class FramebufferLoadMixin {
    @WrapOperation(method = "beginRendering", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkRenderingAttachmentInfo;loadOp(I)Lorg/lwjgl/vulkan/VkRenderingAttachmentInfo;"))
    private VkRenderingAttachmentInfo salts$depthLoad(VkRenderingAttachmentInfo info, int ignored, Operation<VkRenderingAttachmentInfo> original) {
        return original.call(info, org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD);
    }
    @WrapOperation(method = "beginRendering", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkRenderingAttachmentInfo$Buffer;loadOp(I)Lorg/lwjgl/vulkan/VkRenderingAttachmentInfo$Buffer;"))
    private VkRenderingAttachmentInfo.Buffer salts$colorLoad(VkRenderingAttachmentInfo.Buffer info, int ignored, Operation<VkRenderingAttachmentInfo.Buffer> original) {
        return original.call(info, org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD);
    }
}
