package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer;
import org.betterLostItems.salts_anti_aliasing.client.render.common.ScenePostProcessor;
import org.betterLostItems.salts_anti_aliasing.mixin.client.PostChainAccessor;
import org.betterLostItems.salts_anti_aliasing.mixin.client.PostPassAccessor;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public final class OpenGlScenePostProcessor implements ScenePostProcessor {
    private static final Identifier NIS_SHARPEN_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_sharpen");
    private static final Identifier NIS_UPSCALE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_upscale");
    private static final Identifier FSR1_QUALITY_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_quality");
    private static final Identifier FSR1_BALANCED_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_balanced");
    private static final Identifier FSR1_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_performance");
    private static final Identifier FSR1_ULTRA_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_ultra_performance");
    private static final Identifier FSR1_RCAS_QUALITY_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_quality");
    private static final Identifier FSR1_RCAS_BALANCED_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_balanced");
    private static final Identifier FSR1_RCAS_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_performance");
    private static final Identifier FSR1_RCAS_ULTRA_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_ultra_performance");
    private static final Identifier FXAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fxaa");
    private static final Identifier EDGE_DEBUG_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":edge_debug");
    private static final Identifier SMAA_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":smaa");
    private static final String FXAA_UNIFORM = "FxaaConfig";
    private static final String NIS_SHARPEN_UNIFORM = "NisSharpenConfig";
    private static final String RCAS_UNIFORM = "RcasConfig";
    private static final String TAA_UNIFORM = "TaaConfig";
    private static final float FXAA_SUBPIXEL_BLEND = 0.92f;
    private static final float FXAA_EDGE_THRESHOLD = 0.085f;
    private static final float FXAA_EDGE_THRESHOLD_MIN = 0.018f;
    private static final float FXAA_SEARCH_RADIUS = 3.6f;
    private static final float FXAA_COLOR_WEIGHT = 0.85f;
    private static final float FXAA_DEPTH_WEIGHT = 2.35f;
    private static final float FXAA_VARIANCE_GUARD = 0.72f;
    private static final float FXAA_EDGE_CONFIDENCE = 1.18f;
    private static final float NIS_EDGE_BOOST = 1.1f;
    private static final float NIS_CLAMP_BOOST = 0.18f;
    private static final float RCAS_EDGE_LIMIT = 0.22f;
    private static final float RCAS_CLAMP_BOOST = 0.12f;

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
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
                OpenGlSceneTemporalController.instance().apply(gameRenderer, resourcePool);
            } else {
                OpenGlSceneTemporalController.instance().resetForInactiveMode();
                Identifier effectId = effectFor(config);
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
            resourcePool.clear();
            SaltsAntiAliasing.LOGGER.error("Disabling OpenGL scene post-processing after a rendering failure", exception);
        } finally {
            resourcePool.endFrame();
        }
    }

    private void processEffect(Minecraft minecraft, Identifier effectId, AntiAliasingConfig config) {
        PostChain postChain = minecraft.getShaderManager().getPostChain(effectId, LevelTargetBundle.MAIN_TARGETS);
        if (postChain == null) {
            return;
        }

        updateDynamicUniforms(postChain, config);
        postChain.process(minecraft.getMainRenderTarget(), resourcePool);
    }

    private static Identifier effectFor(AntiAliasingConfig config) {
        return switch (config.mode) {
            case NIS_SHARPEN -> NIS_SHARPEN_EFFECT;
            case NIS_UPSCALE -> NIS_UPSCALE_EFFECT;
            case FSR1_UPSCALE -> fsr1EffectFor(config.nisUpscaleQualityPreset);
            case FSR1_RCAS -> fsr1RcasEffectFor(config.nisUpscaleQualityPreset);
            case FXAA -> FXAA_EFFECT;
            case SMAA -> SMAA_EFFECT;
            default -> null;
        };
    }

    private static Identifier fsr1EffectFor(NisUpscaleQualityPreset preset) {
        return switch (NisUpscaleQualityPreset.clamp(preset)) {
            case QUALITY -> FSR1_QUALITY_EFFECT;
            case BALANCED -> FSR1_BALANCED_EFFECT;
            case PERFORMANCE -> FSR1_PERFORMANCE_EFFECT;
            case ULTRA_PERFORMANCE -> FSR1_ULTRA_PERFORMANCE_EFFECT;
        };
    }

    private static Identifier fsr1RcasEffectFor(NisUpscaleQualityPreset preset) {
        return switch (NisUpscaleQualityPreset.clamp(preset)) {
            case QUALITY -> FSR1_RCAS_QUALITY_EFFECT;
            case BALANCED -> FSR1_RCAS_BALANCED_EFFECT;
            case PERFORMANCE -> FSR1_RCAS_PERFORMANCE_EFFECT;
            case ULTRA_PERFORMANCE -> FSR1_RCAS_ULTRA_PERFORMANCE_EFFECT;
        };
    }

    private static void updateDynamicUniforms(PostChain postChain, AntiAliasingConfig config) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            if (config.mode == AntiAliasingMode.NIS_SHARPEN) {
                GpuBuffer uniformBuffer = customUniforms.get(NIS_SHARPEN_UNIFORM);
                if (uniformBuffer != null) {
                    writeNisSharpenUniform(customUniforms, uniformBuffer, config.sharpenStrength);
                }
            }

            if (config.mode == AntiAliasingMode.FSR1_RCAS) {
                GpuBuffer uniformBuffer = customUniforms.get(RCAS_UNIFORM);
                if (uniformBuffer != null) {
                    writeRcasUniform(customUniforms, uniformBuffer, config.sharpenStrength);
                }
            }

            if (config.mode == AntiAliasingMode.TAA) {
                GpuBuffer uniformBuffer = customUniforms.get(TAA_UNIFORM);
                if (uniformBuffer != null) {
                    writeTaaUniform(customUniforms, uniformBuffer, OpenGlSceneTemporalController.instance());
                }
            }
        }
    }

    private static void writeNisSharpenUniform(Map<String, GpuBuffer> customUniforms, GpuBuffer uniformBuffer, float sharpenStrength) {
        replaceUniformBuffer(customUniforms, NIS_SHARPEN_UNIFORM, uniformBuffer, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(NIS_EDGE_BOOST);
            bufferData.putFloat(NIS_CLAMP_BOOST);
        });
    }

    private static void writeRcasUniform(Map<String, GpuBuffer> customUniforms, GpuBuffer uniformBuffer, float sharpenStrength) {
        replaceUniformBuffer(customUniforms, RCAS_UNIFORM, uniformBuffer, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(RCAS_EDGE_LIMIT);
            bufferData.putFloat(RCAS_CLAMP_BOOST);
        });
    }

    private static void writeTaaUniform(Map<String, GpuBuffer> customUniforms, GpuBuffer uniformBuffer, OpenGlSceneTemporalController controller) {
        replaceUniformBuffer(customUniforms, TAA_UNIFORM, uniformBuffer, bufferData -> {
            bufferData.putFloat(controller.baseHistoryWeight());
            bufferData.putFloat(controller.lumaRejection());
            bufferData.putFloat(controller.depthRejection());
            bufferData.putFloat(controller.neighborhoodClamp());
            bufferData.putFloat(controller.currentJitterTexelX());
            bufferData.putFloat(controller.currentJitterTexelY());
            bufferData.putFloat(controller.previousJitterTexelX());
            bufferData.putFloat(controller.previousJitterTexelY());
            bufferData.putFloat(controller.cameraMotionAmount());
        });
    }

    private static void replaceUniformBuffer(
            Map<String, GpuBuffer> customUniforms,
            String uniformName,
            GpuBuffer previousBuffer,
            UniformWriter writer
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer bufferData = stack.malloc((int) previousBuffer.size()).order(ByteOrder.nativeOrder());
            writer.write(bufferData);
            while (bufferData.hasRemaining()) {
                bufferData.put((byte) 0);
            }
            bufferData.flip();
            GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                    () -> "Salt's Anti Aliasing " + uniformName,
                    GpuBuffer.USAGE_UNIFORM,
                    bufferData
            );
            GpuBuffer old = customUniforms.put(uniformName, replacement);
            if (old != null) {
                old.close();
            }
        }
    }

    @FunctionalInterface
    private interface UniformWriter {
        void write(ByteBuffer bufferData);
    }
}
