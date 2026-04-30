package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneMsaaController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    @Redirect(
            method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/opengl/GlTextureView;getFbo(Lcom/mojang/blaze3d/opengl/DirectStateAccess;Lcom/mojang/blaze3d/textures/GpuTexture;)I"
            )
    )
    private int saltsAntiAliasing$redirectSceneFramebuffer(
            GlTextureView colorView,
            DirectStateAccess directStateAccess,
            GpuTexture depthTexture,
            Supplier<String> labelSupplier,
            com.mojang.blaze3d.textures.GpuTextureView originalColorView,
            OptionalInt clearColor,
            com.mojang.blaze3d.textures.GpuTextureView originalDepthView,
            OptionalDouble clearDepth
    ) {
        int originalFramebufferId = colorView.getFbo(directStateAccess, depthTexture);
        Integer overrideFramebufferId = OpenGlSceneMsaaController.instance().overrideFramebuffer(
                colorView,
                depthTexture,
                originalFramebufferId
        );
        return overrideFramebufferId != null ? overrideFramebufferId : originalFramebufferId;
    }

    @Inject(method = "clearColorTexture", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorColorClearToMsaa(GpuTexture colorTexture, int clearColor, CallbackInfo callbackInfo) {
        OpenGlSceneMsaaController.instance().mirrorClearColorIfNeeded(colorTexture, clearColor);
    }

    @Inject(method = "clearDepthTexture", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorDepthClearToMsaa(GpuTexture depthTexture, double clearDepth, CallbackInfo callbackInfo) {
        OpenGlSceneMsaaController.instance().mirrorClearDepthIfNeeded(depthTexture, clearDepth);
    }

    @Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorColorDepthClearToMsaa(
            GpuTexture colorTexture,
            int clearColor,
            GpuTexture depthTexture,
            double clearDepth,
            CallbackInfo callbackInfo
    ) {
        OpenGlSceneMsaaController.instance().mirrorClearColorAndDepthIfNeeded(colorTexture, clearColor, depthTexture, clearDepth);
    }

    @Inject(method = "finishRenderPass", at = @At("TAIL"))
    private void saltsAntiAliasing$resolveAfterMainPass(CallbackInfo callbackInfo) {
        OpenGlSceneMsaaController.instance().onRenderPassFinished();
    }
}
