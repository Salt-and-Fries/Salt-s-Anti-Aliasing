package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Documents render target mixin behavior for Salt's Anti Aliasing. Mixin bridge code for carefully
 * scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    /**
     * Redirects the 1.21.1 main framebuffer bind to the MSAA framebuffer while MSAA is active.
     */
    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$overrideMainFramebuffer(boolean setViewport, CallbackInfo callbackInfo) {
        if (ModernMinecraftHooks.forceScaledSceneViewport((RenderTarget) (Object) this, setViewport)) {
            ((RenderTarget) (Object) this).bindWrite(true);
            callbackInfo.cancel();
            return;
        }

        if (ModernMinecraftHooks.overrideMainFramebuffer((RenderTarget) (Object) this, setViewport)) {
            callbackInfo.cancel();
        }
    }

    /**
     * Keeps Minecraft depth copies coherent while the main scene target is redirected.
     */
    @Inject(method = "copyDepthFrom", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthBeforeCopy(RenderTarget sourceTarget, CallbackInfo callbackInfo) {
        if (ModernMinecraftHooks.redirectCopyDepth((RenderTarget) (Object) this, sourceTarget)) {
            callbackInfo.cancel();
        }
    }
}
