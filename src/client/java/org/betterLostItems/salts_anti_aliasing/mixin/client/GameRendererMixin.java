package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
     * 1.21.10-1.21.11 projection hook. The 1.21.8 descriptor is below; exactly one of
     * these {@code @ModifyArg} hooks should apply in any supported modern jar.
     */
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            ),
            index = 5,
            require = 0
    )
    private Matrix4f saltsAntiAliasing$configureTemporalProjection_1_21_10(Matrix4f projectionMatrix) {
        return ModernMinecraftHooks.jitterProjection(projectionMatrix);
    }

    /**
     * 1.21.8-1.21.9 projection hook. This signature has one fewer matrix argument than
     * 1.21.10+, but the projection matrix is still the second matrix argument.
     *
     * <p>The invoke target is deliberately written in intermediary names and marked
     * {@code remap = false}. The current compile target is 1.21.11, so Loom cannot
     * resolve this older invoke descriptor from the named compile classpath. The
     * production jar runs in intermediary namespace, which makes this selector land
     * correctly on the older modern runtimes without adding runtime version checks.</p>
     */
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_761;method_22710(Lnet/minecraft/class_9922;Lnet/minecraft/class_9779;ZLnet/minecraft/class_4184;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    remap = false
            ),
            index = 5,
            require = 0
    )
    private Matrix4f saltsAntiAliasing$configureTemporalProjection_1_21_8(Matrix4f projectionMatrix) {
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
     * Resolves redirected scene-target work after world rendering returns.
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void saltsAntiAliasing$endSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.endSceneRendering((GameRenderer) (Object) this);
    }

    /**
     * Runs post effects after fog has ended the scene frame and before HUD rendering.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
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
