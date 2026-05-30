package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import net.minecraft.client.renderer.PostChain;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

/**
 * Writes per-frame and per-config uniform values consumed by old-format post-processing shaders.
 */
final class OpenGlDynamicUniforms {
    private static final float NIS_EDGE_BOOST = 1.1f;
    private static final float NIS_CLAMP_BOOST = 0.18f;
    private static final float RCAS_EDGE_LIMIT = 0.22f;
    private static final float RCAS_CLAMP_BOOST = 0.12f;

    private OpenGlDynamicUniforms() {
    }

    static void updateForMode(PostChain postChain, AntiAliasingConfig config) {
        if (config.mode == AntiAliasingMode.NIS_SHARPEN) {
            writeNisSharpenUniform(postChain, config.sharpenStrength);
        } else if (config.mode == AntiAliasingMode.NIS_UPSCALE) {
            writeNisSharpenUniform(postChain, config.nisUpscaleQualityPreset.sharpenStrength());
        } else if (config.mode == AntiAliasingMode.FSR1_UPSCALE) {
            writeFsr1Uniform(postChain, config.sceneRenderScale());
        } else if (config.mode == AntiAliasingMode.FSR1_RCAS) {
            writeRcasUniform(postChain, config.sharpenStrength);
        }
    }

    static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller) {
        postChain.setUniform("BaseHistoryWeight", controller.baseHistoryWeight());
        postChain.setUniform("LumaRejection", controller.lumaRejection());
        postChain.setUniform("DepthRejection", controller.depthRejection());
        postChain.setUniform("NeighborhoodClamp", controller.neighborhoodClamp());
        postChain.setUniform("CurrentJitterX", controller.currentJitterTexelX());
        postChain.setUniform("CurrentJitterY", controller.currentJitterTexelY());
        postChain.setUniform("PreviousJitterX", controller.previousJitterTexelX());
        postChain.setUniform("PreviousJitterY", controller.previousJitterTexelY());
        postChain.setUniform("CameraMotion", controller.cameraMotionAmount());
    }

    private static void writeNisSharpenUniform(PostChain postChain, float sharpenStrength) {
        postChain.setUniform("Sharpness", sharpenStrength);
        postChain.setUniform("EdgeBoost", NIS_EDGE_BOOST);
        postChain.setUniform("ClampBoost", NIS_CLAMP_BOOST);
    }

    private static void writeRcasUniform(PostChain postChain, float sharpenStrength) {
        postChain.setUniform("Sharpness", sharpenStrength);
        postChain.setUniform("EdgeLimit", RCAS_EDGE_LIMIT);
        postChain.setUniform("ClampBoost", RCAS_CLAMP_BOOST);
    }

    private static void writeFsr1Uniform(PostChain postChain, float sourceScale) {
        postChain.setUniform("SourceScale", sourceScale);
        postChain.setUniform("EdgeBlend", 0.42f);
        postChain.setUniform("DetailBoost", 0.28f);
        postChain.setUniform("ClampBoost", 0.18f);
    }
}
