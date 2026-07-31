package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
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
    private static final int CONFIG_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int MASK_WRITE = ColorTargetState.WRITE_RED;
    private static final BindGroupLayout INPUT_LAYOUT = BindGroupLayout.builder()
            .withSampler("CurrentColorSampler")
            .withSampler("CurrentOpaqueSampler")
            .withSampler("PreviousColorSampler")
            .withSampler("PreviousOpaqueSampler")
            .withSampler("MotionVectorSampler")
            .withUniform("FsrTemporalMaskConfig", UniformType.UNIFORM_BUFFER)
            .build();

    private RenderPipeline pipeline;
    private GpuBuffer configBuffer;

    private VulkanFsrTemporalMaskRenderer() {
    }

    static VulkanFsrTemporalMaskRenderer instance() {
        return INSTANCE;
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
            return;
        }

        ensurePipeline();
        writeConfig(currentColor.width, currentColor.height, temporalController);

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        RenderPassDescriptor descriptor = RenderPassDescriptor
                .create(() -> "Salt's FSR Temporal Masks")
                .withColorAttachment(reactiveMask.getColorTextureView())
                .withColorAttachment(transparencyMask.getColorTextureView())
                .withRenderArea(new RenderPass.RenderArea(
                        0,
                        0,
                        reactiveMask.width,
                        reactiveMask.height
                ));
        try (RenderPass renderPass = commandEncoder.createRenderPass(descriptor)) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FsrTemporalMaskConfig", configBuffer);
            bindNearest(renderPass, "CurrentColorSampler", currentColor);
            bindNearest(renderPass, "CurrentOpaqueSampler", currentOpaque);
            bindNearest(renderPass, "PreviousColorSampler", previousColor);
            bindNearest(renderPass, "PreviousOpaqueSampler", previousOpaque);
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
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
        if (!compiled.isValid()) {
            throw new IllegalStateException("Unable to compile FSR temporal-mask pipeline");
        }
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
        renderPass.bindTexture(
                samplerName,
                target.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        );
    }

    private static boolean hasColor(RenderTarget target) {
        return target != null && target.getColorTextureView() != null;
    }
}
