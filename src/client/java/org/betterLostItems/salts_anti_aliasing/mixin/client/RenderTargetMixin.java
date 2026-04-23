package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneMsaaController;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneScaleController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    @Inject(method = "getColorTexture", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainColorTexture(CallbackInfoReturnable<GpuTexture> callbackInfo) {
        GpuTexture redirectedTexture = OpenGlSceneScaleController.instance().overrideColorTexture((RenderTarget) (Object) this);
        if (redirectedTexture != null) {
            callbackInfo.setReturnValue(redirectedTexture);
            return;
        }

        OpenGlSceneMsaaController.instance().syncColorIfNeeded((RenderTarget) (Object) this);
    }

    @Inject(method = "getColorTextureView", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainColorTextureView(CallbackInfoReturnable<GpuTextureView> callbackInfo) {
        GpuTextureView redirectedTextureView =
                OpenGlSceneScaleController.instance().overrideColorTextureView((RenderTarget) (Object) this);
        if (redirectedTextureView != null) {
            callbackInfo.setReturnValue(redirectedTextureView);
            return;
        }

        OpenGlSceneMsaaController.instance().syncColorIfNeeded((RenderTarget) (Object) this);
    }

    @Inject(method = "getDepthTexture", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthTexture(CallbackInfoReturnable<GpuTexture> callbackInfo) {
        GpuTexture redirectedTexture = OpenGlSceneScaleController.instance().overrideDepthTexture((RenderTarget) (Object) this);
        if (redirectedTexture != null) {
            callbackInfo.setReturnValue(redirectedTexture);
            return;
        }

        OpenGlSceneMsaaController.instance().syncDepthIfNeeded((RenderTarget) (Object) this);
    }

    @Inject(method = "getDepthTextureView", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthTextureView(CallbackInfoReturnable<GpuTextureView> callbackInfo) {
        GpuTextureView redirectedTextureView =
                OpenGlSceneScaleController.instance().overrideDepthTextureView((RenderTarget) (Object) this);
        if (redirectedTextureView != null) {
            callbackInfo.setReturnValue(redirectedTextureView);
            return;
        }

        OpenGlSceneMsaaController.instance().syncDepthIfNeeded((RenderTarget) (Object) this);
    }

    @Inject(method = "copyDepthFrom", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$syncMainDepthBeforeCopy(RenderTarget sourceTarget, CallbackInfo callbackInfo) {
        if (OpenGlSceneScaleController.instance().redirectCopyDepth((RenderTarget) (Object) this, sourceTarget)) {
            callbackInfo.cancel();
            return;
        }

        OpenGlSceneMsaaController.instance().syncSourceDepthBeforeCopy(sourceTarget);
    }
}
