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

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"), index = 0)
    private Matrix4f salts$temporalCamera(Matrix4f projection) {
        if (!SaltsVulkanModClient.temporalActive()) return projection;
        var position = mainCamera.getPosition();
        var view = new Matrix4f().rotation(mainCamera.rotation().conjugate(new org.joml.Quaternionf()));
        var temporal = SaltsVulkanModClient.temporal();
        temporal.depthRange(.05f, getDepthFar());
        return projection.set(temporal.capture(projection, view, position.x, position.y, position.z,
                mainCamera.getXRot(), mainCamera.getYRot(), 2f * (float) Math.atan(1f / Math.abs(projection.m11()))));
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void salts$resolveBeforeDepthClear(CallbackInfo info) { SaltsVulkanModClient.afterWorld(); }

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
