package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.MultisampleWorld;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Drawer.class, remap = false)
abstract class DrawerRenderingMixin {
    @Shadow private Framebuffer boundFramebuffer;
    @Inject(method = "initiateRenderPass", at = @At("RETURN"))
    private void salts$recording(CallbackInfo ci) { LegacyRenderer.recording(!Drawer.skipRendering); }
    @Inject(method = "endRenderPass", at = @At("RETURN"))
    private void salts$ended(CallbackInfo ci) { LegacyRenderer.recording(false); }
    @Inject(method = "endRendering", at = @At("HEAD"), cancellable = true)
    private void salts$resolve(CallbackInfo ci) {
        if (boundFramebuffer == null) { ci.cancel(); return; }
        if (boundFramebuffer instanceof MultisampleWorld multisample) {
            multisample.end(Drawer.getCommandBuffer()); boundFramebuffer = null; ci.cancel();
        }
    }
}
