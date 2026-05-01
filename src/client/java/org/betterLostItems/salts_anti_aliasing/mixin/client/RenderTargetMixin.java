package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    /**
     * Redirects color texture reads when the modern scene target is active.
     */
    @Inject(method = "getColorTexture", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainColorTexture(CallbackInfoReturnable<GpuTexture> callbackInfo) {
        GpuTexture redirectedTexture = ModernMinecraftHooks.redirectColorTexture((RenderTarget) (Object) this);
        if (redirectedTexture != null) {
            callbackInfo.setReturnValue(redirectedTexture);
        }
    }

    /**
     * Redirects color texture view reads when the modern scene target is active.
     */
    @Inject(method = "getColorTextureView", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainColorTextureView(CallbackInfoReturnable<GpuTextureView> callbackInfo) {
        GpuTextureView redirectedTextureView = ModernMinecraftHooks.redirectColorTextureView((RenderTarget) (Object) this);
        if (redirectedTextureView != null) {
            callbackInfo.setReturnValue(redirectedTextureView);
        }
    }

    /**
     * Redirects depth texture reads when the modern scene target is active.
     */
    @Inject(method = "getDepthTexture", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthTexture(CallbackInfoReturnable<GpuTexture> callbackInfo) {
        GpuTexture redirectedTexture = ModernMinecraftHooks.redirectDepthTexture((RenderTarget) (Object) this);
        if (redirectedTexture != null) {
            callbackInfo.setReturnValue(redirectedTexture);
        }
    }

    /**
     * Redirects depth texture view reads when the modern scene target is active.
     */
    @Inject(method = "getDepthTextureView", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthTextureView(CallbackInfoReturnable<GpuTextureView> callbackInfo) {
        GpuTextureView redirectedTextureView = ModernMinecraftHooks.redirectDepthTextureView((RenderTarget) (Object) this);
        if (redirectedTextureView != null) {
            callbackInfo.setReturnValue(redirectedTextureView);
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
