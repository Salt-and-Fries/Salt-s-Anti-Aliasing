package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.mixin.client.PostChainAccessor;
import org.betterLostItems.salts_anti_aliasing.mixin.client.PostPassAccessor;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;

final class OpenGlDynamicUniforms {
    private static final String NIS_SHARPEN_UNIFORM = "NisSharpenConfig";
    private static final String RCAS_UNIFORM = "RcasConfig";
    private static final String TAA_UNIFORM = "TaaConfig";
    private static final String FXAA_UNIFORM = "FxaaConfig";
    private static final String SMAA_EDGE_UNIFORM = "SmaaEdgeConfig";
    private static final String FSR1_UNIFORM = "Fsr1Config";
    private static final String EDGE_MASK_UNIFORM = "EdgeMaskConfig";
    private static final float NIS_EDGE_BOOST = 1.1f;
    private static final float NIS_CLAMP_BOOST = 0.18f;
    private static final float RCAS_EDGE_LIMIT = 0.22f;
    private static final float RCAS_CLAMP_BOOST = 0.12f;
    private static final float FXAA_SUBPIXEL_BLEND = 1.35f;
    private static final float FXAA_EDGE_THRESHOLD = 0.038f;
    private static final float FXAA_EDGE_THRESHOLD_MIN = 0.006f;
    private static final float FXAA_SEARCH_RADIUS = 6.0f;
    private static final float FXAA_COLOR_WEIGHT = 1.25f;
    private static final float FXAA_DEPTH_WEIGHT = 3.25f;
    private static final float FXAA_VARIANCE_GUARD = 0.26f;
    private static final float FXAA_EDGE_CONFIDENCE = 1.85f;
    private static final float SMAA_EDGE_THRESHOLD = 0.065f;
    private static final float SMAA_LOCAL_CONTRAST_FACTOR = 1.35f;
    private static final float SMAA_DIAGONAL_FACTOR = 0.35f;
    private static final int WRITABLE_UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    private static final Map<GpuBuffer, Integer> LAST_UPLOADED_HASHES = new IdentityHashMap<>();

    private OpenGlDynamicUniforms() {
    }

