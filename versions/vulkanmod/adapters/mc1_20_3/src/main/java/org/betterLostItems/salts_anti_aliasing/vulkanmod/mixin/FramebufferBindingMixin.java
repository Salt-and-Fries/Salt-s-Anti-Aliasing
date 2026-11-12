package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.gl.GlFramebuffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Switching the render pass resets GL bindings while the first attachment is installed. */
@Mixin(value = GlFramebuffer.class, remap = false)
abstract class FramebufferBindingMixin {
    @Shadow private static GlFramebuffer boundFramebuffer;
    @Inject(method = "beginRendering(Lnet/vulkanmod/gl/GlFramebuffer;)V", at = @At("RETURN"))
    private static void salts$retainAttachmentBinding(GlFramebuffer framebuffer, CallbackInfo info) {
        boundFramebuffer = framebuffer;
    }
}
