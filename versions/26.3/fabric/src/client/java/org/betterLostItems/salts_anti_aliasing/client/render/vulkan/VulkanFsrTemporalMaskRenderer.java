package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Generates FSR reactive and transparency/composition masks from temporally reprojected opaque and
 * post-transparency colors. This follows AMD's TCR fallback for engines that cannot write
 * material masks while drawing every translucent surface.
 */
final class VulkanFsrTemporalMaskRenderer {
    private static final VulkanFsrTemporalMaskRenderer INSTANCE = new VulkanFsrTemporalMaskRenderer();
    private static final int BUFFER_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static final int CONFIG_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();
    private static final int MASK_WRITE = ColorTargetState.WRITE_RED;
    private static final BindGroupLayout INPUT_LAYOUT = BindGroupLayout.builder()
            .withUniform("CurrentColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("CurrentOpaqueSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("PreviousColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("PreviousOpaqueSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("MotionVectorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("FsrTemporalMaskConfig", UniformType.UNIFORM_BUFFER)
            .build();

    private RenderPipeline pipeline;
    private GpuBuffer configBuffer;

    private VulkanFsrTemporalMaskRenderer() {
    }

    static VulkanFsrTemporalMaskRenderer instance() {
        return INSTANCE;
    }

    static void release() {
        if (INSTANCE.configBuffer != null) INSTANCE.configBuffer.close();
        INSTANCE.configBuffer = null;
    }

    void render(
            RenderTarget currentColor,
            RenderTarget currentOpaque,
            RenderTarget previousColor,
            RenderTarget previousOpaque,
            RenderTarget motionVectors,
            RenderTarget reactiveMask,
            RenderTarget transparencyMask,
            VulkanSceneTemporalController temporalController
    ) {
        if (!hasColor(currentColor)
                || !hasColor(currentOpaque)
                || !hasColor(previousColor)
                || !hasColor(previousOpaque)
                || !hasColor(motionVectors)
                || !hasColor(reactiveMask)
                || !hasColor(transparencyMask)) {
            MetricsDiagnostics.warnOnce(
                    "fsr-temporal-mask-input-unavailable",
                    "fsr.masks",
                    "render_skipped",
                    "currentColor", hasColor(currentColor),
                    "currentOpaque", hasColor(currentOpaque),
                    "previousColor", hasColor(previousColor),
                    "previousOpaque", hasColor(previousOpaque),
                    "motionVectors", hasColor(motionVectors),
                    "reactiveMask", hasColor(reactiveMask),
                    "transparencyMask", hasColor(transparencyMask)
            );
            return;
        }

        ensurePipeline();
        writeConfig(currentColor.width, currentColor.height, temporalController);

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        RenderPassDescriptor descriptor = RenderPassDescriptor
                .builder(() -> "Salt's FSR Temporal Masks")
                .withColorAttachment(reactiveMask.getColorTextureView())
                .withColorAttachment(transparencyMask.getColorTextureView())
                .withRenderArea(new RenderPass.RenderArea(
                        0,
                        0,
                        reactiveMask.width,
                        reactiveMask.height
                )).build();
        try (RenderPass renderPass = commandEncoder.createRenderPass(descriptor)) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FsrTemporalMaskConfig", configBuffer);
            bindNearest(renderPass, "CurrentColorSampler", currentColor);
            bindNearest(renderPass, "CurrentOpaqueSampler", currentOpaque);
            renderPass.setUniform("PreviousColorSampler", previousColor.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            // Both history layers must use the same fractional footprint, otherwise jitter
            // alone changes the estimated translucent contribution at hard edges.
            renderPass.setUniform("PreviousOpaqueSampler", previousOpaque.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            bindNearest(renderPass, "MotionVectorSampler", motionVectors);
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private void ensurePipeline() {
        if (pipeline != null) {
            return;
        }

        ColorTargetState maskTarget = new ColorTargetState(
                java.util.Optional.empty(),
                GpuFormat.R8_UNORM,
                MASK_WRITE
        );
        pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.parse(SaltsAntiAliasing.MOD_ID + ":pipeline/fsr_temporal_masks"))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Identifier.parse(SaltsAntiAliasing.MOD_ID + ":post/fsr_temporal_masks"))
                .withBindGroupLayout(INPUT_LAYOUT)
                .withColorTargetState(0, maskTarget)
                .withColorTargetState(1, maskTarget)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(pipeline);
        if (compiled == null) {
            throw new IllegalStateException("Unable to compile FSR temporal-mask pipeline");
        }
        MetricsDiagnostics.info("fsr.masks", "pipeline_compiled", MetricsDiagnostics.fields(
                "pipeline", pipeline.getLocation(),
                "outputFormat", GpuFormat.R8_UNORM,
                "colorAttachmentCount", 2
        ));
    }

    private void writeConfig(
            int width,
            int height,
            VulkanSceneTemporalController temporalController
    ) {
        float jitterDeltaX = temporalController.previousJitterTexelX()
                - temporalController.currentJitterTexelX();
        float jitterDeltaY = temporalController.currentJitterTexelY()
                - temporalController.previousJitterTexelY();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(CONFIG_SIZE);
            Std140Builder.intoBuffer(data).putVec4(
                    Math.max(1, width),
                    Math.max(1, height),
                    jitterDeltaX,
                    jitterDeltaY
            );
            var t=temporalController.tuning();
            data.putFloat(t.reactiveStrength).putFloat(t.reactiveThreshold).putFloat(t.reactiveMaximum).putFloat(0);
            data.putFloat(t.fsrTransparencyThreshold).putFloat(t.fsrCompositionStrength).putFloat(t.fsrReactiveScale).putFloat(.01f);
            data.flip();
            if (configBuffer == null || configBuffer.isClosed() || configBuffer.size() != data.remaining()) {
                if (configBuffer != null && !configBuffer.isClosed()) {
                    configBuffer.close();
                }
                configBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "Salt's FSR Temporal Mask Config",
                        BUFFER_USAGE,
                        data
                );
            } else {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(configBuffer.slice(), data);
            }
        }
    }

    private static void bindNearest(RenderPass renderPass, String samplerName, RenderTarget target) {
        renderPass.setUniform(
                samplerName,
                target.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        );
    }

    private static boolean hasColor(RenderTarget target) {
        return target != null && target.getColorTextureView() != null;
    }
}
