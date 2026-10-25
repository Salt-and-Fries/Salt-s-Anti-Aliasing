package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements game renderer mixin behavior for Salt's Anti Aliasing. Mixin bridge code that hooks
 * Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "render3dHud",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V", ordinal = 0))
    private void saltsAntiAliasing$mirrorScreenEffects(
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame effects,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        // Water/fire/suffocation overlays change the world too. Match those in the HUD-less
        // input so automatic UI extraction protects only the hand/HUD, not the entire viewport.
        RenderTarget hudless = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController
                .instance().hudlessWorldOverlayTarget();
        try {
            if (hudless != null && !effects.isEmpty()) {
                RenderTarget main = ((GameRenderer) (Object) this).mainRenderTarget();
                // Vanilla's render pass is closed here; its prepared geometry is still alive.
                try (var mirrored = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                        .createRenderPass(() -> "Salt's FG World Screen Effects", hudless.getColorTextureView(),
                                java.util.Optional.empty(), main.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                    com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(mirrored);
                    net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(mirrored, effects);
                }
            }
        } finally {
            original.call(effects);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void saltsAntiAliasing$separateHandFromFrameGeneration(CallbackInfo ci) {
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) runtime.beforeHandRendering((GameRenderer) (Object) this);
    }

    @ModifyArgs(method = "renderItemInHand", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private void saltsAntiAliasing$handAttachments(Args args) {
        RenderTarget hand = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController
                .instance().begin((GameRenderer) (Object) this, args.get(1), args.get(3));
        if (hand != null) {
            args.set(1, hand.getColorTextureView());
            args.set(3, hand.getDepthTextureView());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void saltsAntiAliasing$resolveHand(CallbackInfo ci) {
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController.instance().end();
    }
    private static final String LEVEL_RENDER_TARGET =
            "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V";
    private static final String LEVEL_PROJECTION_BUFFER_TARGET =
            "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;";

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
        RenderTarget overrideTarget = ModernMinecraftHooks.overrideMainRenderTarget();
        if (overrideTarget != null) {
            callbackInfo.setReturnValue(overrideTarget);
        }
    }

    /**
     * Activates the internal scene target before any resolution-dependent world state is prepared.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void saltsAntiAliasing$beginResolutionScopedScene(
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
                    sceneTarget.height
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
    private void saltsAntiAliasing$endSceneRendering(CallbackInfo callbackInfo) {
        if (saltsAntiAliasing$globalScreenSizeOverridden) {
            WindowRenderState windowState = gameRenderState.windowRenderState;
            saltsAntiAliasing$updateGlobalSettings(windowState.width, windowState.height);
            saltsAntiAliasing$globalScreenSizeOverridden = false;
        }
        ModernMinecraftHooks.endSceneRendering((GameRenderer) (Object) this);
    }

    @Unique
    private void saltsAntiAliasing$updateGlobalSettings(
            int width,
            int height
    ) {
        OptionsRenderState optionsState = gameRenderState.optionsRenderState;
        long gameTime = gameRenderState.levelRenderState.gameTime;
        globalSettingsUniform.update(
                width,
                height,
                optionsState.glintStrength,
                gameTime,
                gameRenderState.levelRenderState.worldPartialTicks,
                optionsState.menuBackgroundBlurriness,
                gameRenderState.levelRenderState.cameraRenderState.pos,
                optionsState.textureFiltering == TextureFilteringMethod.RGSS
        );
    }

    /**
     * Runs post effects after fog has ended the scene frame and before HUD rendering.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void saltsAntiAliasing$applySceneOnlyPostProcessing(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.applyScenePostProcessing((GameRenderer) (Object) this);
    }

    /**
     * Samples frame timing at the end of a rendered world frame.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void saltsAntiAliasing$recordPerformanceMetrics(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.recordRenderedFrame((GameRenderer) (Object) this,
                gameRenderState.shouldRenderLevel);
    }
}
