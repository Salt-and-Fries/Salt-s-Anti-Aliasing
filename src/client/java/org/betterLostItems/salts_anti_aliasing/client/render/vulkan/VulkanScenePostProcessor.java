package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer;
import org.betterLostItems.salts_anti_aliasing.client.render.common.ScenePostProcessor;

import java.util.List;

/**
 * Executes Minecraft post chains against the scene after world rendering and before HUD/menu rendering
 * consumes the main target.
 */
public final class VulkanScenePostProcessor implements ScenePostProcessor {
    private static final Identifier NIS_SHARPEN_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_sharpen");
    private static final Identifier FXAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fxaa");
    private static final Identifier EDGE_DEBUG_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":edge_debug");
    private static final Identifier SMAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":smaa");

    private final CrossFrameResourcePool temporalResourcePool = new CrossFrameResourcePool(3);
    private final CrossFrameResourcePool finalEffectsResourcePool = new CrossFrameResourcePool(3);
    private final EdgeDebugAnalyzer edgeDebugAnalyzer;
    private boolean temporalDisabledAfterFailure;
    private boolean finalEffectsDisabledAfterFailure;

    /**
     * Creates a Vulkan scene post processor instance with the collaborators or initial state
     * supplied by the caller.
     * @param edgeDebugAnalyzer edge debug analyzer value supplied by the caller or Minecraft
     * callback
     */
    public VulkanScenePostProcessor(EdgeDebugAnalyzer edgeDebugAnalyzer) {
        this.edgeDebugAnalyzer = edgeDebugAnalyzer;
    }

    /**
     * Handles apply as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param config configuration object being normalized, copied, or committed
     */
    @Override
    public void applyTemporalResolve(GameRenderer gameRenderer, AntiAliasingConfig config) {
        if (temporalDisabledAfterFailure || config.mode != AntiAliasingMode.TAA) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            VulkanSceneTemporalController.instance().resetForInactiveMode();
            return;
        }

        try {
            VulkanSceneTemporalController.instance().apply(gameRenderer, temporalResourcePool);
        } catch (RuntimeException exception) {
            temporalDisabledAfterFailure = true;
            temporalResourcePool.clear();
            SaltsAntiAliasing.LOGGER.error("Disabling temporal resolve after a rendering failure", exception);
        } finally {
            temporalResourcePool.endFrame();
        }
    }

    @Override
    public void applyFinalEffects(GameRenderer gameRenderer, AntiAliasingConfig config) {
        try {
            if (finalEffectsDisabledAfterFailure) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                VulkanSceneTemporalController.instance().resetForInactiveMode();
                return;
            }

            if (!config.mode.usesHistoryBuffers()) {
                VulkanSceneTemporalController.instance().resetForInactiveMode();
            }

            for (Identifier effectId : antiAliasingEffectsFor(config.mode)) {
                processEffect(minecraft, gameRenderer.mainRenderTarget(), effectId, config);
            }
            boolean nativeFsrSharpeningSucceeded = VulkanSceneFsrController.instance()
                    .consumeNativeSharpeningSucceededThisFrame();
            if (shouldApplyGenericSharpening(
                    config.mode,
                    config.sharpenStrength,
                    nativeFsrSharpeningSucceeded
            )) {
                processEffect(minecraft, gameRenderer.mainRenderTarget(), NIS_SHARPEN_EFFECT, config);
            }

            edgeDebugAnalyzer.captureIfNeeded(gameRenderer.mainRenderTarget(), config);
            if (config.debugViewsEnabled) {
                processEffect(minecraft, gameRenderer.mainRenderTarget(), EDGE_DEBUG_EFFECT, config);
            }
        } catch (RuntimeException exception) {
            finalEffectsDisabledAfterFailure = true;
            finalEffectsResourcePool.clear();
            SaltsAntiAliasing.LOGGER.error("Disabling final scene effects after a rendering failure", exception);
        } finally {
            finalEffectsResourcePool.endFrame();
        }
    }

    /**
     * Handles process effect as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param minecraft minecraft value supplied by the caller or Minecraft callback
     * @param effectId effect id value supplied by the caller or Minecraft callback
     * @param config configuration object being normalized, copied, or committed
     */
    private void processEffect(Minecraft minecraft, RenderTarget mainTarget, Identifier effectId, AntiAliasingConfig config) {
        PostChain postChain = minecraft.getShaderManager().getPostChain(effectId, LevelTargetBundle.MAIN_TARGETS);
        if (postChain == null) {
            return;
        }

        VulkanDynamicUniforms.updateSharpening(postChain, config.sharpenStrength);
        postChain.process(mainTarget, finalEffectsResourcePool);
    }

    /**
     * Handles effect for as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration object being normalized, copied, or committed
     * @return effect for produced by this helper
     */
    private static List<Identifier> antiAliasingEffectsFor(AntiAliasingMode mode) {
        return switch (mode) {
            case FXAA -> List.of(FXAA_EFFECT);
            case SMAA, SMAA_NIS_SHARPEN -> List.of(SMAA_EFFECT);
            default -> List.of();
        };
    }

    /**
     * Chooses the independent fallback sharpen pass. FSR modes skip it only when their native
     * evaluation actually sharpened this frame; failed evaluations resolve linearly and still
     * receive the user's requested sharpening amount.
     */
    static boolean shouldApplyGenericSharpening(
            AntiAliasingMode mode,
            float sharpenStrength,
            boolean nativeFsrSharpeningSucceeded
    ) {
        return sharpenStrength > 0.0f
                && (!mode.usesNativeFsrSharpening() || !nativeFsrSharpeningSucceeded);
    }
}
