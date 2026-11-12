package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirects all 26.2 first-person feature passes into the same multisampled hand target. */
@Mixin(CommandEncoder.class)
public abstract class HandRenderPassMixin {
    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At("HEAD"))
    private void saltsAntiAliasing$handAttachments(
            RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> callbackInfo) {
        VulkanHandAaController.instance().redirectHandPass(descriptor);
    }
}
