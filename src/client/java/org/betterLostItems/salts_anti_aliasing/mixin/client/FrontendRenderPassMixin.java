package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaCompatibility;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaPipelineVariants;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Optional;

/** Selects compiled MSAA variants before RenderPearl validates and binds a pipeline. */
@Mixin(FrontendRenderPass.class)
public abstract class FrontendRenderPassMixin {
    @Shadow @Final
    private GpuDeviceBackend device;

    @Shadow @Final
    private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
    private CompiledRenderPipeline saltsAntiAliasing$selectMultisampledPipeline(CompiledRenderPipeline pipeline) {
        if (!(device instanceof VulkanDevice)) {
            return pipeline;
        }
        pipeline=org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanAttachmentPipelineVariants.select(pipeline,colorAttachments);
        pipeline=org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanTextureDetail.select(pipeline);
        int samples = VulkanMsaaCompatibility.commonAttachmentSampleCount(colorAttachments.stream()
                .filter(attachment -> attachment != null && attachment.textureView() != null)
                .mapToInt(attachment -> VulkanMsaaState.sampleCount(attachment.textureView()))
                .toArray());
        return VulkanMsaaPipelineVariants.compiledVariant(
                pipeline, samples, VulkanMsaaState.currentSceneAlphaToCoverage()
        );
    }
}
