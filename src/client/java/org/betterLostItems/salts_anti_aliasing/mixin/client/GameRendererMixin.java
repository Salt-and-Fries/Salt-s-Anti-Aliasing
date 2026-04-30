package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneTemporalController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void saltsAntiAliasing$prepareTemporalJitter(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        GameRenderer gameRenderer = (GameRenderer) (Object) this;
        int width = gameRenderer.getMinecraft().getMainRenderTarget().width;
        int height = gameRenderer.getMinecraft().getMainRenderTarget().height;
        OpenGlSceneTemporalController.instance().prepareFrameJitter(taaActive, width, height);
    }

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            ),
            index = 5
    )
    private Matrix4f saltsAntiAliasing$configureTemporalProjection(Matrix4f projectionMatrix) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        return new Matrix4f(OpenGlSceneTemporalController.instance().jitterProjection(projectionMatrix, taaActive));
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            )
    )
    private void saltsAntiAliasing$beginSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        runtime.beginSceneRendering((GameRenderer) (Object) this);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void saltsAntiAliasing$endSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        runtime.endSceneRendering((GameRenderer) (Object) this);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void saltsAntiAliasing$applySceneOnlyPostProcessing(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        runtime.applyScenePostProcessing((GameRenderer) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void saltsAntiAliasing$recordPerformanceMetrics(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null || !renderLevel) {
            return;
        }

        GameRenderer gameRenderer = (GameRenderer) (Object) this;
        runtime.recordRenderedFrame(gameRenderer.getMinecraft().getFrameTimeNs(), gameRenderer.getMinecraft().getFps());
    }
}
