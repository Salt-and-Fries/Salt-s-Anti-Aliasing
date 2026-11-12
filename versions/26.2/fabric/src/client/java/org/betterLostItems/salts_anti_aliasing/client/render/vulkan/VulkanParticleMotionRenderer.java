package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.*;

/** Alpha-tested, nearest-visible particle velocity over the normal world/geometry field. */
public final class VulkanParticleMotionRenderer {
    private static RenderPipeline pipeline;
    private static final Map<Boolean,RenderPipeline> PIPELINES=new HashMap<>();
    private static GpuBuffer vertices, config;
    private static java.nio.ByteBuffer staging;
    private static int lastQuads;

    private VulkanParticleMotionRenderer() {}
    public static int lastRenderedQuads() { return lastQuads; }

    static void render(RenderTarget scene, RenderTarget motion, VulkanSceneTemporalController temporal) {
        lastQuads = 0;
        if (!temporal.tuning().entityMotion || temporal.resetHistoryThisFrame()
                || motion.getDepthTextureView() == null) return;
        var groups = new LinkedHashMap<Identifier, FloatArrayList>();
        ParticleMotionGeometry.forEach((now, old) -> {
            FloatArrayList data = groups.computeIfAbsent(now.layer().textureAtlasLocation(), k -> new FloatArrayList());
            int[] order = {0, 1, 2, 2, 3, 0};
            float[] u = {now.u1(), now.u1(), now.u0(), now.u0()};
            float[] v = {now.v1(), now.v0(), now.v0(), now.v1()};
            float threshold = now.layer().translucent() ? temporal.tuning().dlssParticleAlphaThreshold : .1f;
            for (int i : order) {
                for (int c = 0; c < 3; c++) data.add(now.positions()[i * 3 + c]);
                for (int c = 0; c < 3; c++) data.add(old.positions()[i * 3 + c]);
                data.add(u[i]); data.add(v[i]); data.add(now.alpha()); data.add(threshold);
            }
            lastQuads++;
        });
        if (groups.isEmpty()) return;
        ensurePipeline(motion.getColorTexture().getFormat()==GpuFormat.RGBA16_FLOAT);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearDepthTexture(motion.getDepthTexture(), 0.0);
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.malloc(160);
            for (float f : temporal.currentViewProjectionArray()) data.putFloat(f);
            for (float f : temporal.previousViewProjectionArray()) data.putFloat(f);
            data.putFloat(temporal.currentJitterTexelX() * 2 / scene.width)
                    .putFloat(-temporal.currentJitterTexelY() * 2 / scene.height)
                    .putFloat(scene.width).putFloat(scene.height);
            for (float f : temporal.cameraTranslationToPreviousArray()) data.putFloat(f);
            data.putFloat(0).flip();
            if (config == null) config = RenderSystem.getDevice().createBuffer(() -> "Salt's Particle Motion Config",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
            else encoder.writeToBuffer(config.slice(), data);
        }
        int totalFloats = groups.values().stream().mapToInt(FloatArrayList::size).sum();
        int bytes = Math.multiplyExact(totalFloats, Float.BYTES);
        if (vertices == null || vertices.size() < bytes) {
            if (vertices != null) vertices.close();
            vertices = RenderSystem.getDevice().createBuffer(() -> "Salt's Particle Motion Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, Math.max(bytes, 4096));
        }
        // writeToBuffer copies these bytes into the encoder's upload storage before returning.
        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) org.lwjgl.system.MemoryUtil.memFree(staging);
            staging = org.lwjgl.system.MemoryUtil.memAlloc(Integer.highestOneBit(bytes) * 2);
        }
        staging.clear();
        for (var values : groups.values()) for (int i = 0; i < values.size(); i++) staging.putFloat(values.getFloat(i));
        staging.flip(); encoder.writeToBuffer(vertices.slice(0, bytes), staging);
        try (var pass = encoder.createRenderPass(() -> "Salt's Particle Motion", motion.getColorTextureView(),
                Optional.empty(), motion.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("ParticleMotionConfig", config);
            pass.bindTexture("SceneDepthSampler", scene.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertices.slice());
            int first = 0;
            for (var group : groups.entrySet()) {
                var atlas = Minecraft.getInstance().getTextureManager().getTexture(group.getKey());
                pass.bindTexture("ParticleAtlas", atlas.getTextureView(), atlas.getSampler());
                int count = group.getValue().size() / 10;
                pass.draw(count, 1, first, 0); first += count;
            }
        }
    }

    private static void ensurePipeline(boolean sgsr) {
        pipeline=PIPELINES.get(sgsr);
        if (pipeline != null) return;
        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.parse("salts_anti_aliasing:pipeline/particle_motion"+(sgsr?"_sgsr":"")))
                .withVertexShader(Identifier.parse("salts_anti_aliasing:post/particle_motion"))
                .withFragmentShader(Identifier.parse("salts_anti_aliasing:post/"+(sgsr?"sgsr2_particle_motion":"particle_motion")))
                .withVertexBinding(0, VertexFormat.builder(0)
                        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                        .addAttribute("PreviousPosition", GpuFormat.RGB32_FLOAT)
                        .addAttribute("UV", GpuFormat.RG32_FLOAT)
                        .addAttribute("Alpha", GpuFormat.R32_FLOAT)
                        .addAttribute("AlphaThreshold", GpuFormat.R32_FLOAT).build())
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("ParticleMotionConfig", UniformType.UNIFORM_BUFFER)
                        .withSampler("SceneDepthSampler")
                        .withSampler("ParticleAtlas").build())
                .withColorTargetState(new ColorTargetState(Optional.empty(), sgsr?GpuFormat.RGBA16_FLOAT:GpuFormat.RG16_FLOAT,
                        ColorTargetState.WRITE_RED | ColorTargetState.WRITE_GREEN))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, true))
                .withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        PIPELINES.put(sgsr,pipeline);
    }

    static void release() {
        if (vertices != null) vertices.close();
        if (config != null) config.close();
        if (staging != null) org.lwjgl.system.MemoryUtil.memFree(staging);
        staging = null;
        vertices = config = null; lastQuads = 0;
    }
}
