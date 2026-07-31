package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaPipelineVariants;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Selects a sample-count-specific pipeline identity for multisampled Vulkan render passes.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanRenderPass")
public abstract class VulkanRenderPassMixin {
    @Unique
    private int saltsAntiAliasing$sampleCount = 1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void saltsAntiAliasing$rememberRenderPassSampleCount(
            VulkanDevice device,
            VulkanCommandEncoder encoder,
            VkCommandBuffer commandBuffer,
            CheckpointExtension.CheckpointStorage checkpointStorage,
            RenderPass.RenderArea renderArea,
            int outputWidth,
            int outputHeight,
            boolean hasDepth,
            Supplier<String> label,
            CallbackInfo callbackInfo
    ) {
        saltsAntiAliasing$sampleCount = VulkanMsaaState.currentRenderPassSampleCount();
    }

    @Redirect(
            method = "setPipeline",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vulkan/VulkanDevice;getOrCompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/vulkan/VulkanRenderPipeline;"
            )
    )
    private VulkanRenderPipeline saltsAntiAliasing$getOrCompileSampleScopedPipeline(
            VulkanDevice device,
            RenderPipeline pipeline
    ) {
        int samples = Math.max(1, saltsAntiAliasing$sampleCount);
        if (samples <= 1) {
            return ((VulkanDevicePipelineAccessor) device).saltsAntiAliasing$getOrCompilePipeline(pipeline);
        }

        RenderPipeline msaaPipeline = VulkanMsaaPipelineVariants.variant(pipeline, samples);
        return VulkanMsaaState.withPipelineSampleCount(
                samples,
                () -> ((VulkanDevicePipelineAccessor) device).saltsAntiAliasing$getOrCompilePipeline(msaaPipeline)
        );
    }
}
