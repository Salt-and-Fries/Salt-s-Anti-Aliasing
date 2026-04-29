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
    private static final float NIS_EDGE_BOOST = 1.1f;
    private static final float NIS_CLAMP_BOOST = 0.18f;
    private static final float RCAS_EDGE_LIMIT = 0.22f;
    private static final float RCAS_CLAMP_BOOST = 0.12f;
    private static final int WRITABLE_UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    private static final Map<GpuBuffer, Integer> LAST_UPLOADED_HASHES = new IdentityHashMap<>();

    private OpenGlDynamicUniforms() {
    }

    static void updateForMode(PostChain postChain, AntiAliasingConfig config) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            if (config.mode == AntiAliasingMode.NIS_SHARPEN) {
                writeNisSharpenUniform(customUniforms, config.sharpenStrength);
            } else if (config.mode == AntiAliasingMode.NIS_UPSCALE) {
                writeNisSharpenUniform(customUniforms, config.nisUpscaleQualityPreset.sharpenStrength());
            } else if (config.mode == AntiAliasingMode.FSR1_RCAS) {
                writeRcasUniform(customUniforms, config.sharpenStrength);
            }
        }
    }

    static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            writeTaaUniform(customUniforms, controller);
        }
    }

    private static void writeNisSharpenUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength) {
        updateUniformBuffer(customUniforms, NIS_SHARPEN_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(NIS_EDGE_BOOST);
            bufferData.putFloat(NIS_CLAMP_BOOST);
        });
    }

    private static void writeRcasUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength) {
        updateUniformBuffer(customUniforms, RCAS_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(RCAS_EDGE_LIMIT);
            bufferData.putFloat(RCAS_CLAMP_BOOST);
        });
    }

    private static void writeTaaUniform(Map<String, GpuBuffer> customUniforms, OpenGlSceneTemporalController controller) {
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
        });
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
