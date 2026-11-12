package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Framebuffer;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The old device advertises Vulkan 1.2 and exposes dynamic rendering through KHR. */
@Mixin(value = Framebuffer.class, remap = false)
abstract class LegacyRenderingEntryPointMixin {
    @Redirect(method = "beginRendering", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK13;vkCmdBeginRendering(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V"))
    private void salts$extensionEntry(VkCommandBuffer commands, VkRenderingInfo info) {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeAttachmentSynchronization.beforeLoad(commands);
        KHRDynamicRendering.vkCmdBeginRenderingKHR(commands, info);
    }
}
