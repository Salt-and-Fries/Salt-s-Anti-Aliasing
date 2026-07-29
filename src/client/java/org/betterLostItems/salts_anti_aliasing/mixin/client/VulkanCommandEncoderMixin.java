package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks the active render pass sample count for Vulkan MSAA pipeline selection.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanCommandEncoder")
public abstract class VulkanCommandEncoderMixin {
    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void saltsAntiAliasing$beginRenderPassSampleScope(
            RenderPassDescriptor descriptor,
            CallbackInfoReturnable<RenderPassBackend> callbackInfo
    ) {
        VulkanMsaaState.beginRenderPass(descriptor);
    }

    @Inject(method = "createRenderPass", at = @At("RETURN"))
    private void saltsAntiAliasing$endRenderPassSampleScope(
            RenderPassDescriptor descriptor,
            CallbackInfoReturnable<RenderPassBackend> callbackInfo
    ) {
        VulkanMsaaState.endRenderPass();
    }
}
