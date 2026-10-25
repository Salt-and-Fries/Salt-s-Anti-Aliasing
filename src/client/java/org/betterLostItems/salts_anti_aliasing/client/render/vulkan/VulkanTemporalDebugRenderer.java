package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/** Displays the buffers actually submitted to native temporal evaluation. */
final class VulkanTemporalDebugRenderer {
    private static RenderPipeline pipeline;
    private static GpuBuffer config;
    static void release() {
        if (config != null) config.close();
        config = null;
    }

    static void render(RenderTarget source, RenderTarget output, boolean motion) {
        if (source == null || source.getColorTextureView() == null) return;
        if (pipeline == null)
            pipeline =
                    RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                            .withLocation(
                                    Identifier.parse("salts_anti_aliasing:pipeline/temporal_debug"))
                            .withVertexShader(Identifier.parse("minecraft:core/screenquad"))
                            .withFragmentShader(
                                    Identifier.parse("salts_anti_aliasing:post/temporal_debug"))
                            .withBindGroupLayout(
                                    BindGroupLayout.builder()
                                            .withUniform(
                                                    "InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                                            .withUniform(
                                                    "TemporalDebugConfig",
                                                    UniformType.UNIFORM_BUFFER)
                                            .build())
                            .withColorTargetState(
                                    new ColorTargetState(
                                            Optional.empty(),
                                            GpuFormat.RGBA8_UNORM,
                                            ColorTargetState.WRITE_ALL))
                            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                            .build();
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.calloc(16);
            data.putFloat(0, motion ? 1 : 0);
            if (config == null)
                config =
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "Salt's Temporal Debug",
                                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                                        data);
            else
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(config.slice(), data);
        }
        try (var pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "Salt's Temporal Debug",
                                output.getColorTextureView(),
                                Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(
                    "InSampler",
                    source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("TemporalDebugConfig", config);
            pass.draw(3, 1, 0, 0);
        }
    }
}
