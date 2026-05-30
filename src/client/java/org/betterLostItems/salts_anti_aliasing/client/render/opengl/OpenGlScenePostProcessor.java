package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer;
import org.betterLostItems.salts_anti_aliasing.client.render.common.ScenePostProcessor;

/**
 * Applies 1.21.1 old-format post chains to the rendered scene.
 */
public final class OpenGlScenePostProcessor implements ScenePostProcessor {
    private static final ResourceLocation NIS_SHARPEN_EFFECT = id("nis_sharpen");
    private static final ResourceLocation FXAA_EFFECT = id("fxaa");
    private static final ResourceLocation EDGE_DEBUG_EFFECT = id("edge_debug");
    private static final ResourceLocation SMAA_EFFECT = id("smaa");

    private final EdgeDebugAnalyzer edgeDebugAnalyzer;
    private boolean disabledAfterFailure;

    public OpenGlScenePostProcessor(EdgeDebugAnalyzer edgeDebugAnalyzer) {
        this.edgeDebugAnalyzer = edgeDebugAnalyzer;
    }

    @Override
    public void apply(GameRenderer gameRenderer, AntiAliasingConfig config) {
        try {
            if (disabledAfterFailure) {
                return;
            }

            Minecraft minecraft = gameRenderer.getMinecraft();
            if (minecraft.level == null) {
                OpenGlSceneTemporalController.instance().resetForInactiveMode();
                return;
            }

            if (config.mode == AntiAliasingMode.TAA) {
                OpenGlSceneTemporalController.instance().apply(gameRenderer);
            } else {
                OpenGlSceneTemporalController.instance().resetForInactiveMode();
                ResourceLocation effectId = effectFor(config);
                if (effectId != null) {
                    processEffect(minecraft, effectId, config);
                }
            }

            edgeDebugAnalyzer.captureIfNeeded(minecraft.getMainRenderTarget(), config);
            if (config.debugViewsEnabled) {
                processEffect(minecraft, EDGE_DEBUG_EFFECT, config);
            }
        } catch (RuntimeException exception) {
            disabledAfterFailure = true;
            OpenGlPostChainManager.closeAll();
            SaltsAntiAliasing.LOGGER.error("Disabling OpenGL scene post-processing after a rendering failure", exception);
        }
    }

    private void processEffect(Minecraft minecraft, ResourceLocation effectId, AntiAliasingConfig config) {
        try {
            PostChain postChain = OpenGlPostChainManager.get(minecraft, effectId);
            OpenGlDynamicUniforms.updateForMode(postChain, config);
            postChain.process(minecraft.getTimer().getGameTimeDeltaTicks());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to process post effect " + effectId, exception);
        }
    }

    private static ResourceLocation effectFor(AntiAliasingConfig config) {
        return switch (config.mode) {
            case NIS_SHARPEN -> NIS_SHARPEN_EFFECT;
            case FXAA -> FXAA_EFFECT;
            case SMAA -> SMAA_EFFECT;
            default -> null;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SaltsAntiAliasing.MOD_ID, path);
    }
}
