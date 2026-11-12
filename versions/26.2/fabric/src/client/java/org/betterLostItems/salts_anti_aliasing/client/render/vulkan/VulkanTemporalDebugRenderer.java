package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.textures.FilterMode;

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
        render(source,output,motion,1,1);
    }

    static void render(RenderTarget source, RenderTarget output, boolean motion, float scaleX, float scaleY) {
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
                                            .withSampler("InSampler")
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
            data.putFloat(8,scaleX).putFloat(12,scaleY);
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
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(
                    "InSampler",
                    source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("TemporalDebugConfig", config);
            pass.draw(3, 1, 0, 0);
        }
    }
}
