package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.*;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Optional;

/** Selects immutable source variants before 26.2 validates attachment formats and binds them. */
@Mixin(RenderPass.class)
public abstract class RenderPassPipelineMixin {
    @Shadow @Final private GpuDeviceBackend device;
    @Shadow @Final private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
    private RenderPipeline saltsAntiAliasing$selectPipeline(RenderPipeline pipeline) {
        if (!(device instanceof VulkanDevice)) return pipeline;
        pipeline = VulkanAttachmentPipelineVariants.select(pipeline, colorAttachments);
        pipeline = VulkanTextureDetail.select(pipeline);
        int samples = VulkanMsaaCompatibility.commonAttachmentSampleCount(colorAttachments.stream()
                .filter(attachment -> attachment != null && attachment.textureView() != null)
                .mapToInt(attachment -> VulkanMsaaState.sampleCount(attachment.textureView())).toArray());
        return VulkanMsaaPipelineVariants.variant(pipeline, samples,
                VulkanMsaaPipelineVariants.usesAlphaToCoverage(pipeline, samples,
                        VulkanMsaaState.currentSceneAlphaToCoverage()), VulkanMsaaState.currentHandRendering());
    }
}
