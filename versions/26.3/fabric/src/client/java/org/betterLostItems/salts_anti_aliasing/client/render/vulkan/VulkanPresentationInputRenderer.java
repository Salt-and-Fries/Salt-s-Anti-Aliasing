package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.OptionalDouble;

/** Main-target textures and swapchain images have opposite vertical orientations. */
final class VulkanPresentationInputRenderer {
    private static RenderPipeline pipeline;

    static void render(RenderTarget scene, RenderTarget motion, RenderTarget output) {
        if (pipeline == null)
            pipeline =
                    RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                            .withLocation(
                                    Identifier.parse(
                                            "salts_anti_aliasing:pipeline/presentation_motion"))
                            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                            .withFragmentShader(
                                    Identifier.parse(
                                            "salts_anti_aliasing:post/presentation_motion"))
                            .withBindGroupLayout(
                                    BindGroupLayout.builder()
                                            .withUniform(
                                                    "DepthSampler",
                                                    UniformType.COMBINED_IMAGE_SAMPLER)
                                            .withUniform(
                                                    "MotionSampler",
                                                    UniformType.COMBINED_IMAGE_SAMPLER)
                                            .build())
                            .withColorTargetState(
                                    new ColorTargetState(
                                            Optional.empty(),
                                            GpuFormat.RG16_FLOAT,
                                            ColorTargetState.WRITE_RED
                                                    | ColorTargetState.WRITE_GREEN))
                            .withDepthStencilState(
                                    new DepthStencilState(CompareOp.ALWAYS_PASS, true))
                            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                            .build();
        try (var pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "Salt's Frame Generation Presentation Inputs",
                                output.getColorTextureView(),
                                Optional.empty(),
                                output.getDepthTextureView(),
                                OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("DepthSampler", scene.getDepthTextureView(), nearest);
            pass.setUniform("MotionSampler", motion.getColorTextureView(), nearest);
            pass.draw(3, 1, 0, 0);
        }
    }
}
