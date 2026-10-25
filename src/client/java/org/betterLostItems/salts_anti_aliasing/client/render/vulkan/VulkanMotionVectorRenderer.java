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
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Renders signed temporal motion vectors into an RG16F target.
 *
 * <p>Minecraft's JSON post-chain path hardcodes post outputs to RGBA8, which clamps signed motion
 * vectors. DLSS/FSR need real floating-point velocities, so this renderer owns the custom pipeline
 * instead of routing through PostChain.</p>
 */
final class VulkanMotionVectorRenderer {
    private static final int BUFFER_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static final int SAMPLER_INFO_SIZE = new Std140SizeCalculator().putVec2().putVec2().get();
    private static final int MOTION_CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();
    private static final int MOTION_WRITE_MASK = ColorTargetState.WRITE_RED | ColorTargetState.WRITE_GREEN;
    private static final VulkanMotionVectorRenderer DLSS = new VulkanMotionVectorRenderer(
            "Salt's DLSS Motion Vectors",
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":pipeline/dlss_motion_vectors_rg16f"),
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":post/dlss_motion_vectors"),
            "DlssMotionConfig"
    );
    private static final VulkanMotionVectorRenderer FSR = new VulkanMotionVectorRenderer(
            "Salt's FSR Motion Vectors",
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":pipeline/fsr_motion_vectors_rg16f"),
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":post/fsr_motion_vectors"),
            "FsrMotionConfig"
    );

    private final String label;
    private final Identifier pipelineId;
    private final Identifier fragmentShaderId;
    private final String motionUniformName;
    private RenderPipeline pipeline;
    private GpuBuffer samplerInfoBuffer;
    private GpuBuffer motionConfigBuffer;

    private VulkanMotionVectorRenderer(
            String label,
            Identifier pipelineId,
            Identifier fragmentShaderId,
            String motionUniformName
    ) {
        this.label = label;
        this.pipelineId = pipelineId;
        this.fragmentShaderId = fragmentShaderId;
        this.motionUniformName = motionUniformName;
    }

    static VulkanMotionVectorRenderer dlss() {
        return DLSS;
    }

    static void releaseAll() { DLSS.release(); FSR.release(); }
    private void release() {
        if (samplerInfoBuffer != null) samplerInfoBuffer.close();
        if (motionConfigBuffer != null) motionConfigBuffer.close();
        samplerInfoBuffer = motionConfigBuffer = null;
    }

    static VulkanMotionVectorRenderer fsr() {
        return FSR;
    }

    void render(
            RenderTarget sceneTarget,
            TextureTarget motionVectorTarget,
            VulkanSceneTemporalController temporalController
    ) {
        if (sceneTarget == null
                || motionVectorTarget == null
                || sceneTarget.getDepthTextureView() == null
                || motionVectorTarget.getColorTextureView() == null) {
            MetricsDiagnostics.warnOnce(
                    "motion-vector-input-unavailable-" + pipelineId,
                    "temporal.motion_vectors",
                    "render_skipped",
                    "pipeline", pipelineId,
                    "sceneTargetAvailable", sceneTarget != null,
                    "motionVectorTargetAvailable", motionVectorTarget != null,
                    "sceneDepthViewAvailable", sceneTarget != null && sceneTarget.getDepthTextureView() != null,
                    "motionVectorColorViewAvailable", motionVectorTarget != null
                            && motionVectorTarget.getColorTextureView() != null
            );
            return;
        }

        ensurePipeline();
        writeSamplerInfo(sceneTarget, motionVectorTarget);
        writeMotionConfig(temporalController, sceneTarget.width, sceneTarget.height);

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(
                () -> label,
                motionVectorTarget.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
            renderPass.setUniform(motionUniformName, motionConfigBuffer);
            renderPass.setUniform(
                    "SceneDepthSampler",
                    sceneTarget.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            renderPass.draw(3, 1, 0, 0);
        }
        VulkanEntityMotionRenderer.render(sceneTarget,motionVectorTarget,temporalController);
    }

    private void ensurePipeline() {
        if (pipeline != null) {
            return;
        }

        BindGroupLayout layout = BindGroupLayout.builder()
                .withUniform("SceneDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
                .withUniform(motionUniformName, UniformType.UNIFORM_BUFFER)
                .build();
        pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(pipelineId)
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(fragmentShaderId)
                .withBindGroupLayout(layout)
                .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RG16_FLOAT, MOTION_WRITE_MASK))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(pipeline);
        if (compiled == null) {
            throw new IllegalStateException("Unable to compile motion-vector pipeline " + pipelineId);
        }
        MetricsDiagnostics.info("temporal.motion_vectors", "pipeline_compiled", MetricsDiagnostics.fields(
                "label", label,
                "pipeline", pipelineId,
                "fragmentShader", fragmentShaderId,
                "uniform", motionUniformName,
                "cancelProjectionJitter", true,
                "outputFormat", GpuFormat.RG16_FLOAT
        ));
    }

    private void writeSamplerInfo(RenderTarget sceneTarget, TextureTarget motionVectorTarget) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(SAMPLER_INFO_SIZE);
            Std140Builder.intoBuffer(data)
                    .putVec2(motionVectorTarget.width, motionVectorTarget.height)
                    .putVec2(sceneTarget.width, sceneTarget.height);
            data.flip();
            samplerInfoBuffer = writeUniformBuffer(samplerInfoBuffer, data, "SamplerInfo");
        }
    }

    private void writeMotionConfig(VulkanSceneTemporalController controller, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(MOTION_CONFIG_SIZE);
            Std140Builder builder = Std140Builder.intoBuffer(data);
            putMatrix(builder, controller.currentJitteredClipToWorldArray());
            putMatrix(builder, controller.currentViewProjectionArray());
            putMatrix(builder, controller.previousViewProjectionArray());
            builder.putVec4(Math.max(1, width), Math.max(1, height), 0.0f, 0.0f);
            float[] cameraTranslation = controller.cameraTranslationToPreviousArray();
            builder.putVec4(cameraTranslation[0], cameraTranslation[1], cameraTranslation[2], 0.0f);
            data.flip();
            motionConfigBuffer = writeUniformBuffer(motionConfigBuffer, data, motionUniformName);
        }
    }

    private GpuBuffer writeUniformBuffer(GpuBuffer buffer, ByteBuffer data, String name) {
        if (buffer == null || buffer.isClosed() || buffer.size() != data.remaining()) {
            if (buffer != null && !buffer.isClosed()) {
                buffer.close();
            }
            return RenderSystem.getDevice().createBuffer(() -> label + " " + name, BUFFER_USAGE, data);
        }

        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        return buffer;
    }

    private static void putMatrix(Std140Builder builder, float[] values) {
        for (int offset = 0; offset < 16; offset += 4) {
            builder.putVec4(
                    value(values, offset),
                    value(values, offset + 1),
                    value(values, offset + 2),
                    value(values, offset + 3)
            );
        }
    }

    private static float value(float[] values, int index) {
        return index < values.length ? values[index] : 0.0f;
    }
}