    static void updateForMode(PostChain postChain, AntiAliasingConfig config) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            writeFxaaUniform(customUniforms, config.avoidAaOnTextures);
            writeSmaaEdgeUniform(customUniforms, config.avoidAaOnTextures);
            writeFsr1Uniform(customUniforms, config);
            writeEdgeMaskUniform(customUniforms, config);
            if (config.mode == AntiAliasingMode.NIS_SHARPEN) {
                writeNisSharpenUniform(customUniforms, config.sharpenStrength, config.avoidAaOnTextures);
            } else if (config.mode == AntiAliasingMode.NIS_UPSCALE) {
                writeNisSharpenUniform(
                        customUniforms,
                        config.nisUpscaleQualityPreset.sharpenStrength(),
                        config.avoidAaOnTextures
                );
            } else if (config.mode == AntiAliasingMode.FSR1_RCAS) {
                writeRcasUniform(customUniforms, config.sharpenStrength, config.avoidAaOnTextures);
            }
        }
    }

    static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller) {
        updateTaa(postChain, controller, false);
    }

    static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller, AntiAliasingConfig config) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            writeTaaUniform(customUniforms, controller, config.avoidAaOnTextures);
            writeEdgeMaskUniform(customUniforms, config);
        }
    }

    private static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller, boolean avoidTextures) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            writeTaaUniform(customUniforms, controller, avoidTextures);
        }
    }

    private static void writeFxaaUniform(Map<String, GpuBuffer> customUniforms, boolean avoidTextures) {
        updateUniformBuffer(customUniforms, FXAA_UNIFORM, bufferData -> {
            bufferData.putFloat(FXAA_SUBPIXEL_BLEND);
            bufferData.putFloat(FXAA_EDGE_THRESHOLD);
            bufferData.putFloat(FXAA_EDGE_THRESHOLD_MIN);
            bufferData.putFloat(FXAA_SEARCH_RADIUS);
            bufferData.putFloat(FXAA_COLOR_WEIGHT);
            bufferData.putFloat(FXAA_DEPTH_WEIGHT);
            bufferData.putFloat(FXAA_VARIANCE_GUARD);
            bufferData.putFloat(FXAA_EDGE_CONFIDENCE);
            bufferData.putFloat(avoidTextures ? 1.0f : 0.0f);
        });
    }

    private static void writeSmaaEdgeUniform(Map<String, GpuBuffer> customUniforms, boolean avoidTextures) {
        updateUniformBuffer(customUniforms, SMAA_EDGE_UNIFORM, bufferData -> {
            bufferData.putFloat(SMAA_EDGE_THRESHOLD);
            bufferData.putFloat(SMAA_LOCAL_CONTRAST_FACTOR);
            bufferData.putFloat(SMAA_DIAGONAL_FACTOR);
            bufferData.putFloat(avoidTextures ? 1.0f : 0.0f);
        });
    }

    private static void writeFsr1Uniform(Map<String, GpuBuffer> customUniforms, AntiAliasingConfig config) {
        updateUniformBuffer(customUniforms, FSR1_UNIFORM, bufferData -> {
            float sourceScale = config.sceneRenderScale();
            bufferData.putFloat(sourceScale);
            bufferData.putFloat(edgeBlendForScale(sourceScale));
            bufferData.putFloat(detailBoostForScale(sourceScale));
            bufferData.putFloat(clampBoostForScale(sourceScale));
            bufferData.putFloat(config.avoidAaOnTextures ? 1.0f : 0.0f);
        });
    }

    private static void writeEdgeMaskUniform(Map<String, GpuBuffer> customUniforms, AntiAliasingConfig config) {
        updateUniformBuffer(customUniforms, EDGE_MASK_UNIFORM, bufferData -> {
            bufferData.putFloat(config.accountForBlockBoundaries ? 1.0f : 0.0f);
            bufferData.putFloat(0.0000008f);
            bufferData.putFloat(0.00008f);
            bufferData.putFloat(0.06f);
        });
    }

    private static void writeNisSharpenUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength, boolean avoidTextures) {
        updateUniformBuffer(customUniforms, NIS_SHARPEN_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(NIS_EDGE_BOOST);
            bufferData.putFloat(NIS_CLAMP_BOOST);
            bufferData.putFloat(avoidTextures ? 1.0f : 0.0f);
        });
    }

    private static void writeRcasUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength, boolean avoidTextures) {
        updateUniformBuffer(customUniforms, RCAS_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(RCAS_EDGE_LIMIT);
            bufferData.putFloat(RCAS_CLAMP_BOOST);
            bufferData.putFloat(avoidTextures ? 1.0f : 0.0f);
        });
    }

    private static void writeTaaUniform(
            Map<String, GpuBuffer> customUniforms,
            OpenGlSceneTemporalController controller,
            boolean avoidTextures
    ) {
        updateUniformBuffer(customUniforms, TAA_UNIFORM, bufferData -> {
            bufferData.putFloat(controller.baseHistoryWeight());
            bufferData.putFloat(controller.lumaRejection());
            bufferData.putFloat(controller.depthRejection());
            bufferData.putFloat(controller.neighborhoodClamp());
            bufferData.putFloat(controller.currentJitterTexelX());
            bufferData.putFloat(controller.currentJitterTexelY());
            bufferData.putFloat(controller.previousJitterTexelX());
            bufferData.putFloat(controller.previousJitterTexelY());
            bufferData.putFloat(controller.cameraMotionAmount());
            bufferData.putFloat(avoidTextures ? 1.0f : 0.0f);
        });
    }

    private static float edgeBlendForScale(float scale) {
        if (scale <= 0.50f) {
            return 0.58f;
        }
        if (scale <= 0.59f) {
            return 0.48f;
        }
        if (scale <= 0.67f) {
            return 0.38f;
        }
        return 0.30f;
    }

    private static float detailBoostForScale(float scale) {
        if (scale <= 0.50f) {
            return 0.22f;
        }
        if (scale <= 0.59f) {
            return 0.18f;
        }
        if (scale <= 0.67f) {
            return 0.14f;
        }
        return 0.10f;
    }

    private static float clampBoostForScale(float scale) {
        if (scale <= 0.50f) {
            return 0.16f;
        }
        if (scale <= 0.59f) {
            return 0.14f;
        }
        if (scale <= 0.67f) {
            return 0.12f;
        }
        return 0.10f;
    }

    private static void updateUniformBuffer(
            Map<String, GpuBuffer> customUniforms,
            String uniformName,
            UniformWriter writer
    ) {
        GpuBuffer previousBuffer = customUniforms.get(uniformName);
        if (previousBuffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer bufferData = stack.malloc((int) previousBuffer.size()).order(ByteOrder.nativeOrder());
            writer.write(bufferData);
            while (bufferData.hasRemaining()) {
                bufferData.put((byte) 0);
            }
            bufferData.flip();

            int uploadHash = hash(bufferData);
            GpuBuffer writableBuffer = ensureWritableUniformBuffer(customUniforms, uniformName, previousBuffer, bufferData);
            if (writableBuffer != previousBuffer) {
                LAST_UPLOADED_HASHES.put(writableBuffer, uploadHash);
                return;
            }

            Integer lastUploadHash = LAST_UPLOADED_HASHES.get(writableBuffer);
            if (lastUploadHash != null && lastUploadHash == uploadHash) {
                return;
            }

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(writableBuffer.slice(), bufferData);
            LAST_UPLOADED_HASHES.put(writableBuffer, uploadHash);
        }
    }

    private static GpuBuffer ensureWritableUniformBuffer(
            Map<String, GpuBuffer> customUniforms,
            String uniformName,
            GpuBuffer previousBuffer,
            ByteBuffer initialData
    ) {
        if (!previousBuffer.isClosed() && (previousBuffer.usage() & GpuBuffer.USAGE_COPY_DST) != 0) {
            return previousBuffer;
        }

        GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                () -> "Salt's Anti Aliasing " + uniformName,
                WRITABLE_UNIFORM_USAGE,
                initialData
        );
        GpuBuffer old = customUniforms.put(uniformName, replacement);
        if (old != null && !old.isClosed()) {
            old.close();
        }
        LAST_UPLOADED_HASHES.remove(old);
        return replacement;
    }

    private static int hash(ByteBuffer bufferData) {
        ByteBuffer duplicate = bufferData.duplicate();
        int hash = 1;
        while (duplicate.hasRemaining()) {
            hash = 31 * hash + duplicate.get();
        }
        return hash;
    }

    @FunctionalInterface
    private interface UniformWriter {
        void write(ByteBuffer bufferData);
    }
}
