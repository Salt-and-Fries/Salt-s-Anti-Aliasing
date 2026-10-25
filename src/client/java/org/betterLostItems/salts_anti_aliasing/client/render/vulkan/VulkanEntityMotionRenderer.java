package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.resources.Identifier;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;

/** Depth-validated geometry velocities overlay the camera motion field. */
final class VulkanEntityMotionRenderer {
    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer, configBuffer;
    private static ByteBuffer staging;
    private static int lastVertexCount;

    private VulkanEntityMotionRenderer() {}

    static int lastVertexCount() {
        return lastVertexCount;
    }

    static void release() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (configBuffer != null) {
            configBuffer.close();
            configBuffer = null;
        }
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
    }

    static void render(
            RenderTarget scene, RenderTarget motion, VulkanSceneTemporalController temporal) {
        lastVertexCount = 0;
        if (!temporal.tuning().entityMotion || temporal.resetHistoryThisFrame()) return;
        int drawVertices = EntityMotionGeometry.forEachMatched((a, b, primitive) -> {});
        if (drawVertices == 0) return;
        int bytes = drawVertices * 24;
        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAlloc(Integer.highestOneBit(bytes) * 2);
        }
        staging.clear();
        EntityMotionGeometry.forEachMatched(
                (now, old, primitive) -> {
                    int[] order = primitive == 4 ? new int[]{0, 1, 2, 2, 3, 0} : new int[]{0, 1, 2};
                    for (int q = 0; q < now.size(); q += primitive * 3)
                        for (int index : order) {
                            int p = q + index * 3;
                            staging.putFloat(now.getFloat(p))
                                    .putFloat(now.getFloat(p + 1))
                                    .putFloat(now.getFloat(p + 2));
                            staging.putFloat(old.getFloat(p))
                                    .putFloat(old.getFloat(p + 1))
                                    .putFloat(old.getFloat(p + 2));
                        }
                });
        staging.flip();
        lastVertexCount = staging.remaining() / 24;
        if (vertexBuffer == null || vertexBuffer.size() < staging.remaining()) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer =
                    RenderSystem.getDevice()
                            .createBuffer(
                                    () -> "Salt's Entity Motion Geometry",
                                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                                    staging.capacity());
        }
        RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToBuffer(vertexBuffer.slice(0, staging.remaining()), staging);
        if (pipeline == null) {
            pipeline =
                    RenderPipeline.builder()
                            .withLocation(
                                    Identifier.parse("salts_anti_aliasing:pipeline/entity_motion"))
                            .withVertexShader(
                                    Identifier.parse("salts_anti_aliasing:post/entity_motion"))
                            .withFragmentShader(
                                    Identifier.parse("salts_anti_aliasing:post/entity_motion"))
                            .withVertexBinding(
                                    0,
                                    VertexFormat.builder(0)
                                            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                                            .addAttribute("PreviousPosition", GpuFormat.RGB32_FLOAT)
                                            .build())
                            .withBindGroupLayout(
                                    BindGroupLayout.builder()
                                            .withUniform(
                                                    "EntityMotionConfig",
                                                    UniformType.UNIFORM_BUFFER)
                                            .withUniform(
                                                    "SceneDepthSampler",
                                                    UniformType.COMBINED_IMAGE_SAMPLER)
                                            .build())
                            .withColorTargetState(
                                    new ColorTargetState(
                                            Optional.empty(),
                                            GpuFormat.RG16_FLOAT,
                                            ColorTargetState.WRITE_RED
                                                    | ColorTargetState.WRITE_GREEN))
                            .withCull(false)
                            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                            .build();
        }
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.malloc(160);
            for (float v : temporal.currentViewProjectionArray()) data.putFloat(v);
            for (float v : temporal.previousViewProjectionArray()) data.putFloat(v);
            data.putFloat(temporal.currentJitterTexelX() * 2 / scene.width)
                    .putFloat(-temporal.currentJitterTexelY() * 2 / scene.height)
                    .putFloat(scene.width)
                    .putFloat(scene.height);
            for (float v : temporal.cameraTranslationToPreviousArray()) data.putFloat(v);
            data.putFloat(0).flip();
            if (configBuffer == null || configBuffer.isClosed())
                configBuffer =
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "Salt's Entity Motion Config",
                                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                                        data);
            else
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(configBuffer.slice(), data);
        }
        try (var pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "Salt's Entity Motion",
                                motion.getColorTextureView(),
                                Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("EntityMotionConfig", configBuffer);
            pass.setUniform(
                    "SceneDepthSampler",
                    scene.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertexBuffer.slice());
            pass.draw(lastVertexCount, 1, 0, 0);
        }
    }
}
