package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private GlobalSettingsUniform globalSettingsUniform;
    @Shadow @Final private Minecraft minecraft;
    @Unique private boolean salts$changedScreenSize;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void salts$beginWorld(DeltaTracker tracker, CallbackInfo info) {
        SaltsVulkanModClient.beginWorld();
        var scene = SaltsVulkanModClient.sceneOverride();
        salts$changedScreenSize = scene != null;
        if (scene != null) salts$updateScreenSize(scene.width, scene.height, tracker);
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f salts$jitterAndCapture(Matrix4f projection) {
        if (!SaltsVulkanModClient.temporalActive()) return projection;
        var camera = gameRenderState.levelRenderState.cameraRenderState;
        SaltsVulkanModClient.temporal().depthRange(.05f, camera.depthFar);
        return projection.set(SaltsVulkanModClient.temporal().capture(projection, camera.viewRotationMatrix,
                camera.pos.x, camera.pos.y, camera.pos.z, camera.xRot, camera.yRot,
                2f * (float) Math.atan(1f / Math.abs(camera.projectionMatrix.m11()))));
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            shift = At.Shift.AFTER))
    private void salts$resolveBeforeDepthClear(DeltaTracker tracker, CallbackInfo info) {
        SaltsVulkanModClient.afterWorld();
        if (salts$changedScreenSize) {
            var window = gameRenderState.windowRenderState;
            salts$updateScreenSize(window.width, window.height, tracker);
            salts$changedScreenSize = false;
        }
    }

    @Unique private void salts$updateScreenSize(int width, int height, DeltaTracker tracker) {
        var options = gameRenderState.optionsRenderState;
        globalSettingsUniform.update(width, height, options.glintStrength,
                minecraft.level == null ? 0 : minecraft.level.getGameTime(), tracker,
                options.menuBackgroundBlurriness, gameRenderState.levelRenderState.cameraRenderState.pos,
                options.textureFiltering == TextureFilteringMethod.RGSS);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void salts$resolveBeforeHand(CallbackInfo info) { SaltsVulkanModClient.beginHand(); }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V", shift = At.Shift.AFTER))
    private void salts$resolveHand(CallbackInfo info) { SaltsVulkanModClient.endHand(); }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void salts$resolveWorld(CallbackInfo info) { SaltsVulkanModClient.afterWorld(); SaltsVulkanModClient.endHand(); }

    @Inject(method = "render", at = @At("TAIL"))
    private void salts$metrics(DeltaTracker tracker, boolean renderLevel, CallbackInfo info) {
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null && renderLevel && minecraft.level != null)
            runtime.recordRenderedFrame(minecraft.getFrameTimeNs(), minecraft.getFps());
    }
}
