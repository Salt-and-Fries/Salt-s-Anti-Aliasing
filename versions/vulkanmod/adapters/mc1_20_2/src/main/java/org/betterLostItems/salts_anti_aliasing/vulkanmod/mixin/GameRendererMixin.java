package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private Camera mainCamera;
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract float getDepthFar();

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void salts$beginWorld(CallbackInfo info) { SaltsVulkanModClient.beginWorld(); }

    @Shadow public abstract void resetProjectionMatrix(Matrix4f projection);

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void salts$world(net.minecraft.client.renderer.LevelRenderer renderer, com.mojang.blaze3d.vertex.PoseStack pose,
            float tick, long deadline, boolean outline, Camera camera, GameRenderer gameRenderer,
            net.minecraft.client.renderer.LightTexture light, Matrix4f projection,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (SaltsVulkanModClient.temporalActive()) {
            var position = camera.getPosition();
            var temporal = SaltsVulkanModClient.temporal();
            temporal.depthRange(.05f, getDepthFar());
            // Camera.setup runs after the old projection upload. Capture at the world
            // call so camera history and the rendered pose belong to the same frame.
            var view = pose.last().pose();
            org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.WorldViewSpace.capture(view);
            projection.set(temporal.capture(projection, view, position.x, position.y, position.z,
                    camera.getXRot(), camera.getYRot(), 2f * (float) Math.atan(1f / Math.abs(projection.m11()))));
            resetProjectionMatrix(projection);
        }
        try { original.call(renderer, pose, tick, deadline, outline, camera, gameRenderer, light, projection); }
        finally { SaltsVulkanModClient.afterWorld(); }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void salts$beginHand(CallbackInfo info) { SaltsVulkanModClient.beginHand(); }
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void salts$resolveHand(CallbackInfo info) { SaltsVulkanModClient.endHand(); }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void salts$finish(CallbackInfo info) { SaltsVulkanModClient.afterWorld(); SaltsVulkanModClient.endHand(); }

    @Inject(method = "render", at = @At("TAIL"))
    private void salts$metrics(float tick, long frameTime, boolean world, CallbackInfo info) {
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null && world && minecraft.level != null) runtime.recordRenderedFrame(minecraft.getFrameTimeNs(), minecraft.getFps());
    }
}
