package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.MultisampleWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderPass.class, remap = false)
abstract class DynamicMsaaPassMixin {
    @Inject(method = "createRenderPass", at = @At("HEAD"), cancellable = true)
    private void salts$dynamicAttachments(CallbackInfo info) {
        if ((Object) this instanceof MultisampleWorld.Pass) info.cancel();
    }
}
