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

/**
 * Executes Minecraft post chains against the scene after world rendering and before HUD/menu rendering
 * consumes the main target.
 */
public final class VulkanScenePostProcessor implements ScenePostProcessor {
    private static final Identifier NIS_SHARPEN_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_sharpen");
    private static final Identifier FXAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fxaa");
    private static final Identifier EDGE_DEBUG_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":edge_debug");
    private static final Identifier SMAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":smaa");

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private final EdgeDebugAnalyzer edgeDebugAnalyzer;
    private boolean disabledAfterFailure;

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
    public void apply(GameRenderer gameRenderer, AntiAliasingConfig config) {
        try {
            if (disabledAfterFailure) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                VulkanSceneTemporalController.instance().resetForInactiveMode();
                return;
            }

            if (config.mode == AntiAliasingMode.TAA) {
                VulkanSceneTemporalController.instance().apply(gameRenderer, resourcePool);
            } else if (config.mode.usesHistoryBuffers()) {
                Identifier effectId = effectFor(config);
                if (effectId != null) {
                    processEffect(minecraft, gameRenderer.mainRenderTarget(), effectId, config);
                }
            } else {
                VulkanSceneTemporalController.instance().resetForInactiveMode();
                Identifier effectId = effectFor(config);
                if (effectId != null) {
                    processEffect(minecraft, gameRenderer.mainRenderTarget(), effectId, config);
                }
            }

            edgeDebugAnalyzer.captureIfNeeded(gameRenderer.mainRenderTarget(), config);
            if (config.debugViewsEnabled) {
                processEffect(minecraft, gameRenderer.mainRenderTarget(), EDGE_DEBUG_EFFECT, config);
            }
        } catch (RuntimeException exception) {
            disabledAfterFailure = true;
            resourcePool.clear();
            SaltsAntiAliasing.LOGGER.error("Disabling scene post-processing after a rendering failure", exception);
        } finally {
            resourcePool.endFrame();
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

        VulkanDynamicUniforms.updateForMode(postChain, config);
        postChain.process(mainTarget, resourcePool);
    }

    /**
     * Handles effect for as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration object being normalized, copied, or committed
     * @return effect for produced by this helper
     */
    private static Identifier effectFor(AntiAliasingConfig config) {
        return switch (config.mode) {
            case NIS_SHARPEN -> NIS_SHARPEN_EFFECT;
            case FXAA -> FXAA_EFFECT;
            case SMAA -> SMAA_EFFECT;
            default -> null;
        };
    }
}
