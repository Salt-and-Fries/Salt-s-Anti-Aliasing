package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Documents game renderer mixin behavior for Salt's Anti Aliasing. Mixin bridge code for carefully
 * scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /**
     * Computes temporal jitter before the projection matrix is consumed by the level renderer.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void saltsAntiAliasing$prepareTemporalJitter(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.prepareTemporalJitter((GameRenderer) (Object) this);
    }

    /**
     * Applies jitter to the 1.21.1 projection matrix passed into LevelRenderer.
     */
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
            ),
            index = 6
    )
    private Matrix4f saltsAntiAliasing$configureTemporalProjection(Matrix4f projectionMatrix) {
        return ModernMinecraftHooks.jitterProjection(projectionMatrix);
    }

    /**
     * Starts scene-target redirection before world rendering.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void saltsAntiAliasing$beginSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.beginSceneRendering((GameRenderer) (Object) this);
    }

    /**
     * Restores Minecraft's native main target before the first-person hand pass.
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void saltsAntiAliasing$finishScaledSceneBeforeHand(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.finishScaledSceneRendering((GameRenderer) (Object) this);
    }

    /**
     * Resolves redirected scene-target work after world rendering returns.
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void saltsAntiAliasing$endSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.endSceneRendering((GameRenderer) (Object) this);
    }

    /**
     * Runs post effects after the world and vanilla world effects, before HUD rendering.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V",
                    ordinal = 0
            )
    )
    private void saltsAntiAliasing$applySceneOnlyPostProcessing(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.applyScenePostProcessing((GameRenderer) (Object) this);
    }

    /**
     * Samples frame timing at the end of a rendered world frame.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void saltsAntiAliasing$recordPerformanceMetrics(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.recordRenderedFrame((GameRenderer) (Object) this, renderLevel);
    }
}
