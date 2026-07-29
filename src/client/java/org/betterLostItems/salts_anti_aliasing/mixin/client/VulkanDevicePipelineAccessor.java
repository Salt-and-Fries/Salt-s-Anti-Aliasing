package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls VulkanDevice's package-private pipeline compiler from MSAA render-pass mixins.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanDevice")
public interface VulkanDevicePipelineAccessor {
    @Invoker("getOrCompilePipeline")
    VulkanRenderPipeline saltsAntiAliasing$getOrCompilePipeline(RenderPipeline pipeline);
}
