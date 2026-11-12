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

/** Conservative history rejection for color changes without per-object motion vectors. */
final class VulkanTemporalColorRejectionRenderer {
    private static RenderPipeline pipeline;
    private static GpuBuffer configBuffer;
    static void release() {
        if (configBuffer != null) configBuffer.close();
        configBuffer = null;
    }

    static void render(RenderTarget current, RenderTarget previous, RenderTarget motion, RenderTarget mask,
                       VulkanSceneTemporalController temporal) {
        if (pipeline == null) {
            var layout = BindGroupLayout.builder()
                    .withSampler("CurrentSampler")
                    .withSampler("PreviousSampler")
                    .withSampler("MotionVectorSampler")
                    .withUniform("TemporalColorConfig", UniformType.UNIFORM_BUFFER).build();
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.parse("salts_anti_aliasing:pipeline/temporal_color_rejection"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Identifier.parse("salts_anti_aliasing:post/temporal_color_rejection"))
                    .withBindGroupLayout(layout)
                    .withColorTargetState(0, new ColorTargetState(Optional.empty(), GpuFormat.R16_FLOAT, ColorTargetState.WRITE_RED))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        }
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.malloc(32);
            data.putFloat(current.width).putFloat(current.height)
                    .putFloat(temporal.previousJitterTexelX() - temporal.currentJitterTexelX())
                    .putFloat(temporal.currentJitterTexelY() - temporal.previousJitterTexelY());
            var t=temporal.tuning();
            data.putFloat(t.reactiveStrength).putFloat(t.reactiveThreshold).putFloat(t.reactiveMaximum).putFloat(0).flip();
            if (configBuffer == null || configBuffer.isClosed()) {
                configBuffer = RenderSystem.getDevice().createBuffer(() -> "Salt's Temporal Color Rejection",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
            } else RenderSystem.getDevice().createCommandEncoder().writeToBuffer(configBuffer.slice(), data);
        }
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's Temporal Color Rejection", mask.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("TemporalColorConfig", configBuffer);
            var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            pass.bindTexture("CurrentSampler", current.getColorTextureView(), linear);
            pass.bindTexture("PreviousSampler", previous.getColorTextureView(), linear);
            pass.bindTexture("MotionVectorSampler", motion.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }
}
