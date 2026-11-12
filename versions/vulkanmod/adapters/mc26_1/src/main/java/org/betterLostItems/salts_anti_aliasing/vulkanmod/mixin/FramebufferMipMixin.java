package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** VulkanMod stores base-image dimensions even when the bound attachment is a smaller mip view. */
@Mixin(value = Framebuffer.class, remap = false)
abstract class FramebufferMipMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow private int level;
    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true)
    private void salts$mipWidth(CallbackInfoReturnable<Integer> result) { result.setReturnValue(Math.max(1, width >> level)); }
    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true)
    private void salts$mipHeight(CallbackInfoReturnable<Integer> result) { result.setReturnValue(Math.max(1, height >> level)); }
}
