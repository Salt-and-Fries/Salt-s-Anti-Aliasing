package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements game renderer mixin behavior for Salt's Anti Aliasing. Mixin bridge code that hooks
 * Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique private RenderTarget saltsAntiAliasing$screenEffectTarget;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void saltsAntiAliasing$beginHandRendering(CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) runtime.beforeHandRendering(renderer);
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController.instance().beginHandRendering(renderer);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void saltsAntiAliasing$resolveHand(CallbackInfo ci) {
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController.instance().end();
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void saltsAntiAliasing$mirrorScreenEffects(
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher,
            net.minecraft.client.renderer.SubmitNodeStorage storage,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        RenderTarget hudless = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController
                .instance().hudlessWorldOverlayTarget();
        if (hudless == null) { original.call(dispatcher, storage); return; }
        try (var frame = dispatcher.prepareFrame(storage)) {
            frame.executeSolid(); frame.executeTranslucent();
            frame.executeTranslucentAfterTerrain(); frame.executeAlwaysOnTop();
            RenderTarget main = ((GameRenderer) (Object) this).mainRenderTarget();
            // Borrow the HUD-less color and the output depth; vanilla owns both attachments.
            saltsAntiAliasing$screenEffectTarget = new RenderTarget("Salt's FG World Screen Effects", true,
                    hudless.getColorTexture().getFormat()) {{
                width = hudless.width; height = hudless.height;
                colorTexture = hudless.getColorTexture(); colorTextureView = hudless.getColorTextureView();
                depthTexture = main.getDepthTexture(); depthTextureView = main.getDepthTextureView();
            }};
            try {
                frame.executeSolid(); frame.executeTranslucent();
                frame.executeTranslucentAfterTerrain(); frame.executeAlwaysOnTop();
            } finally { saltsAntiAliasing$screenEffectTarget = null; }
        }
    }

    private static final String LEVEL_RENDER_TARGET =
            "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";
    private static final String LEVEL_PROJECTION_BUFFER_TARGET =
            "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;";

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Shadow
    @Final
    private GlobalSettingsUniform globalSettingsUniform;

    @Unique
    private boolean saltsAntiAliasing$globalScreenSizeOverridden;

    /**
     * Lets internal-resolution modes temporarily replace Minecraft's main render target.
     */
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> callbackInfo) {
        RenderTarget overrideTarget = saltsAntiAliasing$screenEffectTarget != null
                ? saltsAntiAliasing$screenEffectTarget : ModernMinecraftHooks.overrideMainRenderTarget();
        if (overrideTarget != null) {
            callbackInfo.setReturnValue(overrideTarget);
        }
    }

    /**
     * Activates the internal scene target before any resolution-dependent world state is prepared.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void saltsAntiAliasing$beginResolutionScopedScene(
            DeltaTracker deltaTracker,
            CallbackInfo callbackInfo
    ) {
        GameRenderer gameRenderer = (GameRenderer) (Object) this;
        saltsAntiAliasing$globalScreenSizeOverridden = false;
        ModernMinecraftHooks.beginSceneRendering(gameRenderer);

        RenderTarget sceneTarget = gameRenderer.mainRenderTarget();
        WindowRenderState windowState = gameRenderState.windowRenderState;
        if (sceneTarget.width != windowState.width || sceneTarget.height != windowState.height) {
            saltsAntiAliasing$updateGlobalSettings(
                    sceneTarget.width,
                    sceneTarget.height,
                    deltaTracker
            );
            saltsAntiAliasing$globalScreenSizeOverridden = true;
        }

        ModernMinecraftHooks.prepareTemporalJitter(gameRenderer);
    }

    /**
     * Jitters the final world projection after vanilla has applied hurt, view-bob, and nausea
     * transforms, while leaving the shared camera state and culling projection untouched.
     */
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = LEVEL_PROJECTION_BUFFER_TARGET),
            index = 0
    )
    private Matrix4f saltsAntiAliasing$configureTemporalProjection(Matrix4f projectionMatrix) {
        return ModernMinecraftHooks.configureTemporalProjection(
                projectionMatrix,
                gameRenderState.levelRenderState.cameraRenderState
        );
    }

    /**
     * Resolves redirected scene-target work after world rendering returns.
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = LEVEL_RENDER_TARGET,
                    shift = At.Shift.AFTER
            )
    )
    private void saltsAntiAliasing$endSceneRendering(DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        if (saltsAntiAliasing$globalScreenSizeOverridden) {
            WindowRenderState windowState = gameRenderState.windowRenderState;
            saltsAntiAliasing$updateGlobalSettings(windowState.width, windowState.height, deltaTracker);
            saltsAntiAliasing$globalScreenSizeOverridden = false;
        }
        ModernMinecraftHooks.endSceneRendering((GameRenderer) (Object) this);
    }

    @Unique
    private void saltsAntiAliasing$updateGlobalSettings(
            int width,
            int height,
            DeltaTracker deltaTracker
    ) {
        OptionsRenderState optionsState = gameRenderState.optionsRenderState;
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        globalSettingsUniform.update(
                width,
                height,
                optionsState.glintStrength,
                gameTime,
                deltaTracker,
                optionsState.menuBackgroundBlurriness,
                gameRenderState.levelRenderState.cameraRenderState.pos,
                optionsState.textureFiltering == TextureFilteringMethod.RGSS
        );
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
