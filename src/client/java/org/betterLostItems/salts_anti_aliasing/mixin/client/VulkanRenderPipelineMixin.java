package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaPipelineVariants;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Vulkan render pipelines use the active MSAA sample count while world rendering is redirected.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline")
public abstract class VulkanRenderPipelineMixin {
    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"
            )
    )
    private static VkPipelineMultisampleStateCreateInfo saltsAntiAliasing$usePipelineSampleCount(
            VkPipelineMultisampleStateCreateInfo multisampleState, int originalSamples,
            VulkanDevice device, BackendRenderPipeline.CreateInfo createInfo
    ) {
        return multisampleState.rasterizationSamples(
                VulkanMsaaPipelineVariants.nativeSampleCount(createInfo.name(), originalSamples)
        );
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;sampleShadingEnable(Z)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"
            )
    )
    private static VkPipelineMultisampleStateCreateInfo saltsAntiAliasing$configureAlphaToCoverage(
            VkPipelineMultisampleStateCreateInfo multisampleState,
            boolean sampleShadingEnabled,
            VulkanDevice device, BackendRenderPipeline.CreateInfo createInfo
    ) {
        boolean handSampleShading = VulkanMsaaPipelineVariants.nativeSampleShading(createInfo.name());
        return multisampleState
                .sampleShadingEnable(sampleShadingEnabled || handSampleShading)
                .minSampleShading(handSampleShading ? 1.0f : multisampleState.minSampleShading())
                .alphaToCoverageEnable(VulkanMsaaPipelineVariants.nativeAlphaToCoverage(createInfo.name()));
    }
}
