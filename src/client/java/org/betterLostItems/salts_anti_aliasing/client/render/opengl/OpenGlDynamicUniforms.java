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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Writes per-frame and per-config uniform values consumed by post-processing shaders.
 */
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

    /**
     * Creates a open gl dynamic uniforms with the collaborators or initial state supplied by the
     * caller.
     */
    private OpenGlDynamicUniforms() {
    }

    /**
     * Coordinates update for mode within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param postChain post chain supplied by Minecraft or the caller
     * @param config configuration being read, normalized, or applied
     */
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

    /**
     * Coordinates update taa within the anti-aliasing render, configuration, or compatibility flow.
     * @param postChain post chain supplied by Minecraft or the caller
     * @param controller controller supplied by Minecraft or the caller
     */
    static void updateTaa(PostChain postChain, OpenGlSceneTemporalController controller) {
        for (PostPass pass : ((PostChainAccessor) postChain).saltsAntiAliasing$passes()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).saltsAntiAliasing$customUniforms();
            writeTaaUniform(customUniforms, controller);
        }
    }

    /**
     * Coordinates write nis sharpen uniform within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param customUniforms custom uniforms supplied by Minecraft or the caller
     * @param sharpenStrength sharpen strength supplied by Minecraft or the caller
     */
    private static void writeNisSharpenUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength) {
        updateUniformBuffer(customUniforms, NIS_SHARPEN_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(NIS_EDGE_BOOST);
            bufferData.putFloat(NIS_CLAMP_BOOST);
        });
    }

    /**
     * Coordinates write rcas uniform within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param customUniforms custom uniforms supplied by Minecraft or the caller
     * @param sharpenStrength sharpen strength supplied by Minecraft or the caller
     */
    private static void writeRcasUniform(Map<String, GpuBuffer> customUniforms, float sharpenStrength) {
        updateUniformBuffer(customUniforms, RCAS_UNIFORM, bufferData -> {
            bufferData.putFloat(sharpenStrength);
            bufferData.putFloat(RCAS_EDGE_LIMIT);
            bufferData.putFloat(RCAS_CLAMP_BOOST);
        });
    }

    /**
     * Coordinates write taa uniform within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param customUniforms custom uniforms supplied by Minecraft or the caller
     * @param controller controller supplied by Minecraft or the caller
     */
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

    /**
     * Coordinates update uniform buffer within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param customUniforms custom uniforms supplied by Minecraft or the caller
     * @param uniformName uniform name supplied by Minecraft or the caller
     * @param writer writer supplied by Minecraft or the caller
     */
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
            ByteBuffer bufferData = stack.malloc(bufferSize(previousBuffer)).order(ByteOrder.nativeOrder());
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

    /**
     * Coordinates ensure writable uniform buffer within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param customUniforms custom uniforms supplied by Minecraft or the caller
     * @param uniformName uniform name supplied by Minecraft or the caller
     * @param previousBuffer previous buffer supplied by Minecraft or the caller
     * @param initialData initial data supplied by Minecraft or the caller
     * @return ensure writable uniform buffer value produced or selected by this code path
     */
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

    private static int bufferSize(GpuBuffer buffer) {
        try {
            Method sizeMethod = buffer.getClass().getMethod("size");
            Object size = sizeMethod.invoke(buffer);
            if (size instanceof Number number) {
                return Math.toIntExact(number.longValue());
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            throw new IllegalStateException("Unable to read GPU uniform buffer size.", exception);
        }

        throw new IllegalStateException("GPU uniform buffer size did not return a number.");
    }

    /**
     * Coordinates hash within the anti-aliasing render, configuration, or compatibility flow.
     * @param bufferData buffer data supplied by Minecraft or the caller
     * @return hash value produced or selected by this code path
     */
    private static int hash(ByteBuffer bufferData) {
        ByteBuffer duplicate = bufferData.duplicate();
        int hash = 1;
        while (duplicate.hasRemaining()) {
            hash = 31 * hash + duplicate.get();
        }
        return hash;
    }

    /**
     * Documents uniform writer behavior for Salt's Anti Aliasing. OpenGL backend code that owns
     * framebuffers, post chains, and GPU-side state.
     */
    @FunctionalInterface
    private interface UniformWriter {
        /**
         * Coordinates write within the anti-aliasing render, configuration, or compatibility flow.
         * @param bufferData buffer data supplied by Minecraft or the caller
         */
        void write(ByteBuffer bufferData);
    }
}
