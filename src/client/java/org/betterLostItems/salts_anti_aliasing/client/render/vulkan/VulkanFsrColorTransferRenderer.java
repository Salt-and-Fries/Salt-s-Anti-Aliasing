package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

import java.util.Optional;

/**
 * Converts Minecraft's display-encoded scene color to and from the linear floating-point color
 * expected by FidelityFX Super Resolution.
 */
final class VulkanFsrColorTransferRenderer {
    private static final VulkanFsrColorTransferRenderer INSTANCE = new VulkanFsrColorTransferRenderer();
    private static final BindGroupLayout INPUT_LAYOUT = BindGroupLayout.builder()
            .withUniform("InputSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    private RenderPipeline decodePipeline;
    private RenderPipeline encodePipeline;
    private RenderPipeline pairedDecodePipeline;

    private VulkanFsrColorTransferRenderer() {
    }

    static VulkanFsrColorTransferRenderer instance() {
        return INSTANCE;
    }

    /** Identical sRGB conversion for both inputs in one MRT pass. */
    void decodePair(RenderTarget full, RenderTarget opaque, RenderTarget linearFull, RenderTarget linearOpaque) {
        if (pairedDecodePipeline == null) {
            ColorTargetState color = new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT,
                    ColorTargetState.WRITE_ALL);
            pairedDecodePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.parse("salts_anti_aliasing:pipeline/fsr_decode_pair"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Identifier.parse("salts_anti_aliasing:post/fsr_decode_pair"))
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("FullSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("OpaqueSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                    .withColorTargetState(0, color).withColorTargetState(1, color)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        }
        var descriptor = com.mojang.renderpearl.api.commands.RenderPassDescriptor
                .builder(() -> "Salt's FSR Paired Linear Decode")
                .withColorAttachment(linearFull.getColorTextureView())
                .withColorAttachment(linearOpaque.getColorTextureView()).build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pairedDecodePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("FullSampler", full.getColorTextureView(), nearest);
            pass.setUniform("OpaqueSampler", opaque.getColorTextureView(), nearest);
            pass.draw(3, 1, 0, 0);
        }
    }

    void decode(RenderTarget source, RenderTarget destination) {
        render(
                source,
                destination,
                decodePipeline(),
                "Salt's FSR Linear Color Decode"
        );
    }

    void encode(RenderTarget source, RenderTarget destination) {
        render(
                source,
                destination,
                encodePipeline(),
                "Salt's FSR Display Color Encode"
        );
    }

    private void render(
            RenderTarget source,
            RenderTarget destination,
            RenderPipeline pipeline,
            String label
    ) {
        if (source == null
                || destination == null
                || source.getColorTextureView() == null
                || destination.getColorTextureView() == null) {
            MetricsDiagnostics.warnOnce(
                    "fsr-color-transfer-input-unavailable-" + label,
                    "fsr.color_transfer",
                    "render_skipped",
                    "label", label,
                    "sourceAvailable", source != null,
                    "destinationAvailable", destination != null,
                    "sourceColorViewAvailable", source != null && source.getColorTextureView() != null,
                    "destinationColorViewAvailable", destination != null
                            && destination.getColorTextureView() != null
            );
            return;
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(
                () -> label,
                destination.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(
                    "InputSampler",
                    source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private RenderPipeline decodePipeline() {
        if (decodePipeline == null) {
            decodePipeline = createPipeline(
                    Identifier.parse(SaltsAntiAliasing.MOD_ID + ":pipeline/fsr_srgb_to_linear"),
                    Identifier.parse(SaltsAntiAliasing.MOD_ID + ":post/fsr_srgb_to_linear"),
                    GpuFormat.RGBA16_FLOAT
            );
        }
        return decodePipeline;
    }

    private RenderPipeline encodePipeline() {
        if (encodePipeline == null) {
            encodePipeline = createPipeline(
                    Identifier.parse(SaltsAntiAliasing.MOD_ID + ":pipeline/fsr_linear_to_srgb"),
                    Identifier.parse(SaltsAntiAliasing.MOD_ID + ":post/fsr_linear_to_srgb"),
                    GpuFormat.RGBA8_UNORM
            );
        }
        return encodePipeline;
    }

    private static RenderPipeline createPipeline(
            Identifier pipelineId,
            Identifier fragmentShaderId,
            GpuFormat outputFormat
    ) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(pipelineId)
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(fragmentShaderId)
                .withBindGroupLayout(INPUT_LAYOUT)
                .withColorTargetState(new ColorTargetState(
                        Optional.empty(),
                        outputFormat,
                        ColorTargetState.WRITE_ALL
                ))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(pipeline);
        if (compiled == null) {
            throw new IllegalStateException("Unable to compile FSR color-transfer pipeline " + pipelineId);
        }
        MetricsDiagnostics.info("fsr.color_transfer", "pipeline_compiled", MetricsDiagnostics.fields(
                "pipeline", pipelineId,
                "fragmentShader", fragmentShaderId,
                "outputFormat", outputFormat
        ));
        return pipeline;
    }
}
